# 02 质量治理｜I2-R2 孤立节点总数离线补修报告

日期：2026-09-28。执行者：原 02「质量治理」当前桌面会话。工作树 `D:/知识图谱-worktrees/assistant-02-quality`；分支 `codex/assistant-02-quality`；HEAD `bcd3b60ea4e6e18daad53f31787028a0f69bc6cf` 未变。本轮仅获四文件离线授权，完成后停止，待总控复核；I2 尚未验收。

## 执行归属与边界

- 本轮质量 SQL、R1 测试单条预期、新测试与本报告由原 02 当前会话实际修改；离线 Maven 也由本会话运行。此前 R1 排名 SQL、15 项排名测试和 R1 报告实际由总控编写、运行，本次不倒改作者或重写排名成果。
- 本轮真实环境验证者：无。总控此前 R1 的真实 MySQL 结果是历史证据，不是本轮复验。主项目未修改，未运行集成脚本、启动应用/数据库或读取数据库凭据。
- 模型要求 `gpt-6-sol / high`；项目负责人在本轮指令中说明已在桌面确认 GPT-6 Sol / High。工具独立回读未核实；实际模型未核实，实际推理强度未核实。该确认不追溯证明此前总控 R1 开发所用模型。

## 四文件与前后像

规范化方法为 UTF-8 解码、CRLF 转 LF、保留结尾换行、不 trim。本次三份 Java 文件的 raw 与规范化 hash 各自相同：

| 文件 | 进入本轮前 SHA-256 | 本轮完成后 SHA-256 | 实际变化 |
|---|---|---|---|
| `backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java` | `fde4199bef96385ea87a3a0bfd671f170edd2501de1a2dab7adc8481b2757e52` | `c977e453d407aa150404c8f5acecddb6a9dfd63ae27692acb4e057840a3bcc15` | 仅 `isolatedTotal` 的独立 COUNT SQL 构造；仍通过 `scopedCount` 绑定并以 `Long` 读取 |
| `backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightRankingRegressionTest.java` | `78d6e56c7ca07cdbf574ae2437fff94cfbf9fd6b94ce9582e15daf0cbbbc8100` | `454ad390e192749f41c5759ba55b7e4915ce203296ced09def72bc4aabcc25d6` | 仅 `nonRankingQueriesAndTheirLimitsKeepFrozenShape` 方法内新增独立 COUNT 预期字符串，并替换列表中的该一条预期；其余断言不变 |
| `backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightIsolatedCountRegressionTest.java` | 不存在 | `1fcb245eaac857476c7a8bb6c4d854f6bfea649d347079291029bb88fca10c66` | 新增 7 个测试定义、10 个展开场景 |
| `docs/assistant-team/reports/02-quality-I2-R2.md` | 不存在 | 本报告 | 记录本次实施、测试与未验证事项；不自记递归变化的报告 hash |

只读的 `JdbcQualityReadRepositoryTest.java` 保持 `c99ac56054eee8f01d20aa3cac9f9f11be3c3a663d5aaeb5919bb5c956b9dabc`。旧 R1 离线报告保持 raw `21ceb14153dee7c3829046182254e37c0a95033b13d7efd1d278f4af5afc7da5`；16 项冻结依赖复核为 16/16 一致。原工作树此前 1 个已跟踪修改和 30 个未跟踪文件均保留；本轮新增测试与报告两个未跟踪路径，未清理已有文件。未修改旧 I1/R1 清单 expected hash 或失败证据。

## COUNT 实现与保持的语义

在原 `visible_nodes / visible_edges` CTE 后，**仅 COUNT** 追加 `incident_nodes`：从已过滤的有效边分别取 source、target，使用 `UNION` 去重，再将可见节点左连接到该端点集并统计 `i.node_id IS NULL`。该查询无 `LIMIT`，不从已截断的列表或排名推总数。`UNION` 去重意味着自环、反向边、多类型平行边与多库共享节点不会重复扣减同一节点；所有端点仍来自 active 且双端可见的边。

`isolatedWhere` 的声明和字符串未改，后续孤立列表仍使用原 `NOT EXISTS`/source-target OR 查询、按 ID 排序并绑定 `isolatedLimit`。排名、`nodeCte`、`visibleNode`、`documentFilter`、`bind`、`insightScope`、`scopedCount`、其他统计、返回契约均未改。完整 `insight()` 仍发出原顺序的 10 条查询，仅第 9 条 COUNT 文本改变；若该 COUNT 抛错，不执行第 10 条列表，也不返回伪造的 0。

后续真实 SQL 的独立预期：空可见集为 0；N 个可见节点但无可见 active 边为 N；有效自环连接的节点不是孤立；反向/多类型边只改变边数，不重复减少孤立节点；多库共享节点只计一次；仅通向范围外节点的边及 inactive 边不消除孤立性；inactive 节点仍在可见集并按实际有效边判断。R1 静态样本中库 7910、7910∪7911 的孤立总数均为 1，空库 7913 为 0；失败前的项目范围独立 Java oracle 记录为 3。它们是后续总控真实复验的独立预期，须在数据未变化前像下使用，**本轮 mock 没有执行这些 SQL 语义样本**。

## 离线测试与实际轮次

命令均在本工作树 `backend-java`，使用 `-o`、明确类名，无 Spring 上下文或数据库/模型调用。

| 轮次 | 实际命令中的 `-Dtest` | 方法定义 / 展开执行 | 结果 |
|---|---|---:|---|
| 1，旧实现红灯 | `JdbcQualityInsightIsolatedCountRegressionTest` | 7 / 10 | 5 通过、5 失败、0 错误/跳过，BUILD FAILURE；失败在旧 COUNT 缺少端点集、Long 返回仍为 mock 0、预置 COUNT 异常未触发；不是 MySQL 3024 复现 |
| 2，修改后绿灯 | `JdbcQualityInsightIsolatedCountRegressionTest` | 7 / 10 | 10 通过、0 失败/错误/跳过 |
| 3，R1 排名 | `JdbcQualityInsightRankingRegressionTest` | 12 / 15 | 15 通过、0 失败/错误/跳过；含仅一条 COUNT 预期更新的冻结形状测试 |
| 4，原核心与质量域 | `AssistantRuntimeTest,DecisionParserTest,QualityAgentToolProviderTest,QualityEvidenceVerifierTest,JdbcQualityReadRepositoryTest` | 59 / 68 | 68 通过、0 失败/错误/跳过；原源码及断言未改 |
| 5，收紧异常测试后复跑 R2 | `JdbcQualityInsightIsolatedCountRegressionTest` | 7 / 10 | 10 通过、0 失败/错误/跳过；异常从标量查询 `.single()` 抛出并在列表前传播 |

命令格式均为 `.\mvnw.cmd -o '-Dtest=<表中类名>' test`。最终去重覆盖为原 68 + R1 15 + R2 10 = **93 个展开场景、78 个方法定义**；R2 红灯和两次绿灯是同一批 10 个场景的不同运行轮次，不累加为 30 个新用例。红灯原始 XML 未另行归档，后续同名 Surefire XML 已被正常重跑覆盖；本报告保留红灯失败名称/类型和轮次摘要，不称原始 XML 完整留存。

首轮五个失败均为断言失败、无执行错误：`countAggregatesScopedIncidentNodesBeforeOneAntiJoin`、`selectedLibraryUnionIsScopedBeforeCountingAndKeepsLargeBoundIds`、`onlyNinthQueryChangesAndOldIsolatedListKeepsItsOwnLimit` 均因旧 SQL 没有独立 incident-node CTE；`countReadsLongResultIndependentOfTruncatedList` 期望 `3000000001`、实际为 mock 默认 `0`；`countFailurePropagatesBeforeTheListQuery` 预置新 COUNT 路径的异常未在旧 SQL 中触发。它们证明新测试能识别目标旧结构，不是原 MySQL 超时的重演。

R2 隔离测试从真实仓储捕获 SQL 和绑定，验证项目/选库范围先过滤、共享库 `EXISTS`、active 双端可见边、去重端点及反连接、COUNT 无 LIMIT、Long 返回、超大绑定 ID、空结果、拒绝资料/节点范围、COUNT 故障传播、排名与列表独立且原列表 SQL/限制保留。mock 的标量返回和空列表仅验证 Java 映射及调用流程，**不证明 MySQL 语法、优化计划、真实样本计数或性能**；不能以测试转绿认定原 3024 已解决。

## 未验证、风险与停止点

- 仍待总控另获授权后受控汇集、统一回归，并在原 MySQL 8.4.11、原 UUID/端口/库及原 7901 规模与连接 3000ms、socket 4000ms、statement 2s、session SELECT 1500ms 预算下复验。当前没有新 EXPLAIN、真实 COUNT 耗时、完整洞察结果、20 次单线程/20 次四并发 p95 或静态窗口零写入证据。
- `UNION` 的端点去重与反连接是语义等价的离线方案，但 MySQL 实际执行方式和预算内耗时未知。未改的孤立列表仍含旧相关 `NOT EXISTS`/OR；如后续在列表或其他 SQL 再触发 3024，立即停止并另行定界，不能顺手优化、调预算、改索引/迁移或缩样本。
- 多条 SELECT 仍非一致性事务快照；读取期间并发写入及 I2 其他遗留项未验证。离线完成不等于 I2、网站或生产能力验收。没有主项目汇集、提交、push、部署、I3/W2 或其他任务启动。

本轮到此停止，等待总控技术复核；最终验收由项目负责人决定。
