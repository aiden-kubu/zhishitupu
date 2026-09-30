# I2 质量治理既有成果接手单（原 02）

日期：2026-09-28。准备者：总控。状态：**材料已准备，待负责人手动转发、原 02 只读核对并回执；尚未认定接手完成。**

本单交接已经存在的代码与证据，不授权继续修复。原 02 不需要为了换作者重写排名补丁；后续质量领域问题由原 02 承接，真实环境复验仍由总控负责。

## 1. 执行身份与本轮边界

- 接收者：现有原 02「质量治理」桌面任务，不创建替代会话或子 Agent。
- 工作树：`D:/知识图谱-worktrees/assistant-02-quality`；预期分支 `codex/assistant-02-quality`；准备时 HEAD 为 `bcd3b60ea4e6e18daad53f31787028a0f69bc6cf`。
- 主项目：`D:/知识图谱`，只读参考，不得修改或反向同步。总控准备时分支为 `main`。
- 模型：要求 `gpt-6-sol / high`；**项目负责人已在桌面确认原 01～05 均为 GPT-6 Sol / High**，确认依据为 2026-09-28 总控对话。工具独立回读未核实，原 02 不得将用户确认描述为自己已自动回读。
- 只允许读取材料、核对目录/分支/Git 状态、计算文件 hash、静态审查以及回复接手结论。
- **不修改任何文件，不创建报告，不编译或运行测试，不运行集成/验收脚本，不启动或连接数据库，不接收或读取数据库凭据。** 保留已有修改，不清理、不切分支、不提交、不部署。
- 不启动其他任务，不研究 App Server，不做真实模型调用、API/前端集成或 I3/W2。

## 2. 原件在哪里，以及真实执行归属

排名实现、新增领域测试与离线报告已经在原 02 工作树，无需复制。总控本次只读核对主项目与原 02 的实现/两个测试一致；本单不是代码同步指令。

| 已有成果 | 实际作者/执行者 | 接手后的责任 |
|---|---|---|
| 排名 SQL 改为可见边端点预聚合 | 此前总控直接在 02 工作树修改，不是原 02 桌面会话执行 | 原 02 审阅承接，后续领域维护由原 02 实施 |
| 新增 15 项排名回归及离线报告 | 此前总控在 02 工作树编写；83 项离线绿灯及之前红灯也由总控运行 | 原 02 理解并维护领域测试；本轮不重跑 |
| 主项目两文件受控汇集、154 项原回归和 15 项单列复跑 | 总控 | 仍由总控管理集成基线和统一回归 |
| 新增真实 JDBC 验收测试、SQL 合成样本、运行脚本、真实结果/零写入证据 | 总控 | 原 02 只读理解，不接管账号、实例、样本装载或验收脚本 |

后续报告要区分“历史作者”“本次审阅接手者”“后续新增补修者”。不要把接手写成原 02 曾经执行过 R1，也不为更改作者标签重做已有成果。总控不能以主项目、前端/API 或适配层为名重新实现质量规则；适配层只传递数据和转换格式，不产生另一套业务判断。

## 3. 当前基线与 hash

以下路径相对主项目或原 02 工作树。规范化规则：UTF-8 解码、CRLF 转 LF、保留结尾换行、不 trim。总控本次已核对两端三文件均一致，原 02 接手时仍须自行核对。

| 文件 | 当前规范化 SHA-256 |
|---|---|
| `backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java` | `fde4199bef96385ea87a3a0bfd671f170edd2501de1a2dab7adc8481b2757e52` |
| `backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightRankingRegressionTest.java` | `78d6e56c7ca07cdbf574ae2437fff94cfbf9fd6b94ce9582e15daf0cbbbc8100` |
| `backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepositoryTest.java` | `c99ac56054eee8f01d20aa3cac9f9f11be3c3a663d5aaeb5919bb5c956b9dabc` |

历史排名修复前的实现 hash 是 `cc2c7aa2e5a401fd6f1c3dbc05d631be563e0dad9551f7f70de7be0ab17ebee6`，前像保留在 [归档源码](D:/知识图谱/docs/assistant-team/integration/I2/R1/archive/JdbcQualityReadRepository.java.before.txt)。这是历史值，**不要把当前 fde419… 版本恢复成旧 cc2c7…，也不要改旧清单使其指向新版本**。

本工作树 [I2-R1 离线报告](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/reports/02-quality-I2-R1.md)与主项目[归档副本](D:/知识图谱/docs/assistant-team/integration/I2/R1/archive/02-quality-I2-R1.md)当前 raw SHA-256 均为 `21ceb14153dee7c3829046182254e37c0a95033b13d7efd1d278f4af5afc7da5`；保留原文，不覆盖作者记录。

总控本次只读核验原 02 的 [W1 冻结清单](D:/知识图谱/docs/assistant-team/references/02-quality-W1-DEPENDENCIES.json)为 **16/16 一致**；主项目 [R1-PACKAGE.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/R1-PACKAGE.json)中 `files` 列出的 **12/12 文件 raw hash 一致**。本次没有读取该包的环境文件或连接数据库。原 02 只核对 `files` 和自身冻结依赖，不探测环境/凭据。

注意：旧 R1 保护快照包括后来经负责人批准更新的协调/登记文档，新根规则、总控规则和本接手单也不在旧白名单内。不要运行旧校验脚本并尝试“修绿”；治理文档增量记录属于总控后续获批集成工作。源码/依赖不符则停止，不将其混同于已说明的文档更新。

## 4. 必读材料（全部只读）

先读现行规则；工作树的旧协调副本仍须了解，但其中旧 App Server 操作说明不再适用：

1. 主项目 [COORDINATION.md](D:/知识图谱/docs/assistant-team/COORDINATION.md)、[CONTROLLER-RULES.md](D:/知识图谱/docs/assistant-team/CONTROLLER-RULES.md)，重点看实际执行者更正、用户模型确认和职责边界。
2. 本工作树 [旧协调记录](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/COORDINATION.md)、[02 W1 任务单](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/assignments/02-quality-W1.md)、[02 W1 报告](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/reports/02-quality-W1.md)、[I2-R1 离线报告](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/reports/02-quality-I2-R1.md)。
3. 主项目 [I2 原中间报告](D:/知识图谱/docs/assistant-team/integration/I2/I2-REPORT.md)、[原 R1 定向任务](D:/知识图谱/docs/assistant-team/integration/I2/I2-R1-QUALITY-PERFORMANCE-TASK.md)、[R1 汇集/复验计划](D:/知识图谱/docs/assistant-team/integration/I2/R1/R1-PLAN.md)、[R1 overlay](D:/知识图谱/docs/assistant-team/integration/I2/R1/R1-OVERLAY.json)及上一节两份清单。它们只说明历史，不构成本次开发或执行授权。
4. 上一节三份领域源码/测试全文；对照归档前像理解排名差异。阅读主项目 [I2QualityInsightRankingTest.java](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2QualityInsightRankingTest.java)和 [R1-FIXTURE.sql](D:/知识图谱/docs/assistant-team/integration/I2/R1/R1-FIXTURE.sql)，理解真实样本与独立预期，不执行文件。
5. 完整阅读下表的 JSON 证据。不要只看本单摘要就称已复核真实 SQL。

| 证据 | 位置 |
|---|---|
| 原 154 项统一回归 | [Original-RESULT.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Offline-20260928T100912110/Original-RESULT.json) |
| 新增 15 项单列复跑 | [Ranking-RESULT.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Offline-20260928T100912110/Ranking-RESULT.json) |
| 原实例身份和样本规模 | [IDENTITY.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/IDENTITY.json) |
| 真实排名、EXPLAIN 和单次耗时 | [Plan-RESULT.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/Plan-RESULT.json) |
| 小型语义结果及项目范围错误 SQL | [Semantics-RESULT.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/Semantics-RESULT.json) |
| 失败后停止 | [FAILURE.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/FAILURE.json)、[RUN.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/RUN.json) |
| 两个 reader 静态窗口 | [Plan-ZERO-WRITE.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/Plan-ZERO-WRITE.json)、[Semantics-ZERO-WRITE.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/Semantics-ZERO-WRITE.json) |
| 正常停止记录 | [SHUTDOWN.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/SHUTDOWN.json) |

原计划中列出的 `R1/R1-REPORT.md` 尚未生成；不要将不存在的报告作为已交付成果。本单用现存原始结果和总控归属更正说明停点，不代替完整性能验收。

## 5. 已有结果和仍未解决的问题

历史执行者均为总控，本次仅重读证据，没有重新执行：

- 领域离线阶段：原 68 项加新增 15 项，83/83 绿灯；之前的红灯 15 项另记，不能加作新增覆盖。
- 主项目统一回归：原 154/154 通过；新增排名 15/15 单列通过。15 项和前次 83 项有重复，不重复计数。
- 原隔离环境：MySQL 8.4.11、UUID `8c6cfcb3-ba49-11f1-bf64-005056c00001`、`127.0.0.1:33078`、库 `kg_assistant_i2_20260927_r01`；7901 仍为 10000 节点、30000 边、2000 资料、20000 chunk。只提供身份事实，不交接账号或实例控制权。
- 真实直接排名：前十为 100000～100009，degree 均为 6；一次 233ms，执行计划不再保持原逐节点相关 COUNT 的失败形态。**不能据此认定完整洞察或 p95 达标。**
- 七个小型完整洞察语义场景成功；随后项目范围完整洞察在 `isolatedTotal` 的 COUNT 查询出现 MySQL 3024 / SQLState HY000。真实测试共定义 3 个方法，已执行 2 个，1 成功、1 错误；七个小场景在方法内部，不冒称七个额外 JUnit 定义。
- 20 次单线程、20 次四并发大规模采样均未开始，其他后续验证组也未继续执行；没有新的 p95。R1 未全部通过，I2 未验收。
- 两个 reader 静态窗口的数据、DDL、binlog 前后一致且无残留 reader 查询；历史收尾记录为正常停机。样本装载在窗口外，不宣称整阶段零技术写入。本次未重新探测实例状态。

当前定位见 [JdbcQualityReadRepository.java](D:/知识图谱-worktrees/assistant-02-quality/backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java:239)：

```java
String isolatedWhere = " FROM visible_nodes n WHERE NOT EXISTS (SELECT 1 FROM visible_edges e"
        + " WHERE e.source_node_id=n.id OR e.target_node_id=n.id)";
long isolatedTotal = scopedCount(scope, cte + "SELECT COUNT(*)" + isolatedWhere);
```

**已证实失败的是项目范围孤立节点总数查询，不是刚补修的排名，也不是已经证明后面的孤立列表同样失败。** `isolatedWhere` 同时供后面的孤立列表复用；未来若要动共享片段，必须先说明是否连带改变第二条查询并申请范围，不能顺带改。

原预算继续是连接 3000ms、socket 4000ms、JDBC statement 2s、session SELECT 1500ms。本次不调整任何预算，不提出通过缩样本、改索引/迁移、缓存结果或跳过断言来通过验收。

## 6. 接手时必须理解的语义与测试限制

- 保持授权范围、`visible_nodes / visible_edges` 和绑定方式；项目范围与所选库并集口径不变，共享节点不因多库归属重复计数。
- 边须 active 且两端均可见；范围外边不计数。原口径没有排除 inactive 节点，不新增此过滤。
- 排名自环只计一次；反向边、同端点不同类型边分别计数；0 度节点保留；度数降序、数值节点 ID 升序；最终 topLimit 截断。
- 孤立节点是授权可见节点中没有可见有效边相接的节点；带有效自环的节点不是孤立节点；总数与按 ID 升序、isolatedLimit 截断的列表不能混淆。
- 不改变公共/领域 DTO、工具 schema、来源/宿主契约；不以修改前端/API、适配层来绕过领域结果。
- 新排名测试的 `nonRankingQueriesAndTheirLimitsKeepFrozenShape` 锁定了非排名 SQL，包括当前孤立 COUNT 和列表。这是 R1 的限定范围断言，不是不能优化 SQL 的永久业务契约。**未来补修若需调整该断言，先在新任务中明确批准准确范围和替代验证；本轮不能删改或弱化测试。**
- fake/mock 的 SQL 结构、参数和映射检查不等于数据库执行或性能证明。接手审查也不等于原 02 已重跑这些测试。

## 7. 原 02 本轮回复清单与停止点

只在当前原桌面会话回复，不创建报告文件。请回报：

1. 实际目录、分支、HEAD、Git 状态；哪些是原有修改，不能判断来源的如实标明，不能清理。
2. 上述三文件在两端的规范化 hash、报告副本一致性、16 项冻结依赖及 R1 `files` 12 项核对结果；缺失或不符即停止，不覆盖修复。
3. 已完整读取哪些材料；确认现有排名补丁真实作者是总控、后续质量领域维护归原 02，不倒改作者记录。
4. 说明排名修改保持的语义、已有离线/真实结果和未完成项，明确新超时具体发生在孤立节点总数查询。
5. 说明共享 `isolatedWhere` 及旧测试形状断言可能影响下一轮修改边界；仅列建议范围与离线测试计划，不编写补丁、不运行测试。
6. 能否承接当前基线；发现前像、契约、冻结依赖或来源不明修改问题时列阻塞，不自行变更。
7. 模型记录：“用户已在桌面确认 GPT-6 Sol / High；工具独立回读未核实”（若确有本轮可靠回读证据，另列证据来源，不猜测）。
8. 明确本轮实际动作是只读审阅/hash 核对，没有开发、编译、测试、数据库连接或其他任务启动。

全部核对完成且无阻塞时，回复：

> 现有成果接手核对完成，后续质量领域维护由原 02 承接；本轮未开发，等待总控新的定向任务单和正式执行指令。

有阻塞则回复“接手核对受阻”并列出证据，然后停止。不能为了凑齐完成句而宣称通过。

原 02 回执后，总控先复核其理解与前像，再准备独立的新补修任务及批准范围；本单不自动启动 I2-R2，不自动执行真实复验或进入后续阶段。

## 8. 总控本次准备记录

本单实际由总控编写；原 02 尚未由本会话启动或收到自动派发，接手状态须等负责人转发后的原任务回执。总控实际模型未核实；子任务当前配置的用户确认与历史开发模型分别记录。

准备前记录主项目 614 个、原 02 工作树 436 个 Git 可见文件的 raw hash；只允许在主项目新增本单、为总控规则增加本次交接索引。没有复制/覆盖领域源码、改历史报告/expected hash、运行测试或触及数据库。本轮文件核对不是新的技术验收结论。
