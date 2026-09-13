/**
 * 验收用 Mock LLM 服务器（OpenAI /chat/completions 兼容，最小实现）。
 * 用途：在不依赖真实大模型的前提下验证 POST /api/search/ai-expand 的
 * 生成/去重/并发/错误码路径。仅监听本机回环地址。
 *
 * 端点：
 *   POST /chat/completions   按 /__mode 决定的剧本返回内容（valid 默认 / invalid / auth401 / upstream500）
 *   GET  /__stats            { calls: 已收到的 completions 次数 }
 *   POST /__reset            清零计数
 *   POST /__mode             { "mode": "valid" | "invalid" | "auth401" | "upstream500" }
 */
import http from 'node:http'

const PORT = 9393
const state = { mode: 'valid', calls: 0 }

/** 从 user 消息中提取「知识点名称：X」的 X。 */
function extractQuery(messages) {
  for (const message of messages ?? []) {
    if (message.role !== 'user') continue
    const match = /知识点名称：(.+)/.exec(message.content ?? '')
    if (match) return match[1].trim()
  }
  return '未知关键词'
}

function buildPayload(query) {
  if (query === 'CDN') {
    return {
      node: {
        canonicalName: 'CDN',
        nameEn: 'Content Delivery Network',
        aliases: ['内容分发网络', 'CDN加速'],
        type: 'concept',
        definition:
          '内容分发网络（CDN）是分布在不同地理位置的服务器网络，通过就近缓存与智能调度，把网站内容快速分发给用户，降低访问延迟并减轻源站压力。',
      },
      // 关系目标只使用库中已存在的 TCP/UDP（种子数据），验收「目标节点已存在时复用原 ID」
      relations: [
        { targetName: 'TCP', targetType: 'concept', targetDefinition: '面向连接的可靠传输层协议。', relationType: '依赖' },
        { targetName: 'UDP', targetType: 'concept', targetDefinition: '无连接的传输层协议。', relationType: '相关' },
        { targetName: 'TCP', targetType: 'concept', targetDefinition: '面向连接的可靠传输层协议。', relationType: '承载' },
      ],
    }
  }
  return {
    node: {
      canonicalName: query,
      nameEn: `${query} Network`,
      aliases: [`${query}别名甲`, `${query}别名乙`],
      type: 'concept',
      definition: `这是验收测试为「${query}」生成的定义文本，用于验证搜索未命中时由 AI 生成并收录的完整流程，内容保持客观并具备足够长度。`,
    },
    relations: state.mode === 'tenRelations'
      ? [
          // 自指 1 条（应被剔除）+ 有效 10 条（含 1 条完全重复），上限 8、去重后应入库 7 条
          { targetName: query, targetType: 'concept', targetDefinition: '自指。', relationType: '自指' },
          { targetName: 'TCP', targetType: 'concept', targetDefinition: '可靠传输。', relationType: '相关' },
          { targetName: 'TCP', targetType: 'concept', targetDefinition: '可靠传输。', relationType: '相关' },
          { targetName: 'UDP', targetType: 'concept', targetDefinition: '无连接传输。', relationType: '相关' },
          { targetName: 'UDP', targetType: 'concept', targetDefinition: '无连接传输。', relationType: '依赖' },
          { targetName: 'TCP', targetType: 'concept', targetDefinition: '可靠传输。', relationType: '依赖' },
          { targetName: 'TCP', targetType: 'concept', targetDefinition: '可靠传输。', relationType: '承载' },
          { targetName: 'UDP', targetType: 'concept', targetDefinition: '无连接传输。', relationType: '承载' },
          { targetName: 'TCP', targetType: 'concept', targetDefinition: '可靠传输。', relationType: '关联' },
          { targetName: 'UDP', targetType: 'concept', targetDefinition: '无连接传输。', relationType: '关联' },
          { targetName: 'HTTP', targetType: 'concept', targetDefinition: '超文本传输协议。', relationType: '相关' },
        ]
      : [
          { targetName: 'TCP', targetType: 'concept', targetDefinition: '面向连接的可靠传输层协议。', relationType: '依赖' },
          { targetName: 'UDP', targetType: 'concept', targetDefinition: '无连接的传输层协议。', relationType: '相关' },
        ],
  }
}

function sendJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
  res.end(JSON.stringify(body))
}

const server = http.createServer((req, res) => {
  const url = req.url ?? ''
  if (req.method === 'GET' && url.startsWith('/__stats')) {
    return sendJson(res, 200, { calls: state.calls, mode: state.mode })
  }
  if (req.method === 'POST' && url.startsWith('/__reset')) {
    state.calls = 0
    res.writeHead(200)
    return res.end('ok')
  }
  if (req.method === 'POST' && url.startsWith('/__mode')) {
    let raw = ''
    req.on('data', (chunk) => (raw += chunk))
    req.on('end', () => {
      try {
        state.mode = JSON.parse(raw).mode ?? 'valid'
      } catch {
        state.mode = 'valid'
      }
      sendJson(res, 200, { mode: state.mode })
    })
    return
  }
  if (req.method === 'POST' && url.startsWith('/chat/completions')) {
    let raw = ''
    req.on('data', (chunk) => (raw += chunk))
    req.on('end', () => {
      state.calls += 1
      if (state.mode === 'auth401') {
        return sendJson(res, 401, { error: { message: 'Invalid API key provided' } })
      }
      if (state.mode === 'upstream500') {
        return sendJson(res, 500, { error: { message: 'mock upstream failure' } })
      }
      let messages = []
      try {
        messages = JSON.parse(raw).messages
      } catch {
        messages = []
      }
      const query = extractQuery(messages)
      const content =
        state.mode === 'invalid'
          ? '抱歉，我无法按要求生成结构化内容，上面的要求我都做不到。'
          : JSON.stringify(buildPayload(query))
      // 200ms 延迟：给并发 single-flight 测试留出请求堆积窗口
      setTimeout(() => {
        sendJson(res, 200, {
          id: 'mock-completion',
          choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }],
        })
      }, state.mode === 'valid' ? 200 : 0)
    })
    return
  }
  res.writeHead(404)
  res.end('not found')
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`mock-llm-server listening on http://127.0.0.1:${PORT}`)
})
