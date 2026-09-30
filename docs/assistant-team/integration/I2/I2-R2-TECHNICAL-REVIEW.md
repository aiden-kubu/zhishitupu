# I2-R2 原 02 离线交付技术复核

日期：2026-09-28。复核者：总控。结论：**本次四文件离线补修未发现需要退回原 02 的阻断问题，可提交负责人确认；尚未汇集，尚未真实 MySQL 复验，不能认定 I2 验收通过。**

## 1. 本次实际做了什么

- 原 02：修改孤立节点总数 COUNT、调整 R1 测试中的一个 COUNT 预期、新增定向测试和 [I2-R2 报告](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/reports/02-quality-I2-R2.md)，并执行其报告中的离线测试。此前 R1 排名补丁的作者仍为总控，不倒改历史归属。
- 总控本次：仅检查本轮差异及其影响、新测试全文、新报告和当次 JUnit 结果；对受影响的 R1 15 项、R2 10 项独立复跑一次，形成本文。不改任何领域代码或领域测试，不接收补丁到主项目。
- 复用结论：W1/I1/R1 未变化的契约、已确认接手结论、原 68 项回归及冻结依赖结论，不重新全文审查。
- 原 02 模型记录：用户已在桌面确认 GPT-6 Sol / High；工具独立回读未核实。本次总控实际模型、推理强度未核实。不能依据目录或报告名称推断实际模型。
- 本次真实环境验证者：无。未启动或连接数据库、读取凭据、调用真实业务模型或推进 I3/W2；没有启动其他任务。总控在原 02 工作树运行的是复核测试，不是代写领域实现。

## 2. 差异和范围

以此前接令准备的 436 个原 02 Git 可见文件前像为依据，检测到且仅检测到两个既有文件改变、两个新增文件；其余 434 个 raw hash 不变。没有对未变化文件再作全文质量审查。原 02 HEAD 保持 `bcd3b60ea4e6e18daad53f31787028a0f69bc6cf`。

| 文件 | 修改前 → 修改后（规范化 SHA-256；当前 Java raw 相同） | 复核结论 |
|---|---|---|
| `JdbcQualityReadRepository.java` | `fde4199bef96385ea87a3a0bfd671f170edd2501de1a2dab7adc8481b2757e52` → `c977e453d407aa150404c8f5acecddb6a9dfd63ae27692acb4e057840a3bcc15` | 仅将 COUNT 构造改成独立端点集合反连接；未改列表、排名或共享方法 |
| `JdbcQualityInsightRankingRegressionTest.java` | `78d6e56c7ca07cdbf574ae2437fff94cfbf9fd6b94ce9582e15daf0cbbbc8100` → `454ad390e192749f41c5759ba55b7e4915ce203296ced09def72bc4aabcc25d6` | 仅在获准方法内构造 COUNT 预期并替换该一条；其余方法/断言不变 |
| `JdbcQualityInsightIsolatedCountRegressionTest.java` | 不存在 → `1fcb245eaac857476c7a8bb6c4d854f6bfea649d347079291029bb88fca10c66` | 7 个方法定义、10 个展开场景，属于 SQL 结构/绑定/Long 读取/异常流程验证 |
| `docs/assistant-team/reports/02-quality-I2-R2.md` | 不存在 → raw `d7d7050bfeb0b50395bbe5e7f72e3d88646ee52258da0eff8fc2d78ae8019855` | 归属、轮次、限制和停止点表达清楚 |

前三份 Java 文件分别位于原 02 的 `backend-java/src/main/java/com/knowledgegraph/assistant/quality/` 与 `backend-java/src/test/java/com/knowledgegraph/assistant/quality/`。规范化为 UTF-8、CRLF 转 LF、保留结尾换行、不 trim。

原 `JdbcQualityReadRepositoryTest.java` 与 R1 报告前像未变；16 项冻结依赖不在变化集合内，复用先前一致结论。主项目相关源码及排名测试仍为 R1 的 `fde419…`、`78d6e5…`，新增 R2 测试尚不存在于主项目，**没有汇集**。

## 3. COUNT 语义与受影响断言

静态推理：`incident_nodes` 的 source、target 都来自原 active 且双端可见的 `visible_edges`；`UNION` 得到去重端点集合。可见节点左连接后保留 `i.node_id IS NULL`，对应“没有可见有效边相接的节点”。因此自环足以使节点非孤立，反向/多类型边不会重复减少孤立节点，范围外及 inactive 边不改变当前孤立性；节点可见性和共享库去重沿用未改的公共过滤。

此为关系逻辑静态审查，不是 MySQL 执行证明。

实质变化仅第 9 条孤立 COUNT，仍经原 `scopedCount`/绑定和 Long 读取。其余 9 条 SQL、调用次序、`isolatedWhere`、列表按 ID 排序/isolatedLimit、排名/排序/topLimit 均未变。COUNT 不带 LIMIT，不读取截断列表计算总数，也没有新增接口或状态语义。

旧排名回归保留其他 8 条非排名 SQL 的精确预期、10 次查询数、参数和上限断言，只更新获准 COUNT 条目，没有删掉范围保护或使测试自动取生产字符串作为预期。新增测试对真实仓储捕获的 SQL 和参数作检查，Long 结果及异常从 mock 标量读取路径返回/抛出；没有引入假 SQL 引擎或以手工 mock 行宣称数据库计数正确。

## 4. 原 02 测试证据与总控定向复跑

### 4.1 原 02 交付的当次 XML

复跑前读取指定七类 Surefire XML，均为零失败、零错误、零跳过，合计 **78 个方法定义、93 个去重展开场景**。按原 68、R1 15、R2 10 分组；定义数通过展开名称去重核对。

| 测试类 | 方法定义 / 展开场景 | 原 02 最后绿灯 XML raw SHA-256 |
|---|---:|---|
| AssistantRuntimeTest | 28 / 28 | `252a54cb7d0730a9bf58a9d1cd6a234cc82eaed4103bb866b8a674e4c93de32c` |
| DecisionParserTest | 6 / 15 | `c29daed44e7e9780e461d327b32f792551ffb38b92ecc9b8643796d555302b89` |
| QualityAgentToolProviderTest | 15 / 15 | `fce7afb89e1f0170aea30a459df0bb149681588d60bffe755712a949b05bab5f` |
| QualityEvidenceVerifierTest | 6 / 6 | `c96ffe95a09c3c7875d1df237b76fae5f37f70525abc78b0661792f044697857` |
| JdbcQualityReadRepositoryTest | 4 / 4 | `25f27e48f8958708ad30fb8a428fb637dd0febba0dd8de2f3815474af0b9f887` |
| JdbcQualityInsightRankingRegressionTest | 12 / 15 | `4b7fac906014aca50140d7ab14283deed4ef53d95d0caccde86905b74acd86ef` |
| JdbcQualityInsightIsolatedCountRegressionTest | 7 / 10 | `d3aa2f92f67787231ecb0b316913fd6977f9e9b5e175b1761af7d7e8550ee887` |

原 02 报告中 R2 首轮红灯的 10 项/5 失败和后续多轮绿灯，只是相同场景的重复执行，不累计为新增覆盖。首轮红灯原 XML 已被其后续运行覆盖，本次不能独立回读该原始 XML；复用报告中如实保留的失败名称/类型，不声称已独立重现红灯或 MySQL 3024。

### 4.2 总控仅复跑受影响的 25 项

理由：本轮改变了 COUNT 实现和一条 R1 测试预期，并新增 R2 测试；需要独立验证这两个受影响测试类。原 68 项源码/断言未变且其当次 XML 已核对，不重复运行。

工作目录：`D:/知识图谱-worktrees/assistant-02-quality/backend-java`。

```powershell
.\mvnw.cmd -o '-Dtest=JdbcQualityInsightRankingRegressionTest,JdbcQualityInsightIsolatedCountRegressionTest' '-Dsurefire.reportNameSuffix=I2R2-CONTROLLER-20260928-01' test
```

一次运行，2026-09-28 23:54:45 +08:00 结束，退出码 0 / BUILD SUCCESS；**25/25 通过，0 失败、0 错误、0 跳过**。这 25 项与原 02 的 93 项重叠，不增加去重覆盖数量。

使用独立报告后缀，不覆盖原 02 的同名 XML。复跑后再次检查原七份 XML，raw hash 全部未变；本次四文件和被抽核的只读文件也未变。Maven 仅更新忽略目录中的正常构建/复核产物，不修改领域源码。

| 总控复跑测试类 | 展开场景 | 新 XML raw SHA-256 |
|---|---:|---|
| JdbcQualityInsightRankingRegressionTest | 15 | `20f117413c91232c17b69fc2d14dadb3f42b1363e8cd86360ebd1347dca7702a` |
| JdbcQualityInsightIsolatedCountRegressionTest | 10 | `3fbf870d2c55c9325ce68acdbe85e488ef31d5022eec0b2a39df68bee65ed7e7` |

复核生成文件位于原 02 的 `backend-java/target/surefire-reports/`：
- [R1 排名复核 XML](D:/知识图谱-worktrees/assistant-02-quality/backend-java/target/surefire-reports/TEST-com.knowledgegraph.assistant.quality.JdbcQualityInsightRankingRegressionTest-I2R2-CONTROLLER-20260928-01.xml)
- [R2 COUNT 复核 XML](D:/知识图谱-worktrees/assistant-02-quality/backend-java/target/surefire-reports/TEST-com.knowledgegraph.assistant.quality.JdbcQualityInsightIsolatedCountRegressionTest-I2R2-CONTROLLER-20260928-01.xml)

这里只引用计数、用例结果和 hash；没有将包含机器环境属性的完整 XML 复制进主项目文档。

## 5. 未验证、风险和交接停点

1. 新 COUNT 尚未连接真实 MySQL，没有新 EXPLAIN、真实样本执行、耗时或 p95；离线语义等价推理不保证优化器会在原预算内完成。
2. 未改的孤立列表仍有旧相关 NOT EXISTS/OR 结构。它是否成为下一处瓶颈未知；后续若超时，仍须立即停止，不能扩大到第二条 SQL。
3. 20 次单线程/20 次四并发完整采样、当前版本的零业务写入窗口和真实环境收尾，本轮均未执行；不能沿用 R1 历史证据宣称当前补丁已复验。
4. I2 的并发写入一致性、物理长原文链路等原有遗留项继续保留。没有网站上线、生产接线、API/前端或 W2 结论。
5. 下一步由负责人先确认本次离线交付和技术复核范围，再单独批准总控受控汇集与原隔离环境复验。届时同时管理两个既有文件的 R2 增量、治理文档增量及新增测试，保留 R1 历史 hash，不直接改旧 expected hash。
6. 后续完整主项目回归应分别列原 154、R1 15、R2 10，预计去重为 179；这是汇集后待执行计划，**不是本轮已经运行 179 项**。

本次没有发现必须退回原 02 的离线补修问题。原 02 保持待命；总控到此停止，不自动汇集、连接数据库或进入后续阶段。最终是否接受由项目负责人决定。

