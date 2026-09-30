# 03 图谱研究｜W1 阶段任务单

日期：2026-09-24。原任务 ID：`01a0c6f9-fdb5-7d72-afa7-3eeda840413a`。
工作目录：`D:/知识图谱-worktrees/assistant-03-research`。
预期分支：`codex/assistant-03-research`。

## 0. 当前授权与阶段门槛

**本轮仅授权接令检查，尚未授权实施。** 完整阅读任务单及依赖、或者判断“没有阻塞”，均不自动获得开发权限。检查完成后立即汇报并停止，等待总控经项目负责人手动转发的正式执行指令。

01 与 02 的 W1 隔离阶段交付已由项目负责人确认。01 的 59 项测试和 02 的 68 项测试不代表网站上线；02 的 68 项中有 43 项公共核心、25 项质量领域测试。01/02 均待命，不再修改；不得要求其继续开发或写入其工作树。

本阶段流程固定：总控准备 → 项目负责人手动发给原 03 → 接令检查并停止 → 总控另发正式执行指令 → 03 单独实施并停止 → 总控技术复核 → 项目负责人最终验收 → 才能考虑 04。禁止自动进入下一阶段、启动 04–05、创建替代任务或派生子 Agent。

不研究或运行 App Server、外部控制器、跨任务自动接管、解锁、终止桌面进程。不运行 `.local/assistant-team/control.mjs`。旧协作文档中相关控制方式、模型回读和历史阶段状态均不作为当前执行依据。

模型要求仍为 `gpt-6-sol / high`，由项目负责人在原 03 桌面确认。任务内部无法可靠回读模型或其他任务状态时如实说明，不以旧 manifest 冒充实时状态，不探测外部入口。此次指令及本任务单优先于旧 W0 的待定建议。

## 1. 本轮接令检查：只读、零开发

完整阅读本工作树下列文件：

1. `docs/assistant-team/COORDINATION.md`；
2. 本任务单；
3. `docs/assistant-team/W1-CORE.md`；
4. `docs/assistant-team/W0-REVIEW.md`；
5. `docs/assistant-team/reports/03-research-W0.md`；
6. `docs/assistant-team/reports/03-research-W1-CORE-REVIEW.md`；
7. `docs/assistant-team/references/01-ingestion-W1.md`；
8. `docs/assistant-team/references/02-quality-W1.md`；
9. `docs/assistant-team/references/03-research-W1-DEPENDENCIES.json`；
10. `docs/智能助手模块审计与重规划-2026-09-22.md`。

历史公共核心审查报告不是当前待修任务；其三项发现已由总控修正，现阶段以同步的核心实现和 W1-CORE 为准。不得重新修改核心或将旧报告误当领域 W1 已完成。

核对 cwd、分支、Git 变更、18 项依赖存在性与规范化 SHA-256。规范化规则：UTF-8、CRLF 转 LF、保留结尾换行。依赖副本是总控本轮同步的只读文件，不是 03 已开发成果；既有模型要求更新和 W0/核心审查报告不得恢复或覆盖。

本轮不得修改文件、创建报告、安装依赖、编译、运行 Maven/测试、启动应用、连接数据库或项目真实模型。只返回：

- 实际目录/分支、必读文档读取情况、依赖校验结果；
- 对本阶段职责、七项工具、A/B 比较和质量交接边界的理解；
- 允许修改范围、禁止事项、隔离测试计划；
- 缺失文件、接口/归属语义阻塞和最小澄清需求；
- 计划报告路径，以及“本轮未开发、未修改，等待正式执行指令”。

## 2. 正式执行后才授权的 W1 目标

在公共核心上实现严格范围的图谱研究只读适配、稳定来源重读、有向邻域/路径、未执行扩展草稿，以及 A/B 独立资料成果与比较的隔离验证。

只写新领域代码，不兼容改造旧 Chat，不修改旧 Retrieval/Graph/Search/Node/Edge/Document 服务。不得用原来“同库优先排序”替代硬过滤，也不能在全局截断后再过滤而声称召回完整。

真实 MySQL、真实模型、API、前端、鉴权、统一查询超时/事务一致性、可信宿主生产成果适配、生产注册、持久化及端到端网站验收均留给后续集成。本阶段不实现生产 DecisionProvider，不发模型请求。

### 2.1 接入与七项工具

采用 `ResearchAgentToolProvider.tools(): List<AssistantTool>`，角色限定 `Role.GRAPH_RESEARCH`。可以在 assistant/research 内新增只读仓储、JDBC 适配、路径/比较/草稿校验类，不建立另一套公共 Runner、Scope 或 ToolProvider。

| 工具 | 本阶段职责与上限 |
|---|---|
| `research.search_nodes` | 按 query 在宿主范围内搜索节点；单页最多 20，返回真实身份、状态、匹配依据及覆盖信息。 |
| `research.retrieve_chunks` | 按 query 在宿主范围内检索正式可用原文；单次最多 8 段、正文总量建议不超过 8000 字；返回真实 documentId/chunkId/hash、来源定位、候选窗口与截断。 |
| `research.read_source` | 以 documentId/chunkId/expectedContentHash 精确重读来源；单段最多 4000 字，身份或 hash 不符不能替换旧引用。 |
| `research.get_node` | 读取范围内节点及有界别名、归属、来源信息；版本只能表达真实 updatedAt/观察状态，不冒充强版本。 |
| `research.get_neighbors` | 范围内 1/2 跳有界邻域，保留真实入边/出边方向；建议最多 50 节点、100 边，截断可见。 |
| `research.find_paths` | 范围内 sourceNodeId 到 targetNodeId 的有向路径，maxDepth 为 1/2、maxPaths 最多 10；每步保留真实边及端点。 |
| `research.draft_expansion` | 只校验、整理有界的节点/关系建议数据，不负责隐藏模型生成；最多各 10 项，输出未执行草稿和真实核查限制。 |

前六项声明 READ_ONLY，draft_expansion 声明 DRAFT_ONLY。工具不得接受任意 SQL/路径/URL，不调用现有 AI expand/persist、图谱写服务、资料处理/复审、路径保存或评分接口。

所有 schema 必须与实际参数校验一致：真实字段、必填、枚举、正整数 ID、字符串/列表/深度/页长边界、额外字段拒绝。工具不接受另一个授权 scope；范围永远取宿主上下文，不能由模型覆盖。

公共 ToolStatus 仍为 OK/PARTIAL/ERROR；缺失、陈旧、不支持范围等使用 errorCode/领域字段，不另造调度状态。

### 2.2 范围与来源可用性

- PROJECT 与 SELECTED 的库/资料/节点集合均为授权条件，多个非空维度取交集。种子 nodeId 是查询参数，不是扩大授权的理由。
- 图谱工具的 nodeIds 是允许返回的节点集合，不是可无限扩展的探索起点。边及路径中间节点、两端节点均需可见；单节点授权不能自动带出其他邻居。
- 库归属使用真实 library_nodes；资料与节点的关联使用真实 node_evidence/chunk/unit/document 链，不能凭名称或“同库”推断。资料范围内返回边还需查证 edge_evidence 的真实来源关联；缺口如实标记。
- 文档归属包含 documents.library_id 与 document_topic_assignments，组合过滤和多库去重在 LIMIT/聚合前完成。
- 当前冻结 SourceLocator 拒绝非空 nodeIds。本 W1 的 retrieve_chunks/read_source 及带原文引用的草稿检查，遇到此类范围应明确拒绝 NODE_SCOPE_UNSUPPORTED；不得去掉 nodeIds 后悄悄查询整份资料。生产宿主如何安排显式库/资料范围留待集成，不能在本任务自行扩大。
- 本 W1 原文策略固定 formal_only：文档 COMPLETED、生命周期 active（含旧记录无 metadata 的兼容语义）、单元 READY/OCR_OK。prepared_diagnostic 不在本领域本轮开放；01 已有对应只读观察基础。
- retrieve_chunks 与 read_source 必须执行相同的来源可用性规则；来源从可用变为归档/未完成时不能只凭 hash 相同继续当正式资料。
- 图谱 active 状态不等于人工已核验；只返回有依据的 origin/reviewStatus，缺失则 unknown/unavailable。AI 扩展产生的待核验图谱对象不能被包装成原文证实的事实。
- 不泄露范围外主库 ID、其他库名称、邻居/匹配对象或证据 ID；全局统计若不符合当前范围则省略或另标不可用。

### 2.3 检索、首次取证与覆盖

- query 使用绑定参数、有界词项和确定排序。允许有界候选窗口，但必须说明窗口/采样规则；不能把“按 ID 先取 N 条再评分”称为全库最相关 Top-N。
- 候选截断、正文截断、分页与实际覆盖分别标明。hasMore=false 不等于完整覆盖；没有检查的资料、来源必须保留缺口。
- 显式选择多份资料时使用每资料有界配额或等效公平策略，避免 A 消耗全部预算导致 B 消失。资料数超过可支持上限时拒绝或明确部分覆盖，不静默取前几份。
- 不按全局 contentHash 去重而抹掉另一份资料的归属；相同文字出现在 A/B，仍需保留各自真实 documentId/chunkId。
- retrieve_chunks 必须提供真实、经归属校验的 chunkId/hash，使调用方能直接重读，不得要求猜 hash 或通过 STALE 错误探知版本。
- 复用 SourceLocator.chunk() 读取正文，不复制另一套按 chunkId 重读正文实现。检索 SQL 可用于文本匹配/排序和元数据；来源展示与精确重读必须复用冻结接口。
- 单位 type/index/locator、资料状态等不足字段可在 research 仓储做有界、范围内元数据补查并核对 unitId/documentId；不得改 source 接口、猜 ID 或要求 01 继续开发。
- 同一真实来源多次检索/重读时，EvidenceRef 的稳定 ID、对象版本、范围、定位和固定长度摘录需一致，避免公共 Runner 的 EVIDENCE_ID_CONFLICT；额外正文放领域 payload，不能让 maxChars 改变同 ID 的证据身份。
- 无来源、仅匹配到节点、hash 陈旧、正文截断不能变成“已完成全文研究”。检索命中和引用存在不能替代语义支持判断。
- “它/两者/继续比较”等指代不明确时，依赖宿主明确提供的 resolvedReferents；没有依据则返回需要澄清的领域结果，由决策层给 needs_input，不猜测跨库对象或读其他任务聊天。

### 2.4 邻域与路径

- 节点、边、COUNT/覆盖统计使用同一过滤口径；真实 inactive/待核验状态不可丢失或改写。
- 邻域可同时包含入边与出边，但输出始终保留真实 sourceNodeId、targetNodeId、edgeId、relationType，不一律写成“当前节点 → 邻居”。
- find_paths 本轮按真实 source → target 方向查找，不默认无向化。最多两跳，每步连续，限制分支、节点/边、路径数量；循环与重复边有确定处理。
- 仅在规定深度内穷尽后才能表述“此范围、此深度无路径”；若窗口截断则只能说尚未发现，不能推断全图无关联。
- properties_json 只提取所需白名单字段，不能整块透传。缺少逐条来源、真实版本或审核信息时标限制，不编造证据或人工确认。

### 2.5 A/B 成果、比较与公共运行核心

- 提供有界的独立资料成果及比较数据结构，嵌入现有 Artifact.dataJson，不增加全局持久化或私有调度协议。
- 每份成果保留 documentId、研究目标、claims/findings、真实 sourceIds、覆盖/缺失/截断和语义判断性质。source_fact、graph_fact、model_explanation、conflict/gap 分开。
- 宿主构造 A/B 两个 GRAPH_RESEARCH 任务，每个授权一份资料；比较任务显式依赖二者且只获准 A+B。两个任务的上下文、工具观察、证据不得串用。
- 比较逻辑从当前任务的直接依赖 Artifact 读取，不接收模型重建的摘要冒充原始成果；验证 expectedDocumentIds、producerTaskId、来源范围及已知引用，检测同一资料冒充 A/B。
- 至少重读关键比较来源；不能只引用上游摘要。缺一方、证据不足或依赖 partial，不得产出完整比较或选出“更正确”的教材。
- **遵守核心已有失败语义**：A 失败/needs_input 时比较任务会 BLOCKED，独立 B 的成果保留；不能要求核心凭空产生 partial 比较成果。A 为 PARTIAL 且比较允许 partial 依赖时可运行，但结果仍传播 PARTIAL 和缺口。
- 本轮仅提供领域成果构建/验证与 fake 决策的隔离场景；不实现生产自然语言规划器或真实模型比较质量验收。
- 测试内可以验证产品运行核心的 A/B 并发与隔离，不等于启动多个桌面任务或子 Agent。真实开发仍仅原 03 一个任务执行。
- 不做 research artifact/聊天原文持久化，不声称服务重启后可恢复。学习交接不包含伪造分数、掌握度或阶段完成凭证。

### 2.6 与已验收 02 的交接

只读依赖包含 `QualityArtifactReader` 的接口副本，不包含其生产宿主适配器，也不授权修改 02。

- 研究成果必须有稳定 claimId 与已知 sourceId 的对应关系，足以在宿主侧映射为 Claim/SourcePointer；在本报告给出明确 JSON 形状、映射规则与样例。
- 建议结构为 claims[{claimId,text,kind,sourceIds}]；documentId/chunkId/expectedHash 必须来自可信工具观测及 Artifact.evidence，并交叉核对，不能只信模型 dataJson 自报的映射。
- 质量域目前只核对来源身份、归属、hash，semanticSupport=not_evaluated；研究侧不得把它升级成“论断已证实”或人工核验。
- 质量单次最多 10 个 claim、每个最多 2 条来源、总计最多 20 条；超限时研究成果需保留全部未复核项并说明需分批，不能删除额外证据伪装完整。
- 本轮用假宿主的原始 Artifact/真实类型构造交接兼容测试，验证合法映射、伪造 sourceId、跨资料/非直接依赖、超限和缺口。生产 run/task 隔离解析器及真正研究→质量→学习的端到端集成由总控后续负责。
- 若现有输入上下文不足以安全获得可信观测，应报告最小接口需求；不得改公共 Context 或给任意全局 artifact ID 读取权限。

### 2.7 未执行扩展草稿与预算

- draft_expansion 只验证和整理模型提出的有界节点/有向关系建议，不调用模型，更不调用 AiExpandStore.persist。没有证据的内容只能标为 model_explanation / recommendation。
- 目标库、已有节点/边引用必须重新查证真实存在及宿主范围；新节点只用局部草稿标识，不能编造正式数据库 ID。边端点必须解析到已校验的现有节点或本草稿内节点。
- 输出固定 recommendationOnly=true、executionAllowed=false、saved=false，不生成执行回执，不代签人工核验。
- 本轮草稿放领域 dataJson，不开放公共 ActionProposal；保留公共默认拒收，避免扩大到执行授权/确认 UI/事务。
- 限制 query、词项、页长、深度、节点/边/路径、claims、来源和字符预算，兼顾 ToolResult.dataJson 32000、EvidenceRef.excerpt 2048、ToolRegistry 整体上限及 Artifact.dataJson 16000。
- 输出过长时有明确截断/覆盖限制或稳定错误，不能静默丢数据后声称完成；分页/请求覆盖与全文/资料全集覆盖分别表达。
- 不输出密钥、路径、未脱敏异常/底层模型错误；底层查询超时与强一致策略尚未接入，不声称核心能强制终止 JDBC 或完成生产鉴权。

## 3. 修改所有权与只读依赖

总控受控同步 18 项依赖，明细/规范化 SHA-256 见 `references/03-research-W1-DEPENDENCIES.json`：

- 公共核心 9 个 Java 源文件、2 个核心测试；
- 01 SourceLocator、JdbcSourceLocator；
- 02 QualityArtifactReader 接口；
- W1-CORE、W0-REVIEW、01 W1 报告和 02 W1 报告参考副本。

这些是只读依赖，不是 Git 合并、生产注册或新业务实现；源文件所有权保持不变。03 既有 W0 与核心审查报告保持原样。

收到正式实施指令后，允许新增/修改的范围仅为：

- `backend-java/src/main/java/com/knowledgegraph/assistant/research/**`；
- `backend-java/src/test/java/com/knowledgegraph/assistant/research/**`；
- `docs/assistant-team/reports/03-research-W1.md`。

禁止修改公共 contract/runtime/tool、assistant/source、assistant/quality、公共测试、现有 Chat/Retrieval/Graph/Search/Node/Edge/Document 服务及 Controller/DTO、前端、pom、配置/密钥、数据库迁移、协调文档、任务单、依赖清单、主工作树或其他工作树。不得提交、合并、push、部署或派生 Agent。

W0 中“可修改旧 RetrievalService/DocumentService”等只是当时建议，本次未授权；旧来源/状态/模型等 P0 待定项以本任务单和现有冻结依赖为准。确有扩权需要先报告，禁止自行绕过。

## 4. 正式实施后的隔离验证与阶段交付

测试使用 fake 仓储/来源/宿主成果/DecisionProvider 与 mock JdbcClient，不启 Spring 上下文，不连接真实业务数据库，不调用真实模型，不安装新依赖。不运行全量 test 或任何 IntegrationTest。

至少覆盖：

- 空项目、同名异库、库/资料/节点维度组合、范围外端点/中间节点/证据拒绝；
- formal_only 与归档/未完成/非准备单元排除，读取前状态变化和 hash 陈旧；
- 正常检索→使用返回的真实 hash 重读，无需预先硬编码调用参数；真实 chunkId 不用 index 冒充；
- 末页非全部覆盖、候选窗口/正文/Top-N/路径截断、A/B 配额和相同正文不同资料；
- 入边/出边方向、连续两跳、反向不可达、自环/循环/重复路径及有界查询；
- 同一来源多次工具调用的证据稳定性及与 Runner 的引用冲突检查；
- A/B 独立任务与比较依赖，A 失败/B 保留/比较 BLOCKED、A partial 的传播、缺资料不完整；
- 歧义指代不扩大范围，未知/伪造引用不进入可信交接，质量引用有效不等于语义支持；
- 草稿未知字段/越界目标/伪造正式 ID/错误端点拒绝，无隐藏模型或业务写调用；
- SQL 参数化、LIMIT 前过滤、COUNT/列表口径、实际 RowMapper/元数据映射、脱敏及整体结果预算。

建议明确测试类：ResearchAgentToolProviderTest、JdbcResearchReadRepositoryTest、ResearchComparisonTest、ResearchDraftValidatorTest。正式实施后可使用：

```powershell
# 仅正式执行阶段，在本工作树 backend-java 内执行；接令检查禁止运行。
.\mvnw.cmd '-Dtest=AssistantRuntimeTest,DecisionParserTest,ResearchAgentToolProviderTest,JdbcResearchReadRepositoryTest,ResearchComparisonTest,ResearchDraftValidatorTest' test
```

类名如调整，使用实际存在的明确列表并在报告记录，不用通配符、不跳过“测试不存在”错误。数据库 mock 不等于 MySQL 兼容，fake 比较结果不等于真实模型语义质量。

阶段报告应列出实际文件、七项工具、支持/拒绝的范围组合、来源/图谱状态口径、检索排序与覆盖、比较和质量交接契约、命令及测试分类数量、未做项/已知限制/最小扩权需求。网站还不能实际验收的能力单独列出。

报告完成后汇报并停止，等待总控复核及项目负责人最终验收。不得自动进入 04、05、生产集成或 W2。
