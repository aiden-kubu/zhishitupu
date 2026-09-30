# 02 质量治理｜W1 隔离实现报告

日期：2026-09-24。工作树：`D:/知识图谱-worktrees/assistant-02-quality`；分支：`codex/assistant-02-quality`。
本阶段只在 `assistant/quality` 新增源码与隔离测试，并新增本报告。公共核心、01 原文定位、既有 Review/Insight 业务代码及其他工作树均未修改。

## 交付文件与职责

| 新增文件 | 职责 |
|---|---|
| `backend-java/src/main/java/com/knowledgegraph/assistant/quality/QualityReadRepository.java` | 只读领域记录、分页与查询边界 |
| `backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java` | 参数化 SQL、真实归属与范围前置、候选/洞察/匹配对象读取；按文档和宿主范围读取 chunk hash 元数据 |
| `backend-java/src/main/java/com/knowledgegraph/assistant/quality/QualityEvidenceVerifier.java` | 真实 chunk ID、任务文档归属、单位元数据及 hash 复核 |
| `backend-java/src/main/java/com/knowledgegraph/assistant/quality/QualityArtifactReader.java` | 宿主只读提供当前任务直接依赖成果的接口；不建全局注册中心 |
| `backend-java/src/main/java/com/knowledgegraph/assistant/quality/QualityAgentToolProvider.java` | 四项 READ_ONLY 工具、字段级 schema、参数/结果边界与公共 ToolResult 适配 |
| `backend-java/src/test/java/com/knowledgegraph/assistant/quality/QualityAgentToolProviderTest.java` | fake 仓储、宿主成果、Runner 接入与参数/状态测试 |
| `backend-java/src/test/java/com/knowledgegraph/assistant/quality/QualityEvidenceVerifierTest.java` | fake SourceLocator 的归属、ID、版本和截断测试 |
| `backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepositoryTest.java` | mock JdbcClient 的 SQL 形态与参数绑定测试 |

没有新增 Controller、Spring 生产注册、ActionProposal 或数据库迁移。`QualityAgentToolProvider.tools()` 返回四项仅允许 `Role.QUALITY_GOVERNANCE` 使用的工具。

## 四项工具与成果契约

1. `quality.get_review_snapshot(jobId,candidateKind,page,pageSize)`：先按宿主 Scope 查询 job/document，再分页读取指定实体或关系候选与行内 AI 审计；返回 job 状态、候选列表、`total/hasMore/completeCoverage/truncated`、观察时间及影响说明。页长 1–20；`evidenceRefs[{chunkId,expectedHash}]` 从受文档归属及宿主 Scope 约束的只读 hash 元数据查询取得，最多 20 条，不包含正文；只有校验通过的 ID/hash 回传，越界/缺失/截断标为不完整。调用方可直接用该信息调用来源工具，不必猜 hash 或通过 STALE 回包探知。hash 是快照时观察值，不是事务锁；来源工具仍要重读。关系给出两端 tempKey/名称/方向/type；匹配边仅在当前范围内存在且两端映射、方向、type 相符时标为可见匹配。存在但不符或缺映射时标未核实/冲突，不把 matched ID 当实际入库落点。
2. `quality.read_candidate_sources(jobId,candidateKind,candidateId,expectedRefs,maxChars)`：先证明 job→document 和 candidate→job；最多 10 个期望 chunk/hash，单段 200–1200 字。每项重读前核对候选证据列表和真实 chunk 文档归属；复用 `SourceLocator.chunk()` 读当前 hash/正文，并通过有范围的单位元数据查询补 unitIndex/locator。缺失、错文档、越界、hash 陈旧、单位不可用、正文截断逐项返回；全部无法核验时 `ERROR` 且无可引用证据，截断时 `PARTIAL`。返回 `requestedCount/checkedCount/currentCount`、`candidateEvidenceTotal/candidateEvidenceChecked/candidateEvidenceUnchecked`；`requestCompleteCoverage` 只表示本次请求均核验通过，`candidateEvidenceCompleteCoverage` 与兼容字段 `completeCoverage` 仅在候选全部证据均请求且通过时为 true。只读部分来源即使全部 CURRENT，结果也为 `PARTIAL` 并带 `CANDIDATE_EVIDENCE_UNCHECKED`。
3. `quality.get_insight_snapshot(isolatedLimit,topLimit)`：只用宿主 Scope，支持 PROJECT 与仅选库的 SELECTED；文档/节点洞察范围明确拒绝。返回节点、active 有向边、资料、待复审候选、类型分布、度数排行和孤立节点；各列表有 `total/limit/sort/truncated/completeCoverage`，另有口径、观察时间及 `trendAvailable=false`。类型最多 50、度数最多 10、孤立最多 100。多库通过 `EXISTS` 查可见节点/资料，避免关联重复计数；边的两端都必须可见。
4. `quality.verify_artifact_claims(artifactId,claimIds)`：最多 10 个 claim，每个最多 2 条来源、合计最多 20 条。`QualityArtifactReader` 只允许宿主提供当前 run/task 的显式直接依赖原始成果及其来源映射；工具逐项重读引用身份、归属和期望 hash。未知/伪造/越界/陈旧引用保留逐项原因。复读产生独立稳定的 `quality:verified:*` 来源 ID，并保留原 sourceId 供追溯，避免与上游 Artifact 的同名引用发生公共 Runner 冲突。返回的 `semanticSupport` 恒为 `not_evaluated`；引用有效不等于论断已获语义支持。

四项 schema 均含真实必填字段、类型、枚举、数量或长度边界和 `additionalProperties=false`；执行时再次校验。Scope 从 `AssistantTool.Context` 获取，不接受模型另传。结果使用公共 `ToolStatus.OK/PARTIAL/ERROR`、`dataJson/evidence/limitations/errorCode`；缺失和 stale 是领域字段，不新建调度状态。单项结果 JSON 超过 30000 字符时返回稳定 `QUALITY_RESULT_LIMIT`，留出公共 32000 字符与 ToolRegistry 总量限制的空间。无内部模型调用、无业务写调用；建议数据明确 `recommendationOnly=true/executionAllowed=false`。

## 四层状态、范围与统计口径

- `reviewStatus` 读取候选数据库状态；`aiDecision` 仅来自候选审计 JSON；`humanDecision` 在没有明确人工决定凭证时为 `unavailable`；`importState` 只报告 job 是否读到 `COMPLETED`，逐候选结果恒 `unknown/outcomeIncomplete=true`。不会从 `EDITED/ACCEPTED` 推断人工确认或正式节点创建。
- AI 复审可能自动入库，但本工具从不调用 retry/ai-review/commit/update/bulk-action。`autoImportPossible` 只在任务处于可继续状态且已见实体候选时为 true；模型可用性及执行结果仍待领域服务决定。当前人工写路由不可用，因此不宣称纠偏建议已经能够执行。
- 复审和来源读取对 SELECTED 库/资料取交集，库归属含文档主库或 topic assignment；nodeIds 明确拒绝。matched node/edge 额外核对当前选定库/资料可见性，范围外 ID/名称不回传。SQL 的过滤发生在 `LIMIT` 和聚合前。
- 洞察 `nodeCount` 包含可见节点的所有状态；`edgeCount` 只含双端点可见的 active 有向边；“孤立”指在当前可见节点集合里没有 active 边相连。资料计数含所有状态，待复审仅计有现存资料且 job 状态为 `AWAITING_REVIEW/AI_REVIEWING/FAILED/CANCELLED` 的 PENDING 候选。多条查询不构成强一致事务快照；观察时间不充当版本锁。
- 分页 `hasMore=false` 只说明没有下一页；只有第一页覆盖总量才有 `completeCoverage=true`。来源请求覆盖与候选证据全集覆盖分开；超出单批 10 条的候选证据无法在一次来源调用中取得完整覆盖，必须继续逐批审查且不得将单批结果说成全量。来源正文截断、候选证据缺失/越界及 Top-N 截断都会降低结果完整性。没有历史快照就不生成趋势。

## 隔离验证

实际在本工作树 `backend-java` 执行：

```powershell
.\mvnw.cmd '-Dtest=AssistantRuntimeTest,DecisionParserTest,QualityAgentToolProviderTest,QualityEvidenceVerifierTest,JdbcQualityReadRepositoryTest' test
```

补修后结果：**68 项通过、0 失败、0 错误、0 跳过，BUILD SUCCESS**；质量域 `QualityAgentToolProviderTest` 15、`QualityEvidenceVerifierTest` 6、`JdbcQualityReadRepositoryTest` 4，共 25 项；公共核心 `AssistantRuntimeTest` 28、`DecisionParserTest` 15，共 43 项。测试使用 fake 仓储/来源/宿主成果与 mock JDBC，无 Spring 上下文、真实数据库或项目真实模型调用。

覆盖空项目、四层状态独立、已完成任务逐候选落点未知、关系方向与匹配边冲突、任务文档归属与越界证据隐藏、hash 陈旧/缺失/错 ID/截断、SELECTED 范围、COUNT/列表 SQL 过滤位置、Top-N/孤立节点截断、末页非全量、未知与非直接依赖成果、伪造来源、引用有效仍不判语义、参数 schema、结果上限、Runner 中 partial 不升级成功且无执行回执。补修新增快照→按实际返回 hash 读取来源（调用方不预置 hash）、两条证据只查一条仍为部分覆盖、两条全查才完整、hash SQL 文档与选定范围约束四项隔离测试。

01 的四项复核经验已对应处理：工具 schema 与实际校验一致；末页与完整覆盖分开；不回传范围外证据 ID 或主库 ID；本阶段不开放 ActionProposal，建议固定为未执行，故不存在用反向影响文案通过执行预览的路径。

## 未验证事项与总控后续需求

- mock JDBC 仅验证 SQL 形态与参数绑定，**不证明 MySQL 兼容、查询性能、索引效果或并发一致性**；生产层需配置查询超时及必要的一致性策略。
- fake 决策/宿主成果不证明真实模型会正确选择工具或判断语义；原文核对只证明引用的身份、归属和 hash。网站/API/前端、生产鉴权、真实模型、真实 MySQL、业务写入及最终用户验收均未开展。
- 总控生产接入前须实现 `QualityArtifactReader.directArtifact(runId,consumerTaskId,artifactId)` 的可信宿主适配：从宿主持有的原始 Artifact、claim 与 evidence 中解析，核对显式直接依赖和 scope；不得接受模型摘要或任意全局 ID。当前只有接口与 fake 测试，没有生产解析器或注册。
- `SourceLocator.chunk()` 不返回 unitIndex/locator，本阶段以质量域只读仓储按 documentId/unitId 补查；一次多表/多语句读取可能遇到状态变化，缺数据库版本/事务快照时必须保留这一限制。单段最多 1200 字的本工具输出可能不足以审查长论断，截断会标记 PARTIAL。
- 当前 Review 入库汇总缺逐候选回执；W1 无法确定每项最终创建、合并或跳过何处，也不修复既有 `matchedEdgeId` 入库逻辑。人工决定的审计/覆盖规则及执行器仍待后续明确授权。
- 未修改其他任务或主工作树；未提交、合并、push、部署、启动应用、运行全量测试或 IntegrationTest。当前会话无法独立回读桌面模型/推理强度和其他任务状态，需项目负责人以桌面设置确认。

建议总控先复核工具输出、宿主成果边界和隔离测试，再决定生产适配与真实数据库兼容验收；本子任务停在 W1，等待复核及项目负责人最终验收。
