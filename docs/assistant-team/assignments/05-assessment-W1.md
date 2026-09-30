# 05 测评分析｜W1 任务单与接令检查

准备日期：2026-09-26。总控：原主任务；执行者：原 05「测评分析」。
工作目录：`D:/知识图谱-worktrees/assistant-05-assessment`；分支：`codex/assistant-05-assessment`。
计划报告：`D:/知识图谱-worktrees/assistant-05-assessment/docs/assistant-team/reports/05-assessment-W1.md`。

## 0. 当前闸门：只做接令检查

本任务单定义后续 W1 隔离实施范围，**不是立即开发授权**。本轮只阅读、核对和回复：不修改任何文件，不创建 W1 报告，不编译、不运行测试、不安装依赖。检查结果交回总控后，须收到另发的正式执行指令才可实施。

项目负责人已最终确认 01～04 的 W1 隔离阶段交付；四个原任务均保持待命，不再修改。04 最终基线为 93 项指定测试（公共核心 43、研究域 24、学习域 26），含“质量请求覆盖与成果覆盖分开”及“可信失败历史不阻断后续合法练习”两项补修；总控已再次复核。上述结论不代表网站上线、生产接入或正式学习闭环已经实现。
本轮只准备原 05；严格人工派发，不新建/替换任务，不派生 Agent，不研究或运行 App Server、外部控制器或跨任务接管，不解锁、不调用 .local/assistant-team/control.mjs。
历史 COORDINATION、W0 报告及 manifest 中的控制方式、旧模型和待办不覆盖本单及负责人最新指令；manifest 不能证明当前桌面模型或其他任务的运行状态。
模型要求为 `gpt-6-sol / high`，由负责人在原桌面任务确认。无法可靠回读时如实说明，不自行降配、不探测其他任务。

### 0.1 本轮检查并回复

1. 实际绝对目录、分支及 Git 状态。区分原有 COORDINATION 修改、05 W0 报告与总控本次同步文件；不清理、不覆盖既有改动。
2. 完整阅读下列 11 份文档及关键代码；核对清单全部 42 项存在，规范化 SHA-256 匹配。规范为 UTF-8 解码、CRLF 转 LF、保留结尾换行，不 trim。
3. 概括四项工具、真实路径/阶段与旧成绩边界、学习素材交接、未执行题目草稿及正式结果不可用语义；指出缺少的契约和不支持的范围。
4. 给出仅 assessment 领域内的文件/隔离测试计划。缺文件、hash 不一致或冻结契约不足以安全实施时报告具体阻塞，不能自行同步、修改冻结依赖或扩大范围。
5. 回复计划报告绝对路径，明确“尚未开发、未修改文件、未编译或运行测试，等待总控正式执行指令”。完成后停止。

### 0.2 必读文档及代码

以下相对路径从本工作树根目录解析：

1. `docs/assistant-team/COORDINATION.md`（控制方式等以本单覆盖）。
2. `docs/智能助手模块审计与重规划-2026-09-22.md`。
3. `docs/assistant-team/W0-REVIEW.md`。
4. `docs/assistant-team/W1-CORE.md`。
5. `docs/assistant-team/reports/05-assessment-W0.md`（历史提案，不是额外授权）。
6. 本任务单 `docs/assistant-team/assignments/05-assessment-W1.md`。
7. `docs/assistant-team/references/05-assessment-W1-DEPENDENCIES.json`。
8. `docs/assistant-team/references/01-ingestion-W1.md`。
9. `docs/assistant-team/references/02-quality-W1.md`。
10. `docs/assistant-team/references/03-research-W1.md`。
11. `docs/assistant-team/references/04-learning-W1.md`（含最后两项补修）。

关键只读代码：公共 contract/runtime/tool；SourceLocator/JdbcSourceLocator；ResearchReadRepository/JdbcResearchReadRepository、ResearchArtifacts、ResearchEvidence；QualityArtifactReader；LearningReadRepository/JdbcLearningReadRepository、LearningContextReader、LearningAgentToolProvider、LearningMaterials、LearningDraftValidator 及冻结测试。
同时阅读既有 `backend-java/src/main/java/com/knowledgegraph/learning/LearningPathService.java`、`LearningPathController.java`、`backend-java/src/main/resources/db/migration/V11__learning_paths.sql`，确认路径/阶段/尝试表字段和写入副作用。前端不在本轮修改范围；若静态查看，先完整阅读 frontend/AGENTS.md。

## 1. 正式实施后的阶段目标

接入公共 `Role.ASSESSMENT_ANALYSIS`，交付当前学习上下文、旧尝试可信度分类、正式结果不可用说明与未执行测评草稿。
复用已验收学习域的公开只读路径/节点/来源关联仓储及研究、原文定位能力；不新建第二套 Runner、调度状态、路径版本系统或正式判分服务。
本轮验证领域校验、只读 SQL、草稿整理和 fake 决策交接，不验证真实模型的出题质量、答案正确性、学习者掌握程度或生产身份隔离。

## 2. 四项领域工具

提供 `AssessmentAgentToolProvider.tools()` 或等价领域入口；只允许 ASSESSMENT_ANALYSIS。前三项 READ_ONLY，最后一项 DRAFT_ONLY。不注册 Spring Bean、不开放 HTTP、不开放 ActionProposal 或任何执行器。
使用封闭 JSON schema，明确必填、类型、枚举、上下限并在执行前复验。scope、profile、run、consumer、可信质量状态不能由模型参数替换。

| 工具 | 输入和最低职责 |
|---|---|
| assessment.read_context | 可选预期 pathId/stepId/snapshotFingerprint（指纹只能与 pathId 配套）。只读 local-default 当前路径、真实 pathId/stepId/stepKey、节点/库、顺序、状态、visitedAt、旧分数与观察时刻；区分无路径、范围不足、无 current、多 current、陈旧基线。分别表达草稿准备条件和正式测评不可用。 |
| assessment.read_legacy_history | 必填 pathId，可选该路径真实 stepId；limit 默认 10、范围 1–20，beforeAttemptId 正整数用于按 id 降序的有界游标分页。只查经当前路径及宿主范围验证的旧尝试，保留真实 attemptId/pathId/可空 stepId、score/correctCount/totalCount、客户端 weak_topics、createdAt；全部标 legacy_unverified。 |
| assessment.read_valid_results | 可选 pathId/stepId，若提供须真实核对，不能让越界对象借空结果通过验证。W1 固定 formalAvailable=false、reason=formal_scoring_unavailable、validResults=[]、weaknessItems=[]、mastery=null、有效通过凭证为空；返回 PARTIAL，不把空集包装成“正式结果服务可用，只是还没作答”。 |
| assessment.prepare_draft | 必填当前真实 pathId/stepId/expectedSnapshotFingerprint；题目建议 1–5 道。基线现读并匹配、目标节点从真实步骤解析。整理题干、选项、主题、候选参考答案、解释、sourceIds 和缺口；输出 unexecuted_draft，不生成正式试卷、不提交答案、不判分、不记尝试或推进阶段。 |

四项工具不能调用 save、markVisited、complete、recordAssessment 或既有 PUT/POST；不得因为方法名是 current/read 就绕过已审阅的领域只读适配。
05 W0 提出的新顶层 assessment 包、前端、路由、正式评分及迁移建议在本阶段均不授权；源码统一限定在 assistant/assessment。

### 2.1 上下文、当前阶段和旧记录

- 复用公开 LearningReadRepository/JdbcLearningReadRepository 的路径快照、节点及 nodeSourceLinked；不修改其冻结实现，不通过反射访问 package-private 学习校验类，不冒用 LEARNING_COACH 的工具权限。
- 路径只读取固定 local-default，明示当前为本机/共享档案，不声称已实现登录用户隔离。scope 的库/文档/节点维度取交集，真实对象关联不能靠集合包含或标题猜测。
- 继承整条路径须完整可见的 W1 限制：路径无、不可完整授权、无 current、多个 current/非法状态、失效节点要分开处理；不得擅选第一步当 current。可报告已保存的 completed/locked 等系统状态，但不能据此证明正式有效掌握或本轮推进成功。
- formatVersion=2 只是旧结构版本；formalRevision=null。真实 pathId、stepId、stepKey 分开表达，不能用标题/stepKey/模型 draft:* ID 冒充数据库 ID。
- snapshotFingerprint 是包含步骤变化的观察指纹，不是正式 revision、事务快照或乐观写锁。预期基线不匹配须显式陈旧，不把旧请求重新绑定到新的 current。
- visitedAt 仅代表访问记录，不是学习时长。旧 assessment_score 及 attempts.score 不等于可信有效成绩；不依据 >=60 推断正式 passed、mastery 或解锁资格。
- 旧历史 W1 只覆盖仍存在且获授权的当前路径，不承诺被级联删除的历史能够恢复。先核实 path/profile/范围，再在 SQL 的 LIMIT 前按路径及可选阶段筛选；阶段必须属于该路径，非空 step_id 指向别的路径或缺失对象时拒绝/明确异常，不能静默改挂当前阶段。
- step_id=NULL 如实返回阶段未知，不补成当前阶段。按具体 stepId 查询时不混入无法归属的记录；路径级查询可以列出未知阶段记录，但须明确无法给出阶段成绩。
- 客户端 weak_topics 只能作为 legacyClientWeakTopics 等不可信原始标签展示；不得转成正式 weaknessItems、题目级错因、错误率或已验证复习重点。无逐题原始记录时明确不可重算。
- 旧记录异常分数/计数、过长或异常 weak_topics 要标记或拒绝，不能静默“修正”为有效成绩。文本/JSON读取及输出有界，披露截断。
- 分页须区分本页读取完成、hasMore/nextCursor 与整个查询历史覆盖。后续页 hasMore=false 不代表已经返回全部历史；可选 stepId 的覆盖只对该筛选成立。截断/并发变化不能宣称全量一致快照。
- 历史读取及草稿校验应检测关键路径/步骤基线变化并报告陈旧；无法证明原子一致性要列限制，不声称有事务保证。SQL 超时/真实方言兼容留后续集成。

### 2.2 可信素材交接与来源核验

- 工具 Context 无直接 Artifact/历史观察字段。允许在 assessment 包新增只读 AssessmentContextReader（或等价宿主边界），按 runId/consumerTaskId 取得当前任务获授权的原始直接依赖及宿主捕获工具观察；本阶段只接口与 fake，不提供任意全局查询、生产实现或公共 Context 变更。
- 明确每份原始 Artifact 的 artifactId/producerTaskId/role/scope/outcome，以及每个捕获观察所属的生产任务、toolCallId/toolName/时间。只有当前 run、当前 consumer 的直接依赖或当前任务自身原始观察可用；不得从模型摘要或模型入参伪造这些身份。
- 04 原始学习成果及 learning.draft_path、learning.draft_explanation、learning.draft_practice 的捕获结果可作为学习材料/建议。工具输出须与对应可信任务、对象、范围和 Artifact 关联核对；Artifact.dataJson 是模型产物，不能只信其 saved/qualityVerified/score 自报字段。
- 04 未保存路径、局部 draft:* 步骤和非正式练习不能变成正式 path/step/assessment/attempt；已有 basePathRef 仅是只读旧基线。05 如需绑定阶段，必须自己重读当前真实路径/阶段。
- W1 必须验证真实 Runner 中“04 生成非正式练习 → 05 读取准备条件并形成未执行测评候选”的直接依赖交接；fake 宿主传递原始捕获观察，不能只手工拼一份带可信标签的模型 JSON。
- 研究来源若来自 03，必须属于当前任务直接依赖；不能借 04 任意读取其上上游成果。04 成果实际携带的可信原引用可复用，但仍需范围/对象/版本核对和关键原文重读，不能因为曾被学习工具接受就免检。
- 来源指针必须来自可信原始 EvidenceRef 与宿主捕获观察；以真实 documentId/chunkId/hash 精确重读，检查正式文档/单元状态、归属、节点-段落实际关联及读取前后版本。不能凭相同 hash、chunkIndex 或模型自报 sourceId 构造身份。
- 保留上游 EvidenceRef 的原 sourceId/scope/excerpt，不替换成同 ID 新内容。新测评域证据 ID 须稳定且包含范围/对象版本，避免跨任务重读冲突。独立来源按 documentId/chunkId/hash 计数，不把不同授权范围 ID 当独立证据。
- 冻结 SourceLocator 对非空 nodeIds 的原文重读不支持：若题目要求此类来源，应明确拒绝/降级为未获原文支持，不能删除 nodeIds 扩权。仅节点事实/明确无原文支持的建议可按真实可用范围继续。
- 02 质量核查或 04 转述的核查结果都不证明语义正确、人工核验或答案唯一。05 W1 不另建质量验证器；缺少经过可信宿主交叉核对的原始核查观察时，质量状态标 unavailable。若展示已经核对的观察，保留“本次请求覆盖”与“原成果论断覆盖”的区别及未覆盖项，不从 partial 升格为完整核验。
- 可信历史工具 ERROR 保留错误原因，不携带/产生证据，不全局阻断后续独立合法操作；未知工具、越界、假生产者或跨 run/consumer 仍拒绝。不得删除历史、重置预算或用放宽身份检查修复恢复流程。
- 必需依赖 FAILED/NEEDS_INPUT 由公共核心阻断；接受 PARTIAL 须显式配置并继续传播缺口。材料不够时域内错误/PARTIAL，再由决策层给 needs_input，不新增公共 outcome 或后台任务。

### 2.3 未执行题目草稿与正式测评边界

- 本 W1 最小题型限定单选候选题：每题局部 draft:* ID、题干、2–4 个唯一选项 ID/文字、一个存在于选项中的候选 referenceOptionId、解释、主题及最多 4 个 sourceIds。题目 1–5 道；实际 fake 场景至少形成两道完整候选，不只返回工具说明。
- 模型显式提供候选内容，工具只做确定性结构/范围/基线/来源校验与整理，不在内部调用模型。候选答案只供草稿审阅，不是正式作答 DTO；来源存在/hash 一致不能证明题目无歧义或答案正确。
- 如参考 04 练习，保留来源任务/观察身份和非正式属性；不把其自查答案当正式标准答案。新候选内容与原练习不同之处仍属于模型建议。
- 目标必须匹配当前真实且完整可见路径的唯一 current 步骤与 active 节点；无路径、全完成无 current、陈旧基线、错步骤/节点时明确不可准备，不回退固定通用题冒充该阶段测评。
- 证据缺失时可以输出明确标为待补证的未执行建议，返回 PARTIAL 并逐题列出缺口；提供了伪造/越界/陈旧 sourceId 时不得静默丢掉引用后当成合法无证据草稿。
- 所有候选统一 semanticSupport=not_evaluated、answerValidity=unverified、formalEligible=false；不代签人审或质量通过。不因有完整引用而声称正式出题条件全部具备。
- 固定 status=unexecuted_draft、saved=false、executionAllowed=false、recommendationOnly=true、progressChanged=false、formalAssessmentAvailable=false。只保留真实旧基线引用；没有新正式 assessmentId/attemptId/pathId/stepId/revision 或正式得分、passed、mastery 回执。
- 不接收用户答卷/客户端总分进行正式判断；不实现提交、评分、幂等写入或解锁。read_valid_results 交给学习角色的正式弱项与有效结果仍为空，不要求 04 修改冻结 read_assessment_summary。
- 题目长度、选项、来源数、素材数、查询次数都有界；单段原文最多 4000 字、直接素材最多 8 份、捕获观察最多 32 个，遵守 ToolResult.dataJson 32000、Artifact.dataJson 16000、excerpt 2048 及工具整体约 64K 限额。超出时显式报错/披露截断，不伪称完成。
- 复用核心 deadline/cancel 检查；不把 DB 阻塞可强制打断、查询原子一致、真实教学效果当隔离测试结论。

## 3. 文件所有权和冻结依赖

总控受控同步 42 项只读依赖，详见 references/05-assessment-W1-DEPENDENCIES.json：
- 04 已验证的 29 项基线：公共核心 9 个源码/2 个测试；01 原文定位 2 个源码；02 QualityArtifactReader；03 研究域 6 个源码/4 个测试；W1-CORE/W0-REVIEW 与 01～03 报告参考副本 5 份。
- 04 最终验收版 8 个学习源码、4 个学习测试、W1 报告参考副本，共 13 项。
依赖来源为 04 的已复核快照；原 29 项先核对原冻结清单，再生成 05 清单。复制仅供编译、契约及隔离回归，不是生产合并、工具注册或自动派发。
已有回归基线为核心 43 + 研究 24 + 学习 26 = 93，不计作 05 新增测试；05 增量须单列。

**仅收到正式执行指令后**允许新增/修改：
- backend-java/src/main/java/com/knowledgegraph/assistant/assessment/**
- backend-java/src/test/java/com/knowledgegraph/assistant/assessment/**
- docs/assistant-team/reports/05-assessment-W1.md

公共 contract/runtime/tool、source/quality/research/learning 及其测试均只读。禁止改既有 LearningPathService/Controller、任何业务服务/DTO、前端、pom、配置/密钥、迁移、协调文件、任务单/清单、主工作树或其他工作树。不提交、合并、push、部署、安装新依赖或派生 Agent。
原 COORDINATION 改动和 W0 报告保留。需要改变契约时报告最小需求并停止，不能要求 01～04继续开发，不进入 W2 或生产集成。

## 4. 正式执行后的隔离验证与报告

本节只定义后续计划，接令检查不得执行。用 fake 学习/研究仓储、SourceLocator、宿主、DecisionProvider 和 mock JDBC，不起 Spring、不连接 MySQL/真实模型/HTTP，不跑全量测试或 IntegrationTest。

建议四类：AssessmentAgentToolProviderTest、JdbcAssessmentReadRepositoryTest、AssessmentArtifactHandoffTest、AssessmentDraftValidatorTest，覆盖：

- 封闭 schema、未知/正式字段拒绝、角色/允许列表、工具只读及草稿效果；对既有写服务零调用。
- 无路径、路径范围不足、无/多个 current、失效节点、真实 ID 与格式 version=2 的区别、步骤变化导致基线陈旧；不自动绑定新 current。
- 库/资料/节点交集、同名跨库、步骤不属路径、非空 step_id 异常、step_id=NULL 不补挂；SQL 范围/路径/步骤过滤在 LIMIT 前，真实 RowMapper 和参数绑定。
- 旧高分及旧 completed 状态仍不等于正式有效结果；客户端 weak_topics 不转成正式弱项；异常记录/过长字段、分页首/中/末页、过滤覆盖及截断诚实表达。
- 正式服务尚不存在时空有效成绩/弱项、mastery=null、formalAvailable=false；不能报告“已通过”或“已经推进”。
- 两道合法未执行候选及题目/选项/参考答案结构错误、重复局部 ID、伪正式 ID/分数、陈旧基线、无证据降级、伪造引用拒绝；不隐藏写入或调用模型。
- 来源真实归属与 nodeSourceLinked、精确 chunk/hash 重读、归档/单元状态变化、nodeIds 限制、跨范围同物理来源去重与稳定引用合并。
- 当前直接学习/研究材料身份核对；fake 宿主拒绝非直接依赖、跨 run/consumer、假观察、模型自报 saved/quality/valid；04 未保存路径/非正式练习不升级为正式对象。
- 使用真实 AssistantRuntime/AgentRunner 的 04→05 交接（04 原工具生成非正式练习，05 读取准备条件并形成草稿）、失败依赖阻断、partial 延续、可信工具失败后合法调用仍可继续且保留失败信息，不删历史或重置预算。
- 取消/超时检查、输出预算及稳定错误码。mock 不代表真实 MySQL 兼容、生产宿主权限或付费模型可靠性。

指定命令（仅正式执行阶段，在本工作树 backend-java）：

```powershell
.\mvnw.cmd '-Dtest=AssistantRuntimeTest,DecisionParserTest,ResearchAgentToolProviderTest,JdbcResearchReadRepositoryTest,ResearchComparisonTest,ResearchDraftValidatorTest,LearningAgentToolProviderTest,JdbcLearningReadRepositoryTest,LearningArtifactHandoffTest,LearningDraftValidatorTest,AssessmentAgentToolProviderTest,JdbcAssessmentReadRepositoryTest,AssessmentArtifactHandoffTest,AssessmentDraftValidatorTest' test
```

类名调整时列真实存在的精确列表，不使用通配符或忽略测试不存在错误。报告分列核心43/研究24/学习26/测评新增与总数，复核42项冻结 hash。
报告写明文件范围、四项 schema/工具、真实上下文与旧成绩/空正式结果口径、历史分页/未知阶段、草稿属性与证据缺口、宿主最小契约、验证及未做项；必须声明当前网站不可实际验收。
完成后汇报并停止，等待总控技术复核与负责人最终验收；不自动进入生产接线、正式评分、前端或 W2。
