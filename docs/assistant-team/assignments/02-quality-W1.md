# 02 质量治理｜W1 阶段任务单

日期：2026-09-24。原任务 ID：`01a0c6f9-fc89-7ed1-b9c0-29d35cbac0b5`。
工作目录：`D:/知识图谱-worktrees/assistant-02-quality`。
分支：`codex/assistant-02-quality`。

## 0. 当前状态与授权门槛

**当前仅授权接令检查，未授权实施。** 阅读本任务单、依赖或得到“检查无阻塞”结论，都不自动启动开发。完成检查后立即汇报并停止，等待总控通过项目负责人手动转发的正式执行指令。

01「资料建设」W1 已由项目负责人确认阶段交付，并保持待命；不得要求 01 继续开发或修改其工作树。其隔离实现与 59 项测试不是网站上线、真实数据库或真实模型验收。

控制方式已确定为：总控准备 → 项目负责人手动发送原 02 → 02 单独执行指定阶段并停止 → 总控复核 → 项目负责人最终验收 → 才能考虑 03。禁止自动切阶段、启动 03–05、创建替代任务或子 Agent。禁止研究、启动或续接任何 App Server、外部控制器、跨任务自动接管、解锁或终止桌面进程。

模型要求仍为 `gpt-6-sol / high`，由项目负责人在原 02 桌面设置中确认。不能从当前任务可靠回读设置或其他任务状态时，如实说明，不把旧 manifest 当实时证据，也不因此探测外部控制入口。

旧 COORDINATION、W0-REVIEW、W1-CORE 中关于 App Server、控制脚本、写入占用、模型回读及阶段状态的记载属于历史记录；本任务单和本轮人工指令优先。不得运行 `.local/assistant-team/control.mjs`。

## 1. 本轮接令检查：只读，不开发

完整阅读：

1. 本工作树 `docs/assistant-team/COORDINATION.md`；
2. `docs/assistant-team/W1-CORE.md`；
3. `docs/assistant-team/W0-REVIEW.md`，尤其公共边界与 02 审阅意见；
4. `docs/assistant-team/reports/02-quality-W0.md`；
5. 本任务单；
6. `docs/assistant-team/references/01-ingestion-W1.md`；
7. `docs/assistant-team/references/02-quality-W1-DEPENDENCIES.json`；
8. 本工作树既有 `docs/智能助手模块审计与重规划-2026-09-22.md`。

核对实际 cwd、分支、Git 变更、依赖存在性及清单中规范化 SHA-256（UTF-8、CRLF 转 LF，不去掉结尾换行）。同步文件属于总控准备的只读依赖，不误判成自己已实现或越权新增；未知差异只汇报，不恢复、不覆盖。

检查阶段不得修改任何文件、创建报告、安装依赖、编译、运行 Maven/测试、启动应用、连接数据库或模型。返回：

- 实际 cwd / 分支；
- 必读文件是否完整读取、依赖是否齐全且校验一致；
- 对 W1 职责、修改范围、禁止事项和四项工具的理解；
- 隔离测试计划、依赖或契约阻塞，以及计划报告路径；
- 本轮未开发、未改文件，并等待正式执行指令。

## 2. 正式执行后才授权的目标

W1 最小切片：只读复审快照、候选来源重读、明确口径的洞察快照、交接论断的确定性引用核查。使用现有公共运行核心和 01 原文定位依赖，运行 fake/mock 隔离单元测试。

本阶段不接真实 MySQL、真实模型、API、前端、生产鉴权、生产注册或统一查询超时/事务一致性；这些留给总控集成阶段。范围过滤必须实现，但不宣称它等于已经完成生产鉴权。不得修改现有 Review/Insight 业务代码来修复历史问题；可以读其源码核对真实语义，在新增适配器中实施安全的只读查询。

### 2.1 固定接口与工具

- 角色 `Role.QUALITY_GOVERNANCE`；通过 `QualityAgentToolProvider.tools(): List<AssistantTool>` 接入，不另建公共 Runner 或 ToolProvider 协议。
- 四项工具固定命名：
  - `quality.get_review_snapshot`：给定 jobId，分页读取实体/关系候选和对应 AI 审计，分开表示任务、文档、候选和实际结果。
  - `quality.read_candidate_sources`：给定 jobId、candidateKind、candidateId 和有界的期望来源信息，先查真实候选归属，再重新读取当前来源，逐项列明缺失、越界、陈旧与截断。
  - `quality.get_insight_snapshot`：使用宿主 scope，不允许模型传另一个 scope；返回当前统计口径、观察时间、有界列表及覆盖信息。
  - `quality.verify_artifact_claims`：对宿主提供的当前任务直接依赖成果，按 claim 重新读取来源并核对引用身份、归属和期望 hash；不是自动裁定语义真伪的工具。
- 全部 READ_ONLY，禁止内部模型调用。每项给出真实字段级参数 schema、必填、类型、ID 范围、枚举、数量上限、额外字段拒绝，并与实际校验对应。
- 推荐新增 `QualityReadRepository`、`JdbcQualityReadRepository`、`QualityEvidenceVerifier` 等，仅放入自己的 assistant/quality 子包。不得依赖尚未交付的 03 新接口。
- 成果使用公共 Artifact 的 dataJson/evidence/limitations，不另造调度枚举。missing/stale/unverified 是领域核查状态；公共 ToolStatus 仍是 OK/PARTIAL/ERROR。
- W1 纠正建议放入明确未执行的领域数据，`recommendationOnly=true`、`executionAllowed=false`。本轮不开放质量 ActionProposal，保留公共默认拒绝；不得为此改公共 ProposalValidator 或注册可执行操作。

### 2.2 四层状态与实际效果

必须分开 `reviewStatus / aiDecision / humanDecision / importState`：
- AI 审计只是模型判断，不代表人工校对；无法证明存在明确人工行为时返回 unavailable/unknown，不能从 EDITED 或 ACCEPTED 推断人工确认。
- 任务 COMPLETED 只能报告已读取到的流程状态；不能证明每个候选都已创建或合并到正式对象。
- 缺逐候选落点与执行回执时标注 unknown / outcomeIncomplete；`matchedNodeId / matchedEdgeId` 是候选匹配信息，不等于入库结果。
- 匹配对象需要先验证范围和真实存在性；关系核查需比对 source、target、type，不只判断 edge ID 存在。端点或候选映射不足时明确未核实，不强行裁定。
- 当前人工纠偏写路由不可用，不把“打开旧页面”描述为已经能完成修正。不调用 retry、ai-review、commit、update、bulkAction 或任何图谱写入。

### 2.3 范围与洞察口径

- jobId → documentId、candidateId → jobId 必须由只读仓储查证。禁止相信模型传来的对象归属。
- 复审/候选/来源工具支持 PROJECT 与 SELECTED 库/文档交集；库归属含文档主库及 topic assignments。不支持的 nodeIds 必须明确拒绝，不忽略维度。
- WHERE 中范围过滤在 LIMIT/聚合之前完成；COUNT 与列表同一口径，多库匹配避免重复计数。
- 本 W1 洞察至少支持 PROJECT 和仅 libraryIds 的 SELECTED：节点通过 library_nodes 限定；统计有向边要求两端均落在可见节点集合，明确 active 边及节点状态口径。多库计数去重，不暴露范围外对象 ID/名称/邻居。
- documentIds 或 nodeIds 洞察如尚无可靠归属映射，整体明确返回 UNSUPPORTED_SCOPE，不返回全局统计或忽略该维度。不得以“先全局 LIMIT 再过滤”声称范围完整。扩展支持留待后续明确授权。
- pending 候选统计限定有真实关联资料且处于可恢复任务状态，口径对齐既有业务并解释。孤立节点明确是“所选范围内无 active 边”还是全局定义，不能混用。
- `hasMore` 与 `completeCoverage` 分离；最后一页不等于完整覆盖。Top-N、孤立节点、候选列表分别给 total/limit/排序/截断及限制。无历史快照则 trendAvailable=false。
- SQL 多次读取不是强一致快照；时间戳/观察 hash 不冒充事务锁或递增版本。

### 2.4 原文与成果交接边界

- 复用只读依赖 `SourceLocator` / `JdbcSourceLocator`，不复制另一套 chunk 正文读取实现，不修改 assistant/source。
- 当前 SourceLocator.chunk 返回 chunkId/unitId/index/hash/offset/content，但不单独返回 unitIndex/locator；需要时在质量仓储中读取有范围约束的单位元数据并与来源记录交叉核对，或明确字段不可用。不得猜测 index 或要求 01 扩权。
- SourceLocator 本身不接收 expected hash；由质量验证层比较真实返回 hash。先验证归属，再比较期望版本；hash 变化明确 stale，不用新文本替代原引用作已验证证据。
- 单段当前最大读取长度为 4000 字，可能截断。不得把“返回一段文本”称为读完全文；需要超出接口能力的上下文时返回 partial/insufficient，留待总控决定后续扩展。
- 交接工具只接收有界的 artifact/claim 标识；原始 Artifact/claims/evidence 由宿主只读解析边界提供并按 runId/taskId 隔离，仅限当前任务显式直接依赖。可在 assistant/quality 中定义领域解析接口，测试使用 fake；不得从全局任意 ID、模型给出的摘要或手工拼装的引用认定为可信宿主输入，不新增全局持久化注册中心。
- 引用存在、hash 相同、原文含关键词，都不等于论断受到支持。W1 确定性工具输出引用核查状态，语义支持标为 not_evaluated/unverified；supported/contradicted 的语义判断留给有明确模型预算的后续决策层，本轮不调用真实模型、不把 fake 输出当真实语义验收。
- 未知 artifact/claim、非直接依赖、scope 越界、伪造证据 ID、缺失/变化来源必须有明确结果；逐 claim 保留未完成项，不能静默丢弃再汇总为成功。
- 明确并测试本领域交接数据结构；若现有公共上下文无法安全满足，报告接口需求与阻塞，不修改共享核心绕过。

### 2.5 返回体与失败处理

正整数 ID、页码/页长、批量 claims/refs、原文字符数均有上限；建议页长最多 20、单批 claims 最多 10，结合整体结果预算控制来源量。要同时遵守公共 dataJson 32000 字符、证据摘录 2048 字符和 ToolRegistry 整体结果上限；不能只限条数而让长文本撑爆结果。

范围外主库/匹配对象/证据引用不得通过辅助字段泄露。底层异常、storagePath、密钥、堆栈与未脱敏模型错误不返回给模型或用户。缺失、陈旧、字段不可用、截断如实表达，不编造成功数据。

## 3. 所有权与只读依赖

总控已准备只读副本，清单和规范化 SHA-256 见 `references/02-quality-W1-DEPENDENCIES.json`：

- 公共 contract/runtime/tool：9 个 Java 源文件；
- 公共核心测试：AssistantRuntimeTest、DecisionParserTest；
- 01 原文定位：SourceLocator、JdbcSourceLocator；
- W1-CORE、W0-REVIEW、01 W1 报告参考副本。

同步是本工作树内的受控依赖准备，不是合并、提交、生产注册，也不改变源文件所有权。所有这些文件均不可由 02 修改。

正式执行后，仅允许新增/修改：

- `backend-java/src/main/java/com/knowledgegraph/assistant/quality/**`；
- `backend-java/src/test/java/com/knowledgegraph/assistant/quality/**`；
- `docs/assistant-team/reports/02-quality-W1.md`。

禁止修改公共核心、assistant/source、01 实现、已有 ReviewService/AiReviewService/InsightService、任何 Controller/原有 DTO、前端、pom、配置/密钥、数据库迁移、协调文档/任务单/依赖清单、其他工作树或主工作树。不得提交、合并、push、部署、运行 App Server 或派生 Agent。W0 报告中拟改既有业务文件的建议被本次所有权规则取代。

## 4. 正式执行后的隔离验证与交付

测试使用 fake 仓储/来源/宿主成果，JDBC 使用 mock；不使用 @SpringBootTest，不连接真实数据库，不执行任何 IntegrationTest。不得安装新依赖或用 H2/mock 结论冒充 MySQL 兼容。

至少覆盖：

- 空项目、四层状态独立、任务完成但逐候选落点未知、AI 不能代签人工；
- job/candidate/文档真实关联、多库交集、跨任务来源和节点范围拒绝；
- 真实 chunk ID、hash 陈旧、缺失/错归属/正文截断及全部失败不能成为成功；
- 关系方向与 matchedEdge 不一致、统计去重、范围前置、COUNT/列表口径；
- 最后一页仍非完整覆盖、Top-N 与孤立节点截断、无历史不可生成趋势；
- 字段级工具 schema、参数边界、脱敏、长文本整体预算；
- 当前直接依赖成果可重读，伪造/其他运行/非直接依赖成果不可读取；
- 引用有效不等于语义支持；未重读、工具失败或 partial 不升级为已核实；
- 与现有 Runner 的 fake 决策/工具接入测试，结果无执行回执且无业务写调用。

建议测试类：QualityAgentToolProviderTest、QualityEvidenceVerifierTest、JdbcQualityReadRepositoryTest。正式执行时只能运行实际存在的明确测试名，例如：
```powershell
# 仅在收到正式执行指令后，在本工作树 backend-java 内运行；本轮接令检查不执行。
.\mvnw.cmd '-Dtest=AssistantRuntimeTest,DecisionParserTest,QualityAgentToolProviderTest,QualityEvidenceVerifierTest,JdbcQualityReadRepositoryTest' test
```
如测试类命名调整，在报告记录实际名单；不得用通配符或全量 test，也不得通过忽略“没有找到测试”伪造成功。

报告应列出新增文件、工具/交接契约、范围与统计口径、实际命令和分类数量、四项 01 经验对应回归、未测试事项、网站尚不可验收能力、依赖/扩权需求及后续建议。mock SQL 测试不证明 MySQL 兼容；fake 决策不证明真实模型判断正确。

阶段完成后汇报报告路径并停止。总控复核后由项目负责人最终验收；未经确认不得进入 03，也不得自动进行 W2 或生产集成。
