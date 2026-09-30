# 03 图谱研究｜W1 隔离实施报告

日期：2026-09-25。工作树：`D:/知识图谱-worktrees/assistant-03-research`；分支：`codex/assistant-03-research`。
本轮是领域隔离实现及 fake/mock 验证，不是生产 Agent、网站 API、数据库或模型验收。

## 实际修改与所有权

仅新增以下研究域文件与本报告；18 项同步依赖、公共核心、01/02、既有业务代码均未改动。

- `assistant/research/ResearchAgentToolProvider.java`：七项 `AssistantTool`、角色/效果/schema、执行与限额。
- `assistant/research/ResearchReadRepository.java`：只读窗口、节点、边、来源元数据契约。
- `assistant/research/JdbcResearchReadRepository.java`：绑定参数、查询前范围过滤、真实映射。
- `assistant/research/ResearchEvidence.java`：稳定来源/节点/边 EvidenceRef。
- `assistant/research/ResearchDraftValidator.java`：未执行草稿结构及端点校验。
- `assistant/research/ResearchArtifacts.java`：独立资料成果验证、直接依赖比较、02 质量交接批次。
- 测试：`ResearchAgentToolProviderTest`、`JdbcResearchReadRepositoryTest`、`ResearchComparisonTest`、`ResearchDraftValidatorTest`。

## 七项工具契约

入口 `new ResearchAgentToolProvider(repo, sourceLocator).tools()`，仅 `Role.GRAPH_RESEARCH`；前六项 `READ_ONLY`，最后一项 `DRAFT_ONLY`。
所有参数 schema 为封闭对象，执行前再次验证；授权 Scope 只从宿主 `AssistantTool.Context` 取，模型参数不含 Scope。

| 工具 | 输入关键字段 | 返回与边界 |
|---|---|---|
| `research.search_nodes` | query、limit≤20 | 真实节点 ID、名称/类型/状态、匹配依据、别名/归属、范围内来源可用标志；匹配分层后 ID 排序、窗口标志。 |
| `research.retrieve_chunks` | query、maxSources≤8、maxChars≤8000 | documentId/chunkId/contentHash/sourceId、单元类型/位置、正文、候选窗口和缺口；最多 4 份资料、每份 16 候选，先为每份有命中资料预留字数再轮转取样。 |
| `research.read_source` | documentId、chunkId、expectedContentHash、maxChars≤4000 | 精确重读、真实 hash/定位；正式状态或 hash 变化明确拒绝，正文截断标明。 |
| `research.get_node` | nodeId | 有界别名/归属、真实状态和 updatedAt、来源可用标志；updatedAt 不称强版本。 |
| `research.get_neighbors` | nodeId、depth=1/2、maxNodes≤50、maxEdges≤100 | 有界节点/边，保留每条真实 sourceNodeId→targetNodeId；截断和来源/审核缺口可见。 |
| `research.find_paths` | sourceNodeId、targetNodeId、maxDepth=1/2、maxPaths≤10 | 仅顺真实有向边，限定分支和路径数；窗口截断时不声称无路径。 |
| `research.draft_expansion` | targetLibraryId、nodes≤10、edges≤10 | 仅整理已给定建议；新节点用 `draft:*`，已有端点重查范围；`recommendationOnly=true`、`executionAllowed=false`、`saved=false`。 |

不提供任意 SQL、文件路径、URL、写工具、隐藏模型生成或 ActionProposal。`ToolResult` 只用公共 OK/PARTIAL/ERROR 与领域 errorCode。

## 范围、来源和覆盖语义

- SQL 对库、资料、节点非空维度取交集；`library_nodes` 是图谱库归属，`node_evidence`/`edge_evidence` 是资料关联；边两端和资料证据均先过滤再 LIMIT。
- 资料库归属同时考虑 `documents.library_id` 与 `document_topic_assignments`；按资料分组去重，检索词绑定参数，无原始 SQL 拼接。
- 资料原文仅 formal_only：document COMPLETED、metadata active 或旧记录 null、unit READY/OCR_OK；检索与精确重读同口径。
- 冻结 `SourceLocator` 对非空 nodeIds 不支持；两项来源工具返回 `NODE_SCOPE_UNSUPPORTED`，不剥除 nodeIds 扩权。
- 检索按有界词项匹配；资料候选按最早匹配 chunk ID 入窗，资料内按词项命中数降序、chunk ID 升序；不是全库语义相关 Top-N。
- 来源正文只通过 `SourceLocator.chunk()` 展示/重读；JDBC 仅检索匹配和元数据。相同正文在 A/B 仍分别保留真实 doc/chunk 归属。
- `research:chunk:{documentId}:{chunkId}:{contentHash}:scope:{sha256}` 是同一授权观察范围内的稳定来源 ID；sha256 基于排序后的 Scope 四维规范串。节点/边 ID 同样带 scope 指纹；EvidenceRef 仍保留真实对象版本、授权范围、定位和固定 240 字摘录。
- 因公共 Runner 用完整 EvidenceRef 比对同一 sourceId，单资料与 A+B 重读、A/B 共享节点/边必须用不同的范围限定 ID。它们的真实 ObjectRef 可相同；不能把某任务的证据 ID 当成另一任务的授权。相同范围、对象版本、定位及内容再次取证仍得到相同 EvidenceRef；同 ID 的内容冲突继续由 Runner 拒绝。
- 多资料检索为每份有候选资料预留 `maxChars/匹配资料数` 字数（余数按确定顺序分配），`documentCharQuota` 与 `usedCharsByDocument` 显示配额；名额不足保留 `REQUESTED_DOCUMENTS_UNCOVERED`，正文/候选截断仍传播 PARTIAL。
- `hasMore`、`queryWindowExhausted` 只描述本次有界候选窗口；`completeCoverage=false` 并带 `FULL_CORPUS_COVERAGE_NOT_VERIFIED`，不把末页或单次匹配说成全文研究。
- 图谱 active 与原文事实、人工核验不同。只白名单提取 properties_json 的 origin/reviewStatus；缺失为 unknown，来源不可用与待核验显式保留。
- “它/两者/继续比较”这类未解析指代返回 `REFERENT_UNRESOLVED`，不猜跨任务聊天指代；宿主 resolvedReferents 的生产传递未接线。

## A/B 成果、比较与 02 交接

宿主应构建两个只授权单份资料的 `GRAPH_RESEARCH` 任务 A/B，比较任务显式直接依赖二者且授权 A+B；本包只接收当前任务由宿主传入的原始直接依赖 `Artifact`，不提供任意 artifact ID 查询。
建议每份成果 `Artifact.dataJson` 形状（额外目标/缺口字段可保留，但不得凭空升级覆盖）：

```json
{"kind":"document_research","documentId":11,"objective":"核查某教材的 TCP 说明","claims":[{"claimId":"a-1","text":"待核验论断","kind":"source_fact","sourceIds":["research:chunk:11:101:h101:scope:1577a1315048d22276fbc9c323f0ebf023d9eff1474f97d3fd7bd27a7e6248ab"]}],"coverage":{"completeCoverage":false,"truncated":true,"uncoveredDocumentIds":[]}}
```

`kind` 限 source_fact/graph_fact/model_explanation/conflict/gap；claimId 唯一。所有 sourceIds 必须存在于原始 `Artifact.evidence`，来源指针再核对对象种类、doc/chunk/hash 和授权范围；模型自报 JSON 不足以建立可信引用。
比较校验 producerTaskId、直接依赖集合、A/B 不重复、expectedDocumentIds 和两份授权范围，至少尝试分别重读关键 source_fact；返回 positions、原始 artifactId、checkedSourceIds、限制。
目前比较只做结构化并列，不做模型语义裁判：`semanticSupport=not_evaluated`、`winner=null`、`completeCoverage=false`、结果 `PARTIAL`；不能据此选出“更正确”教材。
公共核心控制调度：A 失败/needs_input 则比较 BLOCKED 且 B 可独立保留；A partial 仅在比较任务允许 partial 依赖时进入，产物继续传播 PARTIAL。fake 决策测试覆盖并行隔离和失败阻断。

交给 02 的映射：`claims[{claimId,text,kind,sourceIds}]` → 原始 `Artifact.evidence` 中 `SOURCE` → `QualityArtifactReader.SourcePointer(sourceId,documentId,chunkId,expectedHash)`；文本/类型留在原研究 Artifact，质量 DirectArtifact 只带 claimId/来源指针。
`qualityHandoff` 输出 `eligibleBatches`（每批≤10 claim、每 claim≤2 来源、总计≤20）以及 `deferredClaimIds`/`needsSourceBatching`，超限原 claim 仍在原 Artifact 中，不伪装已提交复核。
02 只能核对身份、归属和 hash；引用有效不等于论断语义被支持，更非人工核验、正式评分或入库确认。生产宿主必须校验 runId、consumerTaskId 和直接依赖后才可调用。

## 隔离验证

在 `backend-java` 仅运行以下明确类；未运行全量或 IntegrationTest：

```powershell
.\mvnw.cmd '-Dtest=AssistantRuntimeTest,DecisionParserTest,ResearchAgentToolProviderTest,JdbcResearchReadRepositoryTest,ResearchComparisonTest,ResearchDraftValidatorTest' test
```

定向补修后最后结果 BUILD SUCCESS：公共核心 43 项（28+15），研究域 24 项（7+5+10+2），合计 67/67，通过 0 失败/错误/跳过。
研究测试使用 fake 仓储/SourceLocator/宿主 Artifact/DecisionProvider 和 mock JdbcClient；未启动 Spring、真实数据库或真实模型。
已覆盖正常检索→真实 hash 重读、稳定引用、正式状态/hash 变化、node-scope 拒绝、入/出方向、两跳与反向不可达、草稿端点、SQL 过滤与实际行映射、A/B 隔离/阻断、伪造/跨资料来源、质量批次上限。
新增实际 Runner 回归：单资料来源→A+B `read_source` 重读并合并两条有效证据；A/B 同节点与共享边合并；同 ID 但正文摘录变化仍报 `EVIDENCE_ID_CONFLICT` 且保留首条证据。公共核心既有 `sourceIdCannotBeReboundToDifferentEvidence` 同 ID 异版本回归亦在本次 43 项中通过。
新增多资料长正文测试：maxSources=2/maxChars=2000 时 A/B 各保留 1000 字和独立来源；maxSources=1/maxChars=200 时仅覆盖 A，B 的未覆盖与结果上限明确返回。
按清单的 UTF-8、CRLF→LF、保留结尾换行规则复核 18 项冻结依赖：18/18 存在且规范化 SHA-256 一致，0 差异。

## 未验证与交总控决定

1. 未做生产注册、可信宿主成果适配、API/鉴权、自然语言规划器、正式语义比较、持久化、前端或端到端网站验收；不应把本包当已上线能力。
2. mock SQL 不等于 MySQL 实库兼容；需总控后续安排真实库只读查询计划、状态/归属数据核验、索引和查询超时，但本轮不连接数据库。
3. 当前取证是有界词项窗口，无法证明全文穷尽；资料超过 4 份明确拒绝。真实超时/取消无法强制中断 JDBC，需生产数据源统一超时策略。
4. 来源 hash 与状态在多次查询间仍可能竞态；`read_source` 做前后元数据重查，仍不声称事务一致快照。图谱 updatedAt 也不是强版本。
5. `get_node` 的来源字段只表示当前范围内有无正式证据关联，不返回可直接重读的节点来源指针；若生产验收要求逐条节点证据，需总控批准更细的有界来源契约。
6. 比较 Artifact 必须由宿主从当前运行直接依赖传入；当前领域接口不能自行验证跨运行隔离，需总控生产宿主保证。学习交接不含分数、掌握度、进度凭证。
7. 旧工作树中已有协调/依赖同步文件保持原样；本轮只新增研究域文件及本报告。未提交、合并、push、部署，也未进入 04/05 或 W2。
