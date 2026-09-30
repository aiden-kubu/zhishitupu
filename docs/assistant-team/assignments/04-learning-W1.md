# 04 学习教练｜W1 任务单与接令检查

准备日期：2026-09-25。总控：原主任务；执行者：原 04「学习教练」。
工作目录：`D:/知识图谱-worktrees/assistant-04-learning`；分支：`codex/assistant-04-learning`。
计划报告：`D:/知识图谱-worktrees/assistant-04-learning/docs/assistant-team/reports/04-learning-W1.md`。

## 0. 当前闸门：只做接令检查

本任务单定义后续 W1 隔离实施范围，**不是立即开发授权**。本轮仅阅读、核对和回复：不修改任何文件，不创建 W1 报告，不编译、不运行测试、不安装依赖。接令检查返回总控复核后，须收到另发的正式执行指令才可实施。

项目负责人已确认 01、02、03 的 W1 阶段交付，三个任务均保持待命，不再改动。03 最终基线为 67 项指定测试（核心 43、研究域 24）及两项补修后的实现；这些不是网站上线证明。
本轮只准备原 04；05 尚未启动。严格人工派发，不新建/替换任务，不派生子 Agent，不研究或运行 App Server、外部控制器、跨任务接管，不解锁、不调用 .local/assistant-team/control.mjs。
历史 COORDINATION、W0 报告、manifest 中的控制方式、旧模型及旧待办说明不覆盖本任务单和负责人最新指令；manifest 不能作为当前桌面模型或其他任务运行状态的证据。
模型要求仍为 `gpt-6-sol / high`，由负责人在原桌面任务确认。无法可靠回读时如实说明，不自行降配，不探测其他任务。

### 0.1 本轮检查并回复

1. 实际绝对目录、分支、Git 状态。区分原有 COORDINATION 修改、原 W0 报告、总控本次同步文件，不清理或覆盖任何既有改动。
2. 完整阅读下面 10 份文档及关键代码；核对依赖清单全部 29 项存在，规范化 SHA-256 匹配。规范是 UTF-8、CRLF 转 LF、保留结尾换行，不 trim。
3. 概括八项工具、可信素材交接、路径只读与草稿/正式进度边界，指出不支持的范围与缺少的契约。
4. 给出仅领域内实现的文件/隔离测试计划；发现缺文件、hash 不符或契约不能安全满足则报告具体阻塞，不能自行同步、改冻结依赖或扩大范围。
5. 回复计划报告的绝对路径，并明确“尚未开发、未修改文件、未运行测试；等待总控正式执行指令”。完成即停止。

### 0.2 必读文档及代码

相对路径均从本工作树根目录解析：

1. `docs/assistant-team/COORDINATION.md`（控制方式等以本单覆盖）。
2. `docs/智能助手模块审计与重规划-2026-09-22.md`。
3. `docs/assistant-team/W0-REVIEW.md`。
4. `docs/assistant-team/W1-CORE.md`。
5. `docs/assistant-team/reports/04-learning-W0.md`（历史提案，不是接口定义或额外授权）。
6. 本任务单 `docs/assistant-team/assignments/04-learning-W1.md`。
7. `docs/assistant-team/references/04-learning-W1-DEPENDENCIES.json`。
8. `docs/assistant-team/references/01-ingestion-W1.md`。
9. `docs/assistant-team/references/02-quality-W1.md`。
10. `docs/assistant-team/references/03-research-W1.md`（含最后补修及范围限定引用 ID）。

关键只读代码：公共 contract/runtime/tool；SourceLocator/JdbcSourceLocator；ResearchReadRepository/JdbcResearchReadRepository、ResearchArtifacts、ResearchEvidence、ResearchAgentToolProvider 及对应测试；QualityArtifactReader。
同时阅读既有 `backend-java/src/main/java/com/knowledgegraph/learning/LearningPathService.java`、`LearningPathController.java`、`backend-java/src/main/resources/db/migration/V11__learning_paths.sql`，核对真实表字段与副作用。读取前端文件前先完整阅读 frontend/AGENTS.md；本轮不要求改前端或恢复缓存。

## 1. 正式实施后的阶段目标

接入 `Role.LEARNING_COACH`，在公共核心上提供范围内学习事实、可信研究/质量素材消费，以及未保存路径草稿、差异、分层讲解和非正式练习。
复用已验收研究域的只读仓储/成果校验能力及原文定位，不建立另一套公共 Runner、角色调度、成果存储或正式学习系统。
本轮验证的是领域校验、只读适配、草稿整理和 fake 决策链；真实自然语言生成质量、个性化学习效果和生产模型接线均不在此阶段。

## 2. 八项领域工具

提供 `LearningAgentToolProvider.tools()` 或等价的领域入口，仅授权 LEARNING_COACH。前五项 READ_ONLY，后三项 DRAFT_ONLY；不注册 Spring Bean，不开放 HTTP，不开放 ActionProposal。
参数使用封闭 JSON schema、明确必填/类型/上下限/枚举，并在执行前再次验证；scope/profile/生产运行身份不能由模型参数重写。

| 工具 | 本轮职责与最低边界 |
|---|---|
| learning.read_current_path | 只读 local-default 当前路径、真实数据库 pathId/stepId/stepKey/节点/顺序/状态与观察时间；无路径、范围不足、版本不可用分别表达。 |
| learning.read_graph_scope | 获取宿主范围内有界图谱候选（最多 50 节点、100 边），真实节点状态、归属、方向与覆盖；候选不等于全库最相关或完整先修图。 |
| learning.read_evidence | 接收 documentId/chunkId/expectedContentHash 精确重读（单段最多 4000 字）；复用 SourceLocator，核对正式状态、归属与 hash。首次指针来自可信研究素材，不另写全库搜索器。 |
| learning.read_learning_inputs | 读取当前任务的原始直接依赖研究/比较/质量素材（至多 8 个成果），核对生产者、授权范围、引用、覆盖与缺口；不能读任意成果或其他任务聊天。 |
| learning.read_assessment_summary | 本 W1 明确返回正式测评尚不可用、有效成绩/正式弱项为空；不能以旧客户端汇总替代 05 的有效凭证，不要求 05 提前实施。 |
| learning.draft_path | 校验/整理给定的 2–8 步 new/adjust/replace 路径建议，重查目标库及节点，计算有依据的顺序说明、时长估计、当前路径差异；不保存。 |
| learning.draft_explanation | 校验/整理单节点分层讲解（最多 8 个内容块），分开原文引用、系统/图谱事实、模型解释与建议，缺证据或陈旧引用不包装成事实已核验。 |
| learning.draft_practice | 校验/整理单节点最多 5 道非正式练习，含提示、自查参考答案和解释；mode=informal_practice，无正式试卷/尝试 ID，无记分或阶段推进。 |

草稿工具处理显式给定的有界模型建议，不在内部调用真实模型；模型生成属于后续 DecisionProvider。隔离 fake 决策场景应实际产出一份 2–8 步路径、一份分层讲解及至少两道非正式练习，不能只返回工具说明。
需要复习时，W1 只能根据学习目标、材料缺口作一般复习建议，并注明非正式弱项；无正式成绩不生成掌握率、合格判定或“已掌握”。

### 2.1 范围、图谱及原文

- HostScope 的 libraryIds/documentIds/nodeIds 非空维度取交集；真实归属在查询前过滤，不能先全局 LIMIT 再隐藏越界项。nodeIds 是硬允许集合，不是无限探索种子。
- 复用冻结 ResearchReadRepository/JdbcResearchReadRepository 读取节点、边与原文元数据；新领域所需的有界候选查询可放 assistant/learning 的 JdbcLearningReadRepository。禁止改既有 GraphService/NodeService/学习服务。
- 库归属依真实 library_nodes；资料关联依 node_evidence/edge_evidence→chunk→unit→document。文档同时考虑主库和 topic assignment；不能凭同名、同库或模型声明认定资料支持某节点。
- 所有可见边保留真实 source→target 方向；边类型不自动等于先修关系。未定义先修语义时只能表述图谱关系/规划建议，不把任意 RELATED、contains 或入边倒装成先修事实。
- 保留 active/inactive、origin/reviewStatus 的真实状态和不确定项；active、来源存在、引用有效都不是人工核验。草稿选用节点应真实存在且 active，失效节点不能静默替换。
- 图谱候选上限、列表截断、可见集/查询窗口、事实来源缺失分别表达；统计必须同范围同口径，未查询总量不得编造。空结果不代表项目无资料。
- 原文只读 formal_only：文档 COMPLETED、metadata active 或旧记录 null、单元 READY/OCR_OK；状态与 hash 变化均降级/拒绝旧引用。原文正文复用 SourceLocator.chunk，不另写全文读取路径。
- 冻结 SourceLocator 不支持非空 nodeIds；learning.read_evidence 及依赖原文重读的步骤须明确 NODE_SCOPE_UNSUPPORTED/材料不足，不得去掉 nodeIds 扩权。纯节点事实/未证实建议可明确降级，但不能冒充有原文支持。
- 研究只返回节点“存在范围内来源”标志，不提供逐条节点来源指针；04 不要求 03 扩权。无法从可信材料证实 node↔source 关系时标明缺口，或在 learning 内通过真实 node_evidence 有界查证，禁止由名称推断。
- 不泄露范围外库名、主库 ID、节点、路径目标、未授权步骤或来源信息。

### 2.2 当前路径只读、身份和差异

- 既有 `PathView.version=2` 是格式版本，绝非正式 revision。允许 learning 内 SELECT 真实 path/step 数据库 ID；revision 保持 null/unavailable，不能自造正式版本。
- local-default 是固定共享档案，不接受任意 profileKey，也不宣称多用户隔离。路径状态/visitedAt/assessmentScore 是“系统现有记录”，不等于已学会、实际时长或有效正式分数。
- 查询只限已授权范围：先核实路径库和步骤节点的真实归属。狭窄资料/节点范围无法覆盖整条路径时，返回有界的不足/不可用结果，不把隐藏步骤的局部快照当完整路径生成删改差异；不得泄露不可见路径元数据。
- 对异常步骤数量、空步骤、缺节点、重复位置等有明确限制，不直接调用存在全量暴露/副作用风险的旧 current/save 流程。
- 观察令牌可对真实路径/步骤快照做确定性指纹，用于草稿基线与陈旧检测；须标为 snapshot_fingerprint，不是 revision、事务一致性或写入乐观锁。只用 path.updatedAt 不足以反映步骤变化。
- new/adjust/replace 仅是建议模式。adjust/replace 必须匹配刚重读的真实基线 ID/观察令牌，否则拒绝或报告基线陈旧；缺完整基线时不输出看似完整的 kept/removed/reordered。
- 差异由领域确定性计算，包含保留/新增/移除/改序、原已完成步骤的影响与待确认事项；模型不能直接指定“历史已保留/分数已转移”。新草稿只有 draft:* 局部步骤标识，不编造新的正式 pathId/stepId/revision。
- 保留现有基线路径的真实 ID 作为只读 basePathRef 不等于创建正式路径。新草稿不设置 completed/current/locked、正式成绩或有效进度；状态固定未保存。
- 固定 saved=false、executionAllowed=false、recommendationOnly=true、progressChanged=false。时长是估计，明确前提；不得把已访问时间或自评当实际投入。缺时间预算可询问/标 unknown，不捏造个人日程。
- 不调用 save、markVisited、complete、recordAssessment 或现有 PUT/POST。路径历史保留、缓存自动回写、版本表和统一完成规则仍由总控在后续 W2 排期，本阶段不修改这些问题。

### 2.3 可信素材与公共核心交接

- 原始 Artifact 只从当前任务直接依赖取得；核对原 artifactId、producerTaskId、scope、outcome、evidence 和限制。没有直接依赖权限时不得追溯读取上上游成果；需要额外素材由宿主明确列为直接依赖。
- AssistantTool.Context 不包含 Artifact/历史工具观察；不得修改公共 Context。允许在 learning 包定义只读 LearningContextReader（或等价宿主边界），以 runId/consumerTaskId 查当前已授权原始成果及可信工具观察；本阶段仅提供契约与 fake 宿主，不实现任意全局成果查询或生产适配器。
- 宿主边界须区分原始研究/比较 Artifact 与质量工具的可信原始观察；从模型 dataJson 自报 CURRENT、completeCoverage 或 quality_verified 不能证明质量工具已核查。
- 03 document_research 使用已冻结 ResearchArtifacts.study 等校验；document_comparison 只作为结构化并列材料，保留 checkedSourceIds、未证实 claim、coverage/limitations、semanticSupport=not_evaluated，不自称获得语义胜负。
- 02 核查只证明特定原成果/claim/source 的身份、归属和 hash。必须匹配目标 artifactId、producerTaskId、claimId 与原 sourceId/真实对象版本；未覆盖 claim 不能套用某次 completeCoverage。没有可信质量观察则标质量状态不可用，不能用模型摘要补造。
- 可在 fake 宿主中提供与 02 实际契约一致的只读观察映射：artifactId、producerTaskId、checks[{claimId,referenceStatus,semanticSupport,sources[{originalSourceId,chunkId,expectedHash,actualHash,status}]}]、requestedClaims/currentClaims、completeCoverage、observedAt；这些必须来源于宿主已捕获的工具结果，不作为模型入参的可信字段。对应 Artifact.evidence 和原研究引用需交叉校验。
- 关键原文重读后才作为当前材料；陈旧/归档/缺失/正文截断、研究 partial、未解冲突继续传递。来源有效不等于文本语义获证实；教学补充和练习答案是待核验模型建议。
- sourceId 是范围限定观察 ID，不是授权令牌：03 已将 Scope 指纹写入 ID。保留原始引用，不用旧字符串切割规则。不同范围下同一 documentId/chunkId/hash 可有不同 ID；可以并存，但不能统计为多份独立证据。真实归属不同的 A/B 相同正文/hash 不能被合并掉。
- 新学习域证据同样须稳定、有限且避免同 ID 携带不同范围/摘录造成 Runner 冲突；不要改写上游 EvidenceRef 的 scope/excerpt。版本变化应标陈旧，不替换旧 ID 的对象。
- 所有引用须来自可信工具观察/直接依赖并经过身份与范围核对；草稿中的自报 sourceIds、nodeIds、关联、质量结论不直接可信。已知证据不足时报告材料缺口，不扩大宿主权限。
- 不把 research.* 工具描述符直接开放给 LEARNING_COACH、不伪装角色绕过允许列表。可以复用冻结的只读仓储/校验类；learning.* 工具自行显式声明允许角色、效果与参数。
- 核心失败语义不变：必需依赖 FAILED/NEEDS_INPUT → 下游 BLOCKED；独立成果保留。显式接受 PARTIAL 才运行，下游继续 PARTIAL；不能把“已交草稿”升级为“学习完成”。
- 缺节点/目标/素材时以领域错误或 PARTIAL 表达，由决策层给 NEEDS_INPUT；ToolStatus 沿用公共枚举。不得自己新增调度 outcome、开后台任务或持久化聊天。

### 2.4 草稿、预算与可验证性

- 三项草稿工具都要验证未知字段、必填字段、重复局部 ID、越界节点/来源、长度/数量/时间上限；路径步骤顺序与先修解释明确区分图谱事实和推断。
- 分层讲解区分 source_fact/system_fact/graph_fact/model_explanation/recommendation；事实引用与解释文字分开，semanticSupport 未经正式语义评估保持 not_evaluated。无证据可以给明确标注的建议，不允许挂伪引用。
- 非正式练习可带答案供自查，但不得生成 assessmentId/attemptId、正式得分、passed、mastery 或阶段解锁回执；不会调用旧汇总分数接口。
- query/字符串、工具内查询数、节点/边/资料/成果/claim/内容块均有界；每次来源重读≤4000 字、单次成果读取≤8 个、草稿步骤2–8、练习1–5。整体仍遵守 ToolResult.dataJson 32000、Artifact.dataJson 16000、EvidenceRef.excerpt 2048、ToolRegistry 约64K限额，超限明确截断或错误。
- 超时和取消检查复用核心；SQL mock 不证明真实查询可中断，数据源超时、事务一致性由后续集成验收。不输出连接信息、密钥、任意路径或底层异常消息。

## 3. 文件所有权与冻结依赖

总控仅受控同步 29 项只读依赖，详见 references/04-learning-W1-DEPENDENCIES.json：
- 公共核心 9 个源码、2 个核心测试；
- 01 SourceLocator/JdbcSourceLocator；
- 02 QualityArtifactReader 接口；
- 03 最终验收版 6 个研究源码、4 个研究测试；
- W1-CORE、W0-REVIEW、01/02/03 W1 报告参考副本共5份。

复制用于编译/契约/回归基线，不是 Git 合并、生产注册或跨任务派发。03 测试 baseline=24，公共核心 baseline=43；不能把这67项计为04新增测试。
旧04 W0报告及 COORDINATION 原有改动不动。参考报告中的相对链接若无法在04解析，用依赖清单的来源路径只读核对，不据此改上游。

**仅收到正式执行指令后**允许新增/修改：
- backend-java/src/main/java/com/knowledgegraph/assistant/learning/**
- backend-java/src/test/java/com/knowledgegraph/assistant/learning/**
- docs/assistant-team/reports/04-learning-W1.md

禁止修改 contract/runtime/tool、assistant/source、assistant/quality、assistant/research 及其测试；禁止改既有 learning/graph/chat/ingestion 业务服务/Controller/DTO、前端、pom、配置/密钥、数据库迁移、协调/任务单/清单、主工作树或其他工作树。禁止提交、合并、push、部署、安装新依赖、派生 Agent。
确需改变契约时报告最小需求并停下，不要求01～03返工、不启动05。后续正式路径版本和成绩体系不属于当前扩权范围。

## 4. 正式执行后的隔离验证和报告

本节仅是后续测试计划，接令检查不得执行。使用 fake 仓储/SourceLocator/可信宿主/DecisionProvider 与 mock JDBC，不起 Spring、不连接真实数据库或模型、不运行全量 test 或 IntegrationTest。
建议类：LearningAgentToolProviderTest、JdbcLearningReadRepositoryTest、LearningArtifactHandoffTest、LearningDraftValidatorTest；至少覆盖：

- 无路径/空项目/无正式测评；旧分数、visitedAt、完成状态不能升级为正式掌握度。
- 库/资料/节点交集、同名跨库、路径/步骤越界、路径局部覆盖不能生成完整差异；SQL 过滤在 LIMIT 前、绑定参数、实际 RowMapper。
- 实际 pathId/stepId 与格式 version=2 区分、步骤变化导致快照陈旧、adjust/replace基线不匹配、new/adjust/replace差异、已完成步骤影响。
- 真实关系方向、不把任意边当先修、循环/缺先修/截断说明，失效节点拒绝，时间预算与估计相区分。
- 真实来源首个可信指针→精确重读、hash/归属/正式状态变化、nodeIds拒绝、不按相同hash抹去A/B归属。
- 上游原始研究/比较成果、质量观察交叉核对、伪造/非直接依赖/跨run或consumer被fake宿主拒绝、未知claim/source、局部质量覆盖、不同范围引用去重统计。
- 实际 Runner 中研究/质量材料→学习草稿：角色隔离、允许列表、引用合并不冲突、依赖失败阻断和partial传播；无生产宿主时不要宣称全链路上线。
- 路径/讲解/至少两道非正式练习成功及越界/伪造正式ID/隐藏写入/额外字段拒绝，证据不足诚实降级；整体输出预算、稳定错误。
- 对既有写服务零调用；不连接HTTP、真实DB或模型。生产可信宿主的真正隔离与数据库兼容单列未验证。

指定测试命令（仅正式执行阶段，在本工作树 backend-java）：

```powershell
.\mvnw.cmd '-Dtest=AssistantRuntimeTest,DecisionParserTest,ResearchAgentToolProviderTest,JdbcResearchReadRepositoryTest,ResearchComparisonTest,ResearchDraftValidatorTest,LearningAgentToolProviderTest,JdbcLearningReadRepositoryTest,LearningArtifactHandoffTest,LearningDraftValidatorTest' test
```

类名若需调整，记录真实存在的明确列表，不用通配符、不跳过测试不存在错误。报告将核心43/研究24/学习新增分开计数，核对29项冻结依赖hash。
报告写明实际文件、八项工具/schema、支持和拒绝的范围、可信宿主最小契约、来源及质量语义、路径身份/差异、不可用的正式测评、验证命令及数量、未做项和后续集成注意；必须标明当前网站不可实际验收。
完成后汇报并停止，等待总控技术复核和负责人最终验收；不得自动进入05、生产集成或W2。

