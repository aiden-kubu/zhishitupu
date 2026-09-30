# 04 学习教练｜W1 隔离实施报告

完成日期：2026-09-26。工作树 `D:/知识图谱-worktrees/assistant-04-learning`，分支 `codex/assistant-04-learning`。
本报告只记录本轮领域隔离交付；没有生产注册、上线、正式路径修订或正式测评能力。

## 1. 交付及文件所有权

- `backend-java/src/main/java/com/knowledgegraph/assistant/learning/LearningAgentToolProvider.java`：八项领域工具、封闭参数 schema、执行前复核、领域错误、输出预算。
- `LearningReadRepository.java` 与 `JdbcLearningReadRepository.java`：真实 local-default 路径/步骤 SELECT、有界图谱候选、精确 node/edge/source 链接和范围过滤。
- `LearningContextReader.java`：宿主只读直接依赖/原始质量观察接口；**未提供生产实现**。
- `LearningMaterials.java`：研究原始 Artifact、比较并列材料、质量原始观察交叉核对，构造可信来源指针。
- `LearningDraftValidator.java`、`LearningJson.java`、`LearningEvidence.java`：草稿形状/范围/基线/来源校验，差异整理与范围限定证据 ID。
- `backend-java/src/test/java/com/knowledgegraph/assistant/learning/` 下四类隔离测试：`LearningAgentToolProviderTest`、`JdbcLearningReadRepositoryTest`、`LearningArtifactHandoffTest`、`LearningDraftValidatorTest`。
- 仅新增上述学习域文件与本报告；原有 `COORDINATION.md` 修改、W0 报告及总控同步的未跟踪依赖均保留，未清理或覆盖。
- 未修改公共核心、source/quality/research、旧 `LearningPathService`/Controller、迁移、前端、配置或 pom；未提交、合并、push、部署。

## 2. 八项工具契约

全部只允许 `LEARNING_COACH`，前三项草稿以外均 `READ_ONLY`，草稿为 `DRAFT_ONLY`；无 WRITE、HTTP 或 Spring Bean。
所有 JSON schema 为封闭对象；同名工具在执行前再次校验，不接收模型提供的 scope/profile/run 身份。

| 工具 | 输入 | 结果及边界 |
|---|---|---|
| `learning.read_current_path` | `{}` | `none/available/unavailable`；可用时返回真实 pathId、stepId、stepKey、顺序、状态、visitedAt、旧 score 与快照指纹。`formalRevision=null`，`formatVersion=2` 只是格式；旧分数标 `unverified_legacy_record`。不可完整授权时不泄露局部路径。 |
| `learning.read_graph_scope` | `maxNodes:1–50?`、`maxEdges:1–100?` | 返回受 scope 过滤的节点和真实 source→target 边、状态、归属、origin/review、来源可用标志；截断标识和 `completeCoverage=false`，任意关系均非已证先修。 |
| `learning.read_evidence` | 必填 `documentId/chunkId/expectedContentHash`，可选 `maxChars≤4000` | 只接受宿主原始材料/当前学习工具观察中已有的精确三元指针，元数据→SourceLocator→元数据复查正式状态/hash/单元；陈旧拒绝，正文截断 PARTIAL；非空 nodeIds 拒绝。 |
| `learning.read_learning_inputs` | `{}` | 至多八份当前任务直接研究成果，列出原成果身份、原始 claims/并列 positions、原限制、质量核查原始观察、独立物理来源数；无宿主材料 PARTIAL，不把模型 dataJson 的质量自称当观察。 |
| `learning.read_assessment_summary` | `{}` | 固定 `formalAvailable=false`、空有效成绩/正式弱项、`mastery=null`；05 未接入。 |
| `learning.draft_path` | 目标库、`new/adjust/replace`、目标、难度、2–8 步及可选日预算；adjust/replace 必填真实基线 ID/64 位指纹 | 每步只能选真实 active 节点，局部 `draft:*` ID；有界时长估计、真实边方向核验、来源重读、确定性保留/新增/移除/改序及旧完成步骤影响。无正式 path/step/revision。 |
| `learning.draft_explanation` | 节点及 1–8 个 `source_fact/system_fact/graph_fact/model_explanation/recommendation` 块 | 来源事实须可信引用与节点-段落真实关联；系统/图谱事实引用真实节点；模型文字不挂伪引用。语义支持统一 `not_evaluated`，人审未完成。 |
| `learning.draft_practice` | 节点及 1–5 道题，局部 ID、题目、参考答案、解释、可选提示/来源 | `mode=informal_practice`；答案供自查，非正式评分、无 assessment/attempt ID、无掌握或解锁凭证。 |

三项草稿均固定 `saved=false`、`recommendationOnly=true`；路径/练习还固定 `executionAllowed=false`、`progressChanged=false`。
草稿工具只整理显式传入的有界模型建议，内部没有模型调用、旧学习服务调用或数据库写入。
缺时间预算输出 `unknown`，不依据 visitedAt 或旧 score 捏造投入/掌握。

## 3. 范围、可信素材与来源语义

- 范围三维取交集：库用 `library_nodes`/文档主库及 topic assignment；节点-原文关联查 `node_evidence→chunk→unit→document`；查询在 LIMIT 前过滤。边保留真实方向，文档范围还要求边证据。
- 路径先检查库可见，再检查整条路径所有步骤的当前范围与目标库真实归属；0 步、超过 8 步、缺节点、重复节点/stepKey、异常位置均返回不可用而非局部完整快照。
- `snapshotFingerprint` 涵盖路径头、步骤 ID、节点、位置、标题、状态、visitedAt、旧分数及更新时间；它只是草稿基线和陈旧检测，非数据库事务快照、正式 revision 或并发写锁。
- `LearningContextReader.snapshot(runId,consumerTaskId)` 的实现责任在宿主：认证同一 run/consumer，仅交当前任务原始直接依赖 Artifact、原始工具观察及已捕获质量观察。W1 只有接口与 fake；不得作为任意全局查询入口。
- 研究 `document_research` 使用冻结 `ResearchArtifacts.study`；`document_comparison` 的 positions 保留未重读 claim，只有 `checkedSourceIds` 与原 Artifact.evidence 一致者才能形成可信指针，未覆盖项标缺口。
- 质量观察须来自宿主捕获而非模型入参，校验直接依赖的质量角色、目标原 artifactId/producerTaskId、claimId、原 sourceId/chunkId/hash 和覆盖数；`CURRENT`/完整引用最多说明特定来源当前有效，不证明文本语义、人审或学习者掌握。
- 来源计数以 `(documentId,chunkId,hash)` 去重；同一物理来源的不同授权范围 ID 只算一份，不同文档归属的相同 hash 不合并。上游 EvidenceRef 保持原值；学习域新引用用独立范围指纹 ID，避免 Runner 同 ID 异 scope/excerpt 冲突。
- 非空 nodeIds 与原文重读不可兼容时返回 `NODE_SCOPE_UNSUPPORTED`；不删除范围条件扩权。纯节点事实可降级为未有原文支持。
- 依赖 partial/旧来源/质量未覆盖的限制继续透传；输出超 32000 字符显式报 `LEARNING_OUTPUT_BUDGET`，不声称全量覆盖或完整成功。

## 4. 隔离验证

执行任务单指定的精确测试类命令（在 `backend-java`）：

```powershell
.\mvnw.cmd '-Dtest=AssistantRuntimeTest,DecisionParserTest,ResearchAgentToolProviderTest,JdbcResearchReadRepositoryTest,ResearchComparisonTest,ResearchDraftValidatorTest,LearningAgentToolProviderTest,JdbcLearningReadRepositoryTest,LearningArtifactHandoffTest,LearningDraftValidatorTest' test
```

结果：公共核心 43/43，研究域回归 24/24，学习域新增 26/26；合计 93/93，0 失败、0 错误、0 跳过。
学习域测试用 fake 仓储/SourceLocator/可信宿主/DecisionProvider 与 mock JDBC；没有 Spring 上下文、业务 HTTP、真实数据库或模型调用。
覆盖路径无/有/越界语义、真实 ID 与旧分数隔离、指纹因步骤状态变化、adjust 基线拒绝及旧完成步骤改序影响、封闭 schema/正式字段拒绝、图谱方向/截断、可信精确重读/变更拒绝、nodeIds 限制、直接依赖与质量伪造拒绝、不同 scope 的来源去重。
实际 Runner fake 场景完成两步未保存路径草稿及差异、两块分层讲解和两道非正式练习；另测试研究直接成果→学习原文重读的双引用合并与 PARTIAL 依赖传播。
只读 mock JDBC 检查 SQL 在 LIMIT 前含库/文档/节点过滤、绑定参数、实际 path/step RowMapper 和来源精确三元关系。
冻结清单按 UTF-8 解码、CRLF→LF、保留结尾换行计算 SHA-256：29/29 存在且匹配，0 缺失/不一致。

## 5. 未验证与后续集成事项

- 生产可信宿主适配尚无；fake 只能验证学习域接口与交接逻辑，不能证明生产 run/consumer 隔离。接线须由总控明确宿主所有权及捕获质量工具原始观察方式。
- `SourceLocator` 的非空 nodeIds 限制仍在；本轮拒绝原文重读，不要求 01～03 改冻结接口。
- mock JDBC 不能证明实际数据库方言/数据分布、SQL 超时、事务一致性或现网权限行为；后续需生产隔离验收。当前网站不可实际验收。
- 模型自然语言的语义正确性、教学质量、先修关系、练习答案正确性均未得到自动证明；事实引用有效也非人工核验。
- W1 不保存/覆盖路径，不创建路径历史或正式 revision，不修旧缓存自动回写。共享冲突文件 `LearningPathService` 未触碰，历史保留及正式写入/版本表留待总控后续排期。
- 05 尚未接入；正式评分、弱项、掌握和阶段推进不在本轮实现。旧客户端 score/visitedAt/完成状态不替代正式凭证。
- 本轮未运行全量测试或 IntegrationTest，未进行生产注册；待总控技术复核与负责人最终验收后再决定后续集成，不自动进入下一阶段。

## 6. 总控复核后的 W1 定向补修（2026-09-26）

- 02 的 `completeCoverage` 改按本次请求解释：`requestedClaims/currentClaims/checks` 仍须自洽，`requestCompleteCoverage=true` 不再要求此次覆盖原成果全部 claims。交接另外汇总同一原成果多批次已核查 claim，输出 `artifactClaimCoverage`、`coveredClaimIds`、`uncoveredClaimIds`；未覆盖时保留已核查观察并返回 `QUALITY_ARTIFACT_CLAIMS_UNCOVERED`/PARTIAL。
- 多批次仅合并互不重复且属于原成果的 claim；重复 claim、未知 claim、原 sourceId/chunk/hash 不匹配、虚报请求完整或计数不自洽继续拒绝。引用当前性与成果整体覆盖、人审/语义支持仍分开。
- 历史中的可信 `ToolStatus.ERROR` 现在仅记录 `toolCallId/toolName/errorCode/observedAt` 于 `failedObservations`，加 `PREVIOUS_LEARNING_TOOL_ERROR`，不从失败结果提取证据；后续合法读材料/非正式草稿继续可执行。工具名不在八项允许集、缺身份字段或有效范围越界的观察仍报 `UNTRUSTED_TOOL_OBSERVATION`；当前 run/consumer 归属仍由宿主 `snapshot` 接口认证。
- 新增合法单 claim 请求、双批次覆盖、重复/未知 claim、虚假完整标记、错误历史及非法观察反例；实际 Runner 验证先取证失败、再读素材、再生成未保存非正式练习，最终保持 PARTIAL 与原失败码。未删历史、未重置预算、未改公共核心。
- 补修只改 `LearningMaterials.java`、`LearningAgentToolProvider.java`、对应两类学习域测试和本报告；冻结 29 项及 01～03 均未改动。指定隔离命令和最终数量以本节最新重跑结果为准。
