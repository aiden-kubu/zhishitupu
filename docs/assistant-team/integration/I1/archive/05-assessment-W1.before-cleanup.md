# 05 测评分析｜W1 隔离实施报告

日期：2026-09-26。工作树：`D:/知识图谱-worktrees/assistant-05-assessment`；分支：`codex/assistant-05-assessment`。
本轮仅交付领域内只读事实、可信材料检查与未执行候选草稿；未实现正式评分、作答持久化或生产接线。

## 实际交付与所有权

- 新增 `backend-05「测评分析」W1 阶段交付我已确认。

当前验收结论按阶段范围理解为：

- 05 隔离实现和总控技术复核完成；
- Long ID 对象比较问题已经修复，并补充针对性回归测试；
- 指定测试 113 项通过，其中冻结回归 93 项、05 测评域新增 20 项；
- 42 项冻结依赖 hash 校验一致；
- 当前测评题仍属于未执行草稿，不代表正式试卷、正式作答或正式评分；
- 旧 score、visitedAt 和非正式练习不得作为正式掌握度、通过或阶段推进凭证；
- 当前验收不代表测评分析已经在网站上线；
- 真实数据库、模型、API、前端、鉴权、可信生产宿主和正式测评闭环继续留到后续集成验收。

05 保持待命。

至此，01～05 五个领域任务的 W1 隔离阶段均已完成。

现在不要直接进入 W2，也不要立即开始新的代码修改。

请总控先做一次 W1 总盘点，并给我一份面向项目负责人的下一阶段集成方案，重点说明：

1. W1 公共核心 + 01～05 目前分别真正实现了什么；
2. 五个 Agent 之间实际的数据/Artifact/证据流转关系；
3. 哪些能力现在只是 fake/mock 隔离验证，哪些已经是真实代码但尚未生产接线；
4. 要让这些能力真正出现在现有网站里，还缺哪些工作；
5. 真实 MySQL、真实模型、生产宿主、API、鉴权、前端分别应该在哪一步接入；
6. 最终网站左侧应该增加哪些入口，用户实际能看到哪些页面、状态、图谱、学习路线和测评结果；
7. 图谱研究结果如何与现有知识图谱可视化连接；
8. 哪些功能属于后续 W2 写操作，哪些应该先完成只读集成；
9. 给出下一阶段的实施顺序和每一步我可以亲自验收的方法；
10. 特别列出当前仍不能对外宣称已经完成的能力。

先只做盘点和方案，不修改源码、不启动任何子任务、不部署。java/src/main/java/com/knowledgegraph/assistant/assessment/` 下 8 个源码：`AssessmentAgentToolProvider`、`AssessmentContextReader`、`AssessmentReadRepository`、`JdbcAssessmentReadRepository`、`AssessmentMaterials`、`AssessmentDraftValidator`、`AssessmentEvidence`、`AssessmentJson`。
- 新增同领域测试目录下 4 类：`AssessmentAgentToolProviderTest`、`JdbcAssessmentReadRepositoryTest`、`AssessmentArtifactHandoffTest`、`AssessmentDraftValidatorTest`。
- 本报告是唯一新增文档；未修改公共 contract/runtime/tool、source/research/learning、业务服务、前端、配置或迁移。原有 `COORDINATION.md` 修改、W0 报告及总控同步文件均保留，未清理或覆盖。
- `AssessmentAgentToolProvider.tools()` 是未注册的领域入口（`AssessmentAgentToolProvider.java:49`）；无 Spring Bean、公共路由、ActionProposal、HTTP 或写端点。四项工具仅授权 `ASSESSMENT_ANALYSIS`，前三项 `READ_ONLY`、草稿项 `DRAFT_ONLY`；封闭 JSON schema 与执行前复验见该文件 20–104 行。

## 四项工具的字段与结果口径

1. `assessment.read_context`：可选 `pathId`、`stepId`、`snapshotFingerprint`；`stepId`/指纹须搭配 `pathId`。只读 `local-default` 路径及完整可见步骤，返回 `state`、真实 `pathId/libraryId/currentStepId/currentStepKey/currentNodeId`、`steps[]`（真实 ID、顺序、状态、`visitedAt`、`legacyAssessmentScore` 与 `scoreValidity=legacy_unverified`）、`snapshotFingerprint`、`formatVersion=2`、`formalRevision=null`、`observedAt`、`draftReady`、`formalAvailable=false`。无路径、范围不足、无/多个 current、非法状态及失效节点分别表达；有预期 ID 或指纹时拒绝陈旧基线，并在完成读取前复核指纹（`AssessmentAgentToolProvider.java:106–179`）。`visitedAt` 不作时长，旧分数不作通过凭证。返回的 04 `learningPractices[]` 包含原 `artifactId/producerTaskId/toolCallId/practiceRef` 且标 `informal_practice`。
2. `assessment.read_legacy_history`：必填真实 `pathId`，可选该路径真实 `stepId`、`limit`（默认 10、范围 1–20）、`beforeAttemptId`。先复用完整路径/范围校验，再以绑定参数在 SQL 的 `LIMIT` 前交集过滤库/文档/节点、路径、步骤及游标，按 attempt ID 降序取 `limit+1`（`JdbcAssessmentReadRepository.java:15–54`）。每条保留真实 `attemptId/pathId/stepId`、客户端 `score/correctCount/totalCount/legacyClientWeakTopics`、`createdAt`，恒为 `legacy_unverified`；`stepId=null` 则 `stageAttribution=unknown`，不补挂当前步骤。非空步骤外链不属当前路径或丢失即拒绝；异常分数/计数/weak_topics 有 `anomalies`，不升级成绩。返回 `pageComplete/hasMore/nextCursor/completeCoverage/coverageAppliesTo`；后续页即使末页也不声称覆盖全部历史。关键路径指纹读取前后对比（`AssessmentAgentToolProvider.java:181–248`）。
3. `assessment.read_valid_results`：可选真实 `pathId/stepId`，提供时先校验范围及归属。W1 固定 `ToolStatus.PARTIAL`、`formalAvailable=false`、`reason=formal_scoring_unavailable`、`validResults=[]`、`weaknessItems=[]`、`mastery=null`、`validPassingAttemptId=null`；不是“正式服务正常但暂无作答”（`AssessmentAgentToolProvider.java:250–268`）。04 的旧成绩与非正式练习都不进入有效结果。
4. `assessment.prepare_draft`：必填当前真实 `pathId/stepId/expectedSnapshotFingerprint`，1–5 道模型显式提供的单选候选。每题 `draft:* localId`、题干、主题、2–4 个不重复选项、选项内 `referenceOptionId`、解释、最多 4 个 `sourceIds`，可选 `basedOnPracticeRef`。只绑定完整可见路径的唯一 current 步骤与 active 节点；失效路径/步骤/指纹拒绝，不能退回固定通用题（`AssessmentDraftValidator.java:22–85`）。输出 `status=unexecuted_draft`、`saved=false`、`executionAllowed=false`、`recommendationOnly=true`、`progressChanged=false`、`formalAssessmentAvailable=false`；`assessmentId/attemptId/score/passed/mastery` 均为空。每题 `semanticSupport=not_evaluated`、`answerValidity=unverified`、`formalEligible=false`；无来源时明确 `evidenceGap=source_missing` 和 `questionsMissingEvidence`，作为 `PARTIAL` 未执行建议，不执行、不入库、不判分（`AssessmentDraftValidator.java:119–154`）。

## 可信宿主、证据与隔离边界

- 领域宿主接口 `AssessmentContextReader.snapshot(runId, consumerTaskId)` 只定义当前消费者的原始直接 Artifact、生产角色和宿主捕获的 `ToolObservation`；W1 只有 fake，无生产实现或全局查询（`AssessmentContextReader.java:15–26`）。生产宿主必须认证 run/consumer，模型摘要或入参不能替代这些身份。
- `AssessmentMaterials.inspect` 校验直接依赖任务、角色、Artifact 授权范围、观察的工具名/调用 ID/时间/有效范围；假工具、非直接或越界材料拒绝（`AssessmentMaterials.java:35–80`）。04 `learning.draft_practice` 必须由原始捕获结果确认是 `informal_practice` 且各不执行标志为真；练习引用保留 `artifactId/producerTaskId/toolCallId/localId`，只能作为建议素材（`AssessmentMaterials.java:83–124`；`AssessmentDraftValidator.java:120–138`）。可信 `ERROR` 只保留 `errorCode/observedAt` 而不产证据，后续独立合法观察仍可使用；`PARTIAL` 缺口继续传播。
- 候选 sourceId 必须见于可信原始 EvidenceRef/观察；`documentId/chunkId/hash` 精确解析，校验实际节点-来源关联、正式文档/单元状态、原文定位及元数据读取前后版本，保留原 ref 的 ID/scope/excerpt，不由同 hash 推断来源独立性（`AssessmentMaterials.java:129–162`；`AssessmentDraftValidator.java:91–115`）。伪造、陈旧、未关联或截断的原文拒绝；非空 `nodeIds` 下原文重读直接 `NODE_SCOPE_UNSUPPORTED`，绝不删掉节点条件扩权。引用可核验不代表语义正确、答案唯一或人审通过；质量观察未集成时标 `QUALITY_OBSERVATION_UNAVAILABLE`。
- 路径快照指纹仅为只读观察，不是正式 revision、事务快照或写锁。旧路径被级联删除的尝试无法恢复；`local-default` 是本机/共享档案，不是登录用户隔离。客户端逐题答案不存在，不能从旧 totals 重算掌握或正式弱项。
- 工具输出有 `dataJson<=32000`、最多 64 个 evidence 引用的预算检查；单段原文最多 4000 字，宿主材料最多 8 个 Artifact / 32 条捕获观察。遵守公共 `deadline/cancel` 预检，但无法在本隔离层强制中断阻塞 JDBC。

## 隔离验证

- 在本工作树 `backend-java` 执行任务单指定的 14 个精确测试类命令，定向补修后 `BUILD SUCCESS`：**113/113，通过、0 失败、0 错误、0 跳过**。公共核心 43、研究域 24、学习域 26，共 93 项是冻结回归基线，不计入 05 新增能力；05 新增 **20** 项（工具 10、真实 Runtime 交接/材料 2、草稿 5、mock JDBC 3）。
- mock JDBC 验证交集条件、步骤/游标过滤均在 `LIMIT` 前、参数绑定、实际 RowMapper 保留 `NULL step_id` 及 `limit+1`；fake 仓储覆盖旧高分不升级、异常记录、分页覆盖口径、多个 current、步骤/路径中途变化、正式结果空值、无证据降级、伪造/未关联/陈旧来源拒绝。
- 真实 `AssistantRuntime/AgentRunner` 用 fake 决策器执行 04 `learning.draft_practice` → 宿主传递原捕获观察 → 05 `read_context`/`prepare_draft`；实际形成两道完整候选题，05 为 `PARTIAL`，原练习和候选都没有正式判分、保存或进度变更。可信失败观察保留原因且不挡后续合法练习；未知工具仍拒绝。测试只构造了只读仓储/原文 mock 与 fake 宿主，没有真实业务写调用。
- 实施中首次运行遇到 05 测试编译问题，后续遇到历史时间字段未序列化、夹具身份和 Mockito 嵌套桩问题，均已在 05 授权文件内修正；以上只报告最终通过的完整精确命令结果。
- 完成后按 UTF-8 解码、CRLF 转 LF、保留结尾换行（不 trim）重新计算 `references/05-assessment-W1-DEPENDENCIES.json` 所列 SHA-256：**42/42 匹配，0 异常**；冻结依赖未改。

### 总控定向复核后的补修（2026-09-26）

- 总控隔离复现：`pathId=1000` 时，旧历史的 `Long` 包装对象身份比较让数值相同、对象独立的前后快照误报 `STALE_PATH_BASELINE`；正确阶段的 `actualStepPathId` 也误报 `LEGACY_STEP_OWNERSHIP_MISMATCH`。缓存内 `pathId=91` 掩盖了问题。
- 仅在 `AssessmentAgentToolProvider.java:203,234–236` 把这两处比较改为显式 `longValue()` 数值比较；保留 `actualStepPathId==null`、后读路径非 AVAILABLE / `pathId==null`、阶段实际归属、路径 ID 与指纹变更检测。检查 assessment 源码的其余包装类型比较：`Long.equals` 是值比较，包装值与 primitive 比较会拆箱；未发现第三处包装对象身份比较，冻结依赖未动。
- `AssessmentAgentToolProviderTest.java:199–269` 新增 3 个回归：分别构造 `pathId=1000` 且 `Long` 对象确实不同的前后路径快照，验证空历史和正确 `stepId=1001` / `actualStepPathId=1000` 可用；另验证真实路径 ID 改为 1002、指纹改变、阶段归属 1002 或缺失仍拒绝。以上已包含于本节 **113/113** 精确测试和 **42/42** 冻结 hash 复核结果。

## 未验证事项、集成限制与总控后续决策

- 未启动 Spring、真实 MySQL/模型/HTTP，未运行全量或 IntegrationTest；mock SQL 不能证明真实方言/超时/并发一致性，fake 不能证明生产宿主的 run/consumer 身份隔离。没有生产宿主实现、正式试卷/作答/服务端评分/有效结果查询、人工核验或前端接线，**当前网站不可实际验收**。
- 总控后续需要决定并负责：生产宿主对原 Artifact/ToolObservation 的认证与传递、正式评分服务和有效结果凭证、共享路由/注册及学习角色查询接线。04 的非正式练习只能作素材；路径规划仍归 04。不得把现有学习写端点注册给模型或将本草稿解释成入库确认。
- 本轮没有更改公共 AgentTask/ToolResult/AgentResult/ActionProposal、调度器、公共路由或迁移编号，也未提交、合并、push、部署或进入 W2。桌面模型/推理档位无法由源码可靠回读；`gpt-6-sol / high` 由项目负责人在桌面任务确认。

05 W1 隔离实施完成，等待总控技术复核和项目负责人最终验收。
