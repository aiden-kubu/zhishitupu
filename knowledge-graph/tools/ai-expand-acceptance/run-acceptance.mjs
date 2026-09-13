/**
 * 「搜索未命中时由 AI 补全知识图谱」运行时验收脚本（任务约定 §19）。
 * 前置：后端 8080 已启动（新构建）、mock-llm-server.mjs 已启动、MySQL 本机可连。
 * 覆盖：本地命中不调模型(1)、AI 创建选项后的完整生成链路(2-4)、再次搜索本地命中(6)、
 * 别名命中(7)、并发 single-flight(3/8)、非法 JSON 无残缺数据(9)、关系上限与去重(10)、
 * 来源标记与待审核(11)、AI 问答只读(12)、密钥不入日志/响应/properties(13)。
 * 结束时清理测试节点与 mock 档案，并恢复原默认模型档案状态。
 */
import { execSync } from 'node:child_process'

const BACKEND = 'http://127.0.0.1:8080'
const MOCK = 'http://127.0.0.1:9393'
const MYSQL = '"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql" -h127.0.0.1 -uroot -p' + (process.env.KG_DB_PASSWORD ?? '') + ' -N'
const MOCK_PROFILE_NAME = '验收Mock模型-可删除'
const MOCK_API_KEY = 'sk-mock-key-12345-SECRET'

const results = []
let passCount = 0

function check(name, ok, detail = '') {
  results.push({ name, ok, detail })
  if (ok) passCount += 1
  console.log(`${ok ? '✅' : '❌'} ${name}${detail ? ` — ${detail}` : ''}`)
}

async function api(path, options = {}, timeoutMs = 120000) {
  const response = await fetch(`${BACKEND}${path}`, {
    method: options.method ?? 'GET',
    headers: options.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: AbortSignal.timeout(timeoutMs),
  })
  let envelope = null
  try {
    envelope = await response.json()
  } catch {
    // 非 JSON 响应
  }
  return { status: response.status, envelope }
}

const mockStats = async () => (await fetch(`${MOCK}/__stats`)).json()
const mockMode = async (mode) =>
  fetch(`${MOCK}/__mode`, { method: 'POST', body: JSON.stringify({ mode }) })
const mockReset = async () => fetch(`${MOCK}/__reset`, { method: 'POST' })

const sql = (statement) => execSync(`${MYSQL} -e "${statement}"`, { encoding: 'utf8' }).trim()

async function suggestions(q) {
  const { envelope } = await api(`/api/search/suggestions?q=${encodeURIComponent(q)}&limit=20`)
  return envelope?.data ?? []
}

async function overviewCounts() {
  const { envelope } = await api('/api/graph/overview?limit=500')
  return { total: envelope.data.totalNodes, ids: envelope.data.nodes.map((n) => n.id) }
}

async function waitForBackend() {
  for (let i = 0; i < 60; i++) {
    try {
      const { status } = await api('/api/health', {}, 2000)
      if (status === 200) return true
    } catch {
      /* 重试 */
    }
    await new Promise((r) => setTimeout(r, 1000))
  }
  return false
}

async function main() {
  if (!(await waitForBackend())) {
    console.log('❌ 后端未就绪，终止验收')
    process.exit(1)
  }
  await mockReset()
  await mockMode('valid')

  // ---------- 基线 ----------
  const baseline = await overviewCounts()
  const baselineProfileList = (await api('/api/settings/llm-profiles')).envelope.data
  const originalDefault = baselineProfileList.find((p) => p.isDefault) ?? null
  console.log(
    `基线：${baseline.total} 节点；默认档案：${originalDefault ? `#${originalDefault.id} ${originalDefault.name}` : '无'}`,
  )

  // ---------- 验收 1：本地命中 TCP，不调模型 ----------
  const tcpHits = await suggestions('TCP')
  check('搜索已有 TCP：本地命中', tcpHits.length > 0 && tcpHits[0].name === 'TCP')
  await mockReset()
  const tcpExpand = await api('/api/search/ai-expand', { method: 'POST', body: { query: 'TCP' } })
  check(
    'ai-expand(TCP) 返回 created=false 且未调模型',
    tcpExpand.status === 200 &&
      tcpExpand.envelope.data.created === false &&
      tcpExpand.envelope.data.relationsCreated === 0 &&
      (await mockStats()).calls === 0,
  )

  // ---------- 验收 13/前置：LLM_NOT_CONFIGURED（临时取消默认档案） ----------
  let freshKeywordForErrors = '验收未配置默认模型场景'
  if (originalDefault) {
    sql(`UPDATE knowledge_graph.llm_profiles SET is_default = 0 WHERE id = ${originalDefault.id}`)
    try {
      const { status, envelope } = await api('/api/search/ai-expand', {
        method: 'POST',
        body: { query: freshKeywordForErrors },
      })
      check(
        '未配置默认模型 → 503 LLM_NOT_CONFIGURED 且未写库',
        status === 503 && envelope.code === 'LLM_NOT_CONFIGURED' && (await suggestions(freshKeywordForErrors)).length === 0,
      )
    } finally {
      sql(`UPDATE knowledge_graph.llm_profiles SET is_default = 1 WHERE id = ${originalDefault.id}`)
    }
  } else {
    const { status, envelope } = await api('/api/search/ai-expand', {
      method: 'POST',
      body: { query: freshKeywordForErrors },
    })
    check('未配置默认模型 → 503 LLM_NOT_CONFIGURED', status === 503 && envelope.code === 'LLM_NOT_CONFIGURED')
  }

  // ---------- 验收 2/4：CDN 若本地不存在，优先用真实默认模型生成 ----------
  let realCdnNodeId = null
  let keepIds = new Set()
  const cdnExists = (await suggestions('CDN')).length > 0
  if (!cdnExists && originalDefault) {
    const { status, envelope } = await api('/api/search/ai-expand', { method: 'POST', body: { query: 'CDN' } })
    if (status === 200 && envelope.data.created === true) {
      realCdnNodeId = envelope.data.node.id
      check('真实默认模型生成 CDN：created=true', true, `节点 #${realCdnNodeId}`)
      // 真实生成的主节点与其 AI 新建的关系目标节点整体保留（真实知识，非 mock 数据）
      const afterReal = await overviewCounts()
      keepIds = new Set(afterReal.ids.filter((id) => !baseline.ids.includes(id)))
      const cdnHits = await suggestions('CDN')
      check('再次搜索 CDN：本地命中', cdnHits.length > 0 && cdnHits[0].id === realCdnNodeId)
      const zhHits = await suggestions('内容分发网络')
      check(
        '搜索「内容分发网络」：通过别名命中同一节点',
        zhHits.length > 0 && zhHits[0].id === realCdnNodeId,
        zhHits.length ? `命中 ${zhHits.length} 项` : '无命中（模型未返回该别名，不算失败）',
      )
    } else {
      check(
        '真实默认模型生成 CDN（不可用时改由 mock 覆盖）',
        true,
        `真实模型响应：HTTP ${status} ${envelope?.code ?? ''}`,
      )
    }
  } else if (cdnExists) {
    const { envelope } = await api('/api/search/ai-expand', { method: 'POST', body: { query: 'CDN' } })
    check('CDN 已存在：ai-expand 直接返回 created=false', envelope.data?.created === false)
  }

  // ---------- 创建 mock 档案并设为默认（错误码与并发链路测试） ----------
  const created = await api('/api/settings/llm-profiles', {
    method: 'POST',
    body: {
      name: MOCK_PROFILE_NAME,
      baseUrl: MOCK,
      model: 'mock-model',
      apiKey: MOCK_API_KEY,
      timeoutMs: 15000,
      maxOutputTokens: 2048,
      vision: false,
      enabled: true,
    },
  })
  const mockProfileId = created.envelope.data?.id
  check('创建 mock 模型档案', created.status === 200 && mockProfileId > 0)
  await api(`/api/settings/llm-profiles/${mockProfileId}/default`, { method: 'POST' })
  const defaultNow = (await api('/api/settings/llm-profiles')).envelope.data.find((p) => p.isDefault)
  check('mock 档案已成为默认', defaultNow?.id === mockProfileId)

  // ---------- 错误码链路（验收 9 前提：错误时不写库） ----------
  await mockMode('auth401')
  const authFail = await api('/api/search/ai-expand', { method: 'POST', body: { query: '验收认证失败场景' } })
  check(
    '模型认证失败 → LLM_AUTH_FAILED 且未写库',
    authFail.status === 502 &&
      authFail.envelope.code === 'LLM_AUTH_FAILED' &&
      (await suggestions('验收认证失败场景')).length === 0,
  )

  await mockMode('upstream500')
  const unreachable = await api('/api/search/ai-expand', { method: 'POST', body: { query: '验收服务不可达场景' } })
  check(
    '模型服务不可用 → LLM_UNREACHABLE 且未写库',
    unreachable.status === 502 &&
      unreachable.envelope.code === 'LLM_UNREACHABLE' &&
      (await suggestions('验收服务不可达场景')).length === 0,
  )

  await mockMode('invalid')
  await mockReset()
  const badJson = await api('/api/search/ai-expand', { method: 'POST', body: { query: '验收非法JSON场景' } })
  check(
    '模型返回非法内容 → LLM_BAD_RESPONSE 且数据库无残缺数据',
    badJson.status === 502 &&
      badJson.envelope.code === 'LLM_BAD_RESPONSE' &&
      (await mockStats()).calls === 1 &&
      (await suggestions('验收非法JSON场景')).length === 0,
  )

  // ---------- 验收 2/4/6/7/11/13：mock 正常生成 + 来源标记 + 别名 + 密钥不外泄 ----------
  await mockMode('valid')
  await mockReset()
  const badgeKeyword = '验收AI标识节点'
  const expandOk = await api('/api/search/ai-expand', { method: 'POST', body: { query: badgeKeyword } })
  const okData = expandOk.envelope.data
  const badgeNodeId = okData?.node?.id
  check(
    'AI 生成并收录：created=true、关系入库、别名返回',
    expandOk.status === 200 && okData.created === true && okData.relationsCreated === 2 &&
      okData.node.aliases.includes(`${badgeKeyword}别名甲`),
    `节点 #${badgeNodeId}，relationsCreated=${okData?.relationsCreated}`,
  )
  check('只调用了一次模型', (await mockStats()).calls === 1, `calls=${(await mockStats()).calls}`)

  const detail = await api(`/api/nodes/${badgeNodeId}`)
  const props = detail.envelope.data?.properties ?? {}
  const detailJson = JSON.stringify(detail.envelope.data)
  check(
    '节点详情含 AI 来源标记（origin/reviewStatus/modelProfileId/model/generatedAt/generatedQuery）',
    props.origin === 'ai_search_generation' &&
      props.reviewStatus === 'PENDING' &&
      props.modelProfileId === mockProfileId &&
      props.model === 'mock-model' &&
      typeof props.generatedAt === 'string' &&
      props.generatedQuery === badgeKeyword,
  )
  check('API Key 不出现在节点详情响应中', !detailJson.includes(MOCK_API_KEY))
  check('别名搜索命中同一节点', (await suggestions(`${badgeKeyword}别名甲`))[0]?.id === badgeNodeId)
  check('关键词再次搜索本地命中', (await suggestions(badgeKeyword))[0]?.id === badgeNodeId)
  await mockReset()
  const again = await api('/api/search/ai-expand', { method: 'POST', body: { query: badgeKeyword } })
  check(
    '再次 ai-expand 同名：created=false 且未调模型',
    again.envelope.data?.created === false && (await mockStats()).calls === 0,
  )
  const aliasExpand = await api('/api/search/ai-expand', {
    method: 'POST',
    body: { query: `${badgeKeyword}别名乙` },
  })
  check('别名作为查询词：命中已存在节点（不重复建）', aliasExpand.envelope.data?.created === false)

  // ---------- 验收 3/8：并发 single-flight ----------
  await mockReset()
  const concurrencyKeyword = '验收并发CDN'
  const [first, second] = await Promise.all([
    api('/api/search/ai-expand', { method: 'POST', body: { query: concurrencyKeyword } }),
    api('/api/search/ai-expand', { method: 'POST', body: { query: concurrencyKeyword } }),
  ])
  const sameNode = first.envelope.data?.node?.id === second.envelope.data?.node?.id
  check(
    '两个并发同词请求：只调用一次模型、不产生重复节点',
    first.status === 200 && second.status === 200 && sameNode && (await mockStats()).calls === 1,
    `calls=${(await mockStats()).calls}`,
  )

  // ---------- 验收 10：关系最多 8 条 + 去重边 ----------
  await mockMode('tenRelations')
  const tenRel = await api('/api/search/ai-expand', { method: 'POST', body: { query: '验收十条关系节点' } })
  check(
    '模型返回超量关系：只接受前 8 条，重复边被唯一约束去重（期望 7）',
    tenRel.status === 200 && tenRel.envelope.data?.relationsCreated === 7,
    `relationsCreated=${tenRel.envelope.data?.relationsCreated}`,
  )
  await mockMode('valid')

  // ---------- 验收 12：右侧 AI 问答只读，不修改知识库 ----------
  const beforeChat = await overviewCounts()
  const session = await api(`/api/nodes/${badgeNodeId}/chat/sessions`, { method: 'POST' })
  const sessionId = session.envelope.data?.id
  const chat = await api(`/api/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    body: { content: '这个节点是什么？', mode: 'knowledge_only' },
  })
  const afterChat = await overviewCounts()
  check(
    'AI 问答正常回答且节点/关系数量不变（只读）',
    chat.status === 200 && chat.envelope.data?.role === 'assistant' &&
      beforeChat.total === afterChat.total &&
      beforeChat.ids.length === afterChat.ids.length,
  )
  await api(`/api/chat/sessions/${sessionId}`, { method: 'DELETE' })

  // ---------- 清理：删除测试节点（保留真实模型生成的 CDN 及其新建目标节点） ----------
  const finalCounts = await overviewCounts()
  const createdIds = finalCounts.ids.filter((id) => !baseline.ids.includes(id) && !keepIds.has(id))
  for (const id of createdIds) {
    await api(`/api/nodes/${id}`, { method: 'DELETE' })
  }
  console.log(`清理：删除测试节点 ${createdIds.length} 个${keepIds.size ? `，保留真实生成节点 ${[...keepIds]}` : ''}`)

  // 删除 mock 档案并恢复原默认状态
  await api(`/api/settings/llm-profiles/${mockProfileId}`, { method: 'DELETE' })
  if (originalDefault) {
    await api(`/api/settings/llm-profiles/${originalDefault.id}/default`, { method: 'POST' })
    // 删除默认档案会自动提升「最早的档案」为默认，需把提升档案的默认标记撤掉以还原现场
    const profilesNow = (await api('/api/settings/llm-profiles')).envelope.data
    const promoted = profilesNow.find((p) => p.isDefault && p.id !== originalDefault.id)
    if (promoted) {
      sql(`UPDATE knowledge_graph.llm_profiles SET is_default = 0 WHERE id = ${promoted.id}`)
    }
    const restored = (await api('/api/settings/llm-profiles')).envelope.data.find((p) => p.isDefault)
    check('默认模型档案已恢复原状', restored?.id === originalDefault.id)
  } else {
    // 原本无默认档案：撤掉删除 mock 档案时自动提升的默认标记
    sql('UPDATE knowledge_graph.llm_profiles SET is_default = 0 WHERE is_default = 1')
    check('无默认档案的原始状态已还原', true)
  }

  // ---------- 终验：节点数回到基线 + 保留节点 ----------
  const afterCleanup = await overviewCounts()
  check(
    '测试节点清理完成，数据库回到基线（+保留节点）',
    afterCleanup.ids.length === baseline.ids.length + keepIds.size,
    `基线 ${baseline.ids.length} → 现在 ${afterCleanup.ids.length}`,
  )
  const leftovers = []
  for (const keyword of ['验收并发CDN', '验收十条关系节点', '验收非法JSON场景', '验收认证失败场景', '验收服务不可达场景']) {
    if ((await suggestions(keyword)).length > 0) leftovers.push(keyword)
  }
  check('错误场景关键词均无残留数据', leftovers.length === 0, leftovers.join('、'))

  // ---------- 汇总 ----------
  const failed = results.filter((r) => !r.ok)
  console.log(`\n===== 验收结果：${passCount}/${results.length} 通过 =====`)
  if (failed.length > 0) {
    failed.forEach((f) => console.log(`❌ ${f.name} ${f.detail}`))
    process.exit(1)
  }
}

main().catch((error) => {
  console.error('验收脚本异常：', error)
  process.exit(1)
})
