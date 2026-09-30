# 原 03｜I2-R4 正式执行指令

日期：2026-09-29。由项目负责人手动转发给原 03「图谱研究」。这是接令检查完成后的正式执行指令，不是再次接令检查，也不授权 06 开始测试。

原 03：按负责人已经批准的研究领域分析与修复安排，现在正式开始 I2-R4 的领域离线分析、限定修复和单元回归。

## 1. 已完成的门禁与模型记录

你的 I2-R4 增量接令回执已收到，未报告文件前像、冻结依赖或新增路径阻塞。06 已完成只读入职并停止。本轮负责人明确回复：“我已核实03的模型”。模型要求仍为 GPT-6 Sol / High；报告分别记录本轮负责人确认与工具核实状态，不把桌面人工确认写成工具独立回读成功。工具无法回读时仍注明“实际模型未核实；实际推理强度未核实（工具独立回读）”。不重复要求负责人作同一确认。

复用刚完成的接令检查和未变化材料的结论，不再次全文审查。执行前仅确认直接修改目标未在接令后变化、新增路径仍未被占用；若有未说明变化则停报，不覆盖。

## 2. 工作位置与有效任务基线

- 仅在既有工作树 `D:/知识图谱-worktrees/assistant-03-research` 执行，分支 `codex/assistant-03-research`，接令 HEAD 为 `bcd3b60ea4e6e18daad53f31787028a0f69bc6cf`。每条命令显式使用该工作树或其 backend-java 子目录；不在主项目执行编译或写操作。
- 完整范围以 [I2-R4 任务单](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-RESEARCH-EDGE-QUERY-TASK.md)及 [证据清单](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-RESEARCH-EDGE-EVIDENCE.json)为准。本指令解除其中“仅接令检查、等待正式执行”的阶段停点，不扩大其他边界。
- 任务单 raw SHA-256：`faa3d2e707696bfc29ec5f2831ecdd6aff5d1355f055afd493fcf6e0e781d994`。
- 证据清单 raw SHA-256：`afca6f306ac755c41e3c9c9ed914205dbe1bfb9bdd72fe00a1d00fd8c1cfc1c3`。
- 保留所有已有修改，不新建或切换工作树、不创建替代会话、不研究 App Server，不提交或推送。

## 3. 本次允许修改

以下路径全部相对于原 03 工作树，不表示可修改主项目同名文件：

1. `backend-java/src/main/java/com/knowledgegraph/assistant/research/JdbcResearchReadRepository.java`：仅 outgoingEdges / incidentEdges 共用边查询链路及必要的边查询私有片段。
2. `backend-java/src/test/java/com/knowledgegraph/assistant/research/JdbcResearchReadRepositoryTest.java`：仅允许调整 `edgeQueryChecksBothEndpointsAndRealEvidenceBeforeLimit` 的相关 SQL 形状断言，并用等价或更强的保护替代；其他四个用例不变。
3. 新增 `backend-java/src/test/java/com/knowledgegraph/assistant/research/JdbcResearchEdgeQueryPerformanceRegressionTest.java`。
4. 新增 `docs/assistant-team/reports/03-research-I2-R4.md`。
5. 新增 `docs/assistant-team/reports/I2-R4/` 内本轮独立、脱敏的测试证据及索引，不覆盖历史证据。

两个既有目标的规范化前像仍分别为：

- 实现：`b922ec24fc397fd772524524c34c6837a3f044bff4bd18efcdd23c37ae67612f`。
- 既有测试：`1b7c80d568cd0e75b982427c10c404b90f608ea8dee83daf4b92843a131277ce`。

规范化规则：UTF-8 解码、CRLF 转 LF、保留结尾换行、不 trim。

## 4. 分析与修复要求

先分析共用边查询及范围分支，再选择有依据、能够保持语义的实现；总控不指定 SQL 写法。没有问题的非边查询不得为统一风格而修改。共享辅助逻辑的其他调用所生成的 SQL、绑定与行为必须保持不变；若无法在该边界内完成，提出准确扩围申请后停止。

任务单第 4 节全部语义约束继续生效，尤其是：

- library/document/node 维度交集与单维度集合语义；真实方向及双端可见性、来源条件先过滤，再按 e.id 升序取 limit+1。
- outgoing 只出边；incident 保留 source→target；自环不重复，反向边和不同关系类型不合并，多证据/多库归属不产生重复边。
- 文档范围下两端节点证据与边证据独立满足正式来源条件，不额外要求来自同一文档或 chunk。
- has_source 与边可见性分别保持；不引入质量域的 active-edge 规则；保留字段、状态、Long ID、hasMore、空结果与异常传播。
- 不吞超时、不用固定空结果冒充正确查询，不扩大权限、不改变超时预算或返回契约。

当前只有真实 3024 失败记录，没有该研究 SQL 的真实 EXPLAIN；不能把质量域执行计划当作研究根因，也不能把测试方法总耗时写成单条 SQL 耗时。若静态证据不足以确定安全修法，提交给总控的诊断请求，列明查询、参数、待回答问题及预算，由总控另行安排获批的 06 补证；原 03 停止等待，不自行连接数据库。

## 5. 离线验证与交付

按任务单测试矩阵补充覆盖，只用 fake/mock 与离线单元测试。精确选择原六类：AssistantRuntimeTest、DecisionParserTest、ResearchAgentToolProviderTest、JdbcResearchReadRepositoryTest、ResearchComparisonTest、ResearchDraftValidatorTest。原基线为 67 个展开场景，必须实际运行并报告本轮结果；新增测试类单独统计。

使用离线 Maven 和每轮唯一 surefire reportNameSuffix；保留失败、错误、修正后重跑及原始 XML hash。分别记录测试方法定义数、展开场景数、执行轮次和重跑次数，不把重跑算新覆盖。不能通过改标准、弱化断言、无效输入或修改 mock 掩盖错误。缺依赖时报告，不自行安装或修改 pom。

报告必须包含：

1. 实际修改的查询/分支及原因；既有断言到新保护的逐项映射。
2. 检查过但未修改的查询/分支及原因。
3. 发现但需另行批准的事项，以及事实、静态推断与待真实验证的假设。
4. 文件前后 hash、冻结依赖当前一致性证据、测试分组/数量/轮次及证据路径。不得改写历史 expected hash。
5. 实际代码修改者、测试运行者、真实环境验证者、模型要求、负责人确认来源及工具独立核实状态。本轮若由原 03 修改和自测，应如实归属原 03；真实环境验证为“本轮未执行”。

明确说明：mock 通过不代表 MySQL 查询语义或真实性能已验收，不代表 I2 通过。

## 6. 禁止事项及停止点

不连接或启动数据库，不读取数据库账号；不改索引、迁移、权限、公共/领域契约、其他领域、既有业务、主项目、集成 i2 测试或脚本、前端；不做汇集、部署、真实模型、I3 或 W2。

完成离线交付后立即停止，回复报告和测试证据路径，等待总控增量技术复核。03 不自行派发 06；06 后续汇集、统一回归和真实复验须另获明确批次指令。最终验收由负责人确认。
