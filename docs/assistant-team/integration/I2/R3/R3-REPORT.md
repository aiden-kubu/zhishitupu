# I2-R3 受控汇集、统一回归与真实整批复验报告

日期：2026-09-29。执行人：总控。最终验收人：项目负责人。

## 1. 本轮结论与停点

已完成获批的整批执行并停止，**整批结果不是全绿**：

- 主项目离线统一回归 **198/198**；本轮各组均一轮，零重跑。
- 原隔离 MySQL 真实测试 **43 通过 / 44 执行**，1 项研究域查询错误，无跳过。
- 质量域 Q01～Q10 在 10 个范围/限额场景的 **100 份真实 EXPLAIN、100 次结果核对、10 次完整 insight 调用**均完成；质量域这些执行未触发业务 SQL 超时。
- 原 7901 规模的采样 **20 次单线程＋20 次四并发**均完成。既定组合（完整 quality.insight 加研究节点检索）p95 分别 **1220ms / 1843ms**，均低于原 3000ms 标准。不是仅 insight 自身的 p95。
- 唯一失败：研究域 `outgoingEdges(AB,7702,10)` 返回 MySQL **3024 / HY000**。它不属于 quality.insight 的 Q01～Q10。没有现场修复、没有提高预算、没有失败重跑或改断言。
- 八个 reader 静态窗口的数据/DDL/binlog 前后相同，loader 始终锁定，窗口结束 reader 活跃查询均为 0；隔离实例已正常停止，数据保留。
- 技术证据支持“本轮固定条件下质量洞察的排名、COUNT、LIST 和整条洞察采样已达到既定标准”，不代表整个 I2 已通过，也不代表网站上线。最终验收仍由负责人确认。

汇总证据：[POSTRUN-SUMMARY.json](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/POSTRUN-SUMMARY.json)。
真实批次 [RUN.json](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/Real-20260929T104824880/RUN.json) 保留 `success=false`，未改写为通过。

## 2. 受控汇集及保护边界

复用已接受的增量技术复核，不重复全文审查旧材料。接收前重新核对前像、原 02 最终文件和新增路径；未整体覆盖工作树。主项目 main / HEAD `86d1e1fcff6b7b492faec3d499837a42246df320` 不变，无提交。

本轮接收原 02 的四份领域文件，只有三份既有文件内容变化：

| 文件 | 接收内容 |
| --- | --- |
| JdbcQualityReadRepository.java | Q10 改为可见边端点归并后反连接；提取 incidentNodes 供 Q09/Q10 使用，Q09 生成 SQL 保持原样 |
| JdbcQualityInsightRankingRegressionTest.java | 只更新获批 Q10 SQL 预期 |
| JdbcQualityInsightIsolatedCountRegressionTest.java | COUNT 捕获定位更精确，并更新获批 Q10 SQL 预期 |
| JdbcQualityInsightPerformanceRegressionTest.java | 新增 6 个测试方法、19 个展开场景 |

主项目 `JdbcQualityReadRepositoryTest.java` 未变。Q01～Q09、共享范围规则、公共契约、01/03/04/05 均未修改。领域文件与原 02 交付逐字节一致；本轮旧/新 raw 与规范化 hash 见 [R3-OVERLAY.json](D:/知识图谱/docs/assistant-team/integration/I2/R3/R3-OVERLAY.json)。规范化规则仍为 UTF-8、CRLF 转 LF、保留结尾、不 trim。

仓储文件旧 hash：
`c977e453d407aa150404c8f5acecddb6a9dfd63ae27692acb4e057840a3bcc15`；
新 hash：
`6a17642e64bbfdb5f0d3bffa13ad9cde4ddbe6384e3b2d5f1a2e592a4db0d740`。

接收前记录的 689 份主项目 Git 可见文件，批准变化 3 份，另外 **686 份内容未变**。I1 的 62 份基线按显式 R1/R2/R3 增量解析；98 份重复依赖、105 个冻结条目一致。历史 expected hash、历史失败记录与原 02 报告均未改写。三份前像和原 02 报告/离线索引另存于本目录 archive。

总控直接新增的是集成所有权内容：`I2QualityInsightWholeTest.java`、R3 验收/保护脚本、增量清单和本报告。不是领域适配器或业务规则替代实现。独立预期只存在于测试中；以基础数据逐行读取、Java 集合计算结果，与真实领域查询交叉核对。

## 3. 主项目统一回归及计数口径

离线批次：`Offline-20260929T104719641`。

| 分组 | 测试方法定义 | 展开场景执行 | 结果 |
| --- | ---: | ---: | --- |
| 原 19 类统一回归 | 145 | 154 | 154 通过 |
| R1 排名 | 12 | 15 | 15 通过 |
| R2 COUNT | 7 | 10 | 10 通过 |
| R3 整体回归 | 6 | 19 | 19 通过 |
| 去重合计（22 类） | **170** | **198** | **198 通过** |

真实批次：`Real-20260929T104824880`。

| 分组 | 方法定义 | 展开场景执行 | 结果 |
| --- | ---: | ---: | --- |
| Matrix：Q01～Q10 整体核对 | 1 | 1 | 通过 |
| CountPlan：保留 R2 COUNT 真值与执行计划 | 1 | 1 | 通过 |
| Plan：保留 R1 排名与 7901 前十 | 1 | 1 | 通过 |
| Semantics：保留 R1 语义场景及项目预期 | 1 | 1 | 通过 |
| Base：原 JDBC 基线＋权限 | 25 | 35 | 34 通过、1 错误 |
| Budget：原超时取消及小样本预算 | 3 | 3 | 通过 |
| Performance：原增强采样组合 | 1 | 1 | 通过 |
| LegacyWindow：原规模窗口/范围 | 1 | 1 | 通过 |
| 去重合计（7 类） | **34** | **44** | **43 通过、1 错误** |

每组执行轮次均为 1，重跑 0，断言 failure=0，真实 error=1，skipped=0。Matrix 内的 100 次查询/10 次整条调用，以及 Performance 内的 40 个采样，都不冒充新增 JUnit 测试定义。此前原 02 的离线红灯/修正轮次、总控既有增量复核 48 项也不重复计入本轮新增覆盖。

本轮没有再执行旧 `I2ScaleQueryTest` 中“只打印汇总、未逐样本存档”的重复采样方法；采用既有且未修改的 R1 增强采样方法执行同一洞察＋检索组合、同规模和同阈值，同时保留逐样本数据和排名断言。旧规模窗口方法仍执行。本轮不声称旧类两个方法都运行，不改旧测试标准。

实际命令（不是新一轮执行授权）：

```powershell
& 'D:/知识图谱/docs/assistant-team/integration/I2/R3/Invoke-R3Verification.ps1' -Phase Offline
& 'D:/知识图谱/docs/assistant-team/integration/I2/R3/Invoke-R3Verification.ps1' -Phase Real
```

选择器和 Maven 离线命令在 [Invoke-R3Tests.ps1](D:/知识图谱/docs/assistant-team/integration/I2/R3/Invoke-R3Tests.ps1)；精确后缀 XML、日志、case 及 hash 在各 `*-RESULT.json`。本轮 **31 份原始 XML 的当前 hash 均与结果索引匹配**。XML 原件保留于本地 target，不把其中可能携带的系统属性整份复制到公开报告。

## 4. 原环境、规模、权限与预算

- MySQL **8.4.11**，UUID **8c6cfcb3-ba49-11f1-bf64-005056c00001**。
- 仅 `127.0.0.1:33078 / kg_assistant_i2_20260927_r01`。
- 英文联接仍指向原 `D:/知识图谱-i2-env`，使用原 datadir、auto.cnf、二进制及配置；四项环境 pin 一致。
- 7901 保持 **10000 节点 / 30000 边 / 2000 资料 / 20000 chunk**；没有减样本。
- 启动后的 34 张表数据/DDL 与 R2 LIST EXPLAIN 收尾快照一致；未重装样本、未重置数据库、未改索引/迁移/统计信息。
- reader 仍为原 `kg_i2_reader@127.0.0.1`、无角色、23 项原 SELECT 表/列权限。13 项原权限测试通过，包含越权读取及写入/DDL 拒绝测试。没有扩大权限。
- loader 全程 locked=Y。管理凭据只由总控用于身份/快照/日志启停与正常停库；测试子进程只获得 reader 凭据，未向原 02 提供账号。
- 原连接 3000ms、socket 4000ms、JDBC statement 2s、session `max_execution_time=1500ms` 不变。7901 组合 p95 标准仍为 3000ms。
- Budget 组原有超时探针保留：JDBC 超时取消实际约 1024ms，server 探针预期返回 3024、约 240ms，两次均验证原连接可继续使用。这些是故意验证取消能力的成功用例，与 Base 中非预期的业务超时严格区分。
- 小样本原 20＋20 预算测试 p95=177ms / 171ms，原上限 1000ms；不把该组与 7901 大规模 40 样本混算。

证据：[IDENTITY](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/Real-20260929T104824880/IDENTITY.json)、[RETAINED-DATA](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/Real-20260929T104824880/RETAINED-DATA.json)、[Budget 日志](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/Real-20260929T104824880/Budget-MAVEN.log)。

## 5. Q01～Q10 真实结果及完整洞察

Matrix 在项目范围、7901、7910、7910+7911、7911、范围外独立库 7912、空库 7913、7101，以及两个限额变体共 10 个场景，执行当前仓储原样 SQL 与绑定参数。没有 SQL 提示改写、EXPLAIN ANALYZE、临时索引或权限绕过。

每个查询先取得真实 EXPLAIN FORMAT=JSON，再执行并对比独立预期，记录 SQL/参数/plan/实际行/预期行。随后调用真实 `quality.insight()` 对比完整返回结构。10 个场景全部无差异。

以下为单次诊断耗时，不是单条 SQL 的 p95，也不能直接加总当成另一轮整条调用：

| 查询 | PROJECT ms | 7901 ms | 结果 |
| --- | ---: | ---: | --- |
| Q01 可见节点总数 | 50 | 23 | 预期一致 |
| Q02 可见 active 边总数 | 79 | 124 | 预期一致 |
| Q03 范围内资料总数 | 18 | 11 | 预期一致 |
| Q04 待复审实体候选 | 18 | 9 | 预期一致 |
| Q05 待复审关系候选 | 15 | 8 | 预期一致 |
| Q06 节点类型总数 | 14 | 19 | 预期一致 |
| Q07 类型分布（limit=50） | 16 | 21 | 预期一致 |
| Q08 度数排名 | 145 | 246 | 预期一致 |
| Q09 孤立节点总数 | 133 | 231 | 预期一致 |
| Q10 孤立节点列表 | 118 | 214 | 预期一致 |

Q10 及完整调用：

| 范围/限额场景 | isolatedTotal | Q10 列表 ID（有序） | 完整 insight 单次 ms |
| --- | ---: | --- | ---: |
| project | 3 | 7703, 7705, 900006 | 470 |
| scale_7901 | 0 | 空 | 871 |
| library_7910 | 1 | 900006 | 93 |
| union_7910_7911 | 1 | 900006 | 91 |
| library_7911 | 0 | 空 | 88 |
| outside_7912 | 1 | 900004 | 82 |
| empty_7913 | 0 | 空 | 89 |
| baseline_7101 | 2 | 7703, 7705 | 92 |
| project_limits_one | 3 | 7703 | 384 |
| union_limits_one_three | 1 | 900006 | 87 |

PROJECT 三个节点为 7703「异常停用节点」、7705「孤立节点」、900006「I2R1 j」，degree 均为 0。保留既定可见节点语义，不因节点 inactive 便擅自排除。7910/并库测试保留共享节点、自环、反向边、多类型边和范围外边的原预期。

7901 和 PROJECT 的排名前十仍为 **100000～100009，degree 均为 6**。空库返回空列表和零总数；limit=1 只裁剪列表，不把总数改成 1；并库共享节点未重复计数。

固定样本并非所有可能数据的穷尽验证：本批类型分布只有 concept，待复审统计为 0；不能据此宣称已真实覆盖 50 种以上类型截断或所有非零复审组合。没有为扩大覆盖擅自写数据。跨事务并发写入一致性不在本轮完成项。

完整计划与行结果：[Matrix-RESULT.json](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/Real-20260929T104824880/Matrix-RESULT.json)。

## 6. Q10 执行计划变化

保留原 R2 LIST [失败形态的 EXPLAIN](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/EXPLAIN-RAW.json)，不覆盖旧证据。

PROJECT 原形态为外层 n=ALL，边 e=ALL，结合 source/target OR 条件反连接，估算 rows_produced_per_join=232927149，并有临时表/filesort。

本轮 PROJECT Q10：

- 外层 n 按 PRIMARY index 顺序读。
- 归并后的 incident_nodes 物化子查询为 dependent=false / cacheable=true。
- 外层对 i 使用自动去重键 eq_ref、not_exists=true，估算每次扫描 1 行。
- 外层 ordering_operation 的 using_filesort=false。
- 物化内部仍有两处边 ALL 扫描，不能谎称“完全没有全表扫描”或“没有临时表”；差异在于不再保留原逐外层节点叠加全边扫描的失败结构。

7901 Q10 同样使用物化端点和外层 eq_ref 反连接，内部可见边读取使用既有索引，未新建索引。上述行数、cost 都是优化器估算，不是实际扫描计数；本轮没有执行 EXPLAIN ANALYZE。实际通过依据是结果相符、原预算下执行完成及后续采样，而非单看计划文本。

## 7. 原规模 20＋20 逐样本与 p95

每个样本先完成整个 `quality.insight(SCALE,10,10)`，检查节点数、边数、孤立总数及前十排名，再执行既定 research.searchNodes 检索并核对 10 个结果。耗时记录覆盖这个组合，不是纯 insight、纯 SQL 或网站接口延迟。

| 模式 | 实际样本 | min ms | max ms | p95 ms | 原上限 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| 单线程 | 20 | 1086 | 1351 | 1220 | 3000 |
| 四并发（总计 20） | 20 | 1717 | 1852 | 1843 | 3000 |

按各组 20 个值排序取第 19 个（ceil(0.95×20)）独立复算，与原测试输出一致。每组 sample ID 1～20 完整、status 全部 ok；未删除慢值、未挑一轮最优结果、warmupDiscarded=0。此前的执行计划/结果诊断已经访问数据，因此这是诊断之后的运行状态，**不声称冷缓存性能**。四并发是同时最多四个只读任务，不是四个写事务，也不是 20 轮×4=80 样本。

所有 40 个原始值：[Performance-RESULT.json](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/Real-20260929T104824880/Performance-RESULT.json)。没有因 Base 红灯删掉已获准的独立诊断；批次最终仍保持失败状态。

## 8. 本轮唯一业务红灯、未达断言与领域归属

失败测试：[I2JdbcBaselineTest.java:95](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2JdbcBaselineTest.java:95)，`researchEdgesKeepDirectionAndBothEndpointScope`，JUnit 方法用时 1.566s。此数包含该方法已执行部分，不当作唯一 SQL 的精确耗时。

失败调用：

- `research.outgoingEdges(AB,7702,10)`。
- AB：libraryIds={7101}，documentIds={7201,7202}，nodeIds 为空。
- nodeId=7702，业务 limit=10，仓储内部 maxRows=11。
- 原期望为空列表；实际在取得结果前返回 MySQL 3024 / HY000。
- 源码位置：[JdbcResearchReadRepository.java:75](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/research/JdbcResearchReadRepository.java:75)，共用 edges 查询执行于第 91～92 行。
- 同方法之前的 `incidentEdges(LIB,7701,10)` 两条边断言已走过；后面的 `outgoingEdges(AB,7701,10)` 方向核对及 `incidentEdges(AB,7701,10)` 精确范围断言因异常未到达，不能视为已通过。

完整 SQL 和异常保存在 [Base-RESULT.json](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/Real-20260929T104824880/Base-RESULT.json)、[Base 日志](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/Real-20260929T104824880/Base-MAVEN.log) 及原始 XML。本轮只定位调用和归属；没有额外跑该查询的 EXPLAIN、没有重跑证明“偶发”、没有推断根因已确定。

**归属是原 03 research，不是原 02 quality。** 03 源码及原集成测试 hash 未变；这不足以推断为什么此时超时，也没有证据认定是 R3 改写质量 SQL 导致。将它描述为 Q11 或让 02 越界修研究领域均不合适。

按照整批处理原则，本报告及证据可整体人工交给原 02了解本轮质量验证结论和跨域阻断，但不授权 02 修改 03。是否向原 03 派发定向检查需负责人决定；本轮未准备新修复实现、未启动或派发任何子任务。

## 9. 零业务写入、收尾与证据完整性

每个组前后保存 34 张表 COUNT、稳定行内容 SHA-256、SHOW CREATE TABLE hash、binlog 文件/位置、loader 状态，并记录 general log hash。八个 `*-ZERO-WRITE.json` 均证明：

- dataAndSchemaEqual=true；
- binlogPositionEqual=true（仅同一运行的静态窗口对比，启动轮换不冒充数据写入）；
- loaderLockedBefore/After=Y；
- activeReaderQueriesAfter=0。

Base 的 testSucceeded=false 如实保留，但其零写入检查仍成立。权限负向测试发起被拒绝的写入/DDL 探针，不代表产生了实际业务写入。

正常 SHUTDOWN 成功；原隔离进程和 33078 监听残留均为空：[SHUTDOWN.json](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/Real-20260929T104824880/SHUTDOWN.json)。未触碰其他 MySQL 服务，未删除原目录、数据或日志。

归档时发现一项总控证据转录缺口：本地 JVM file.encoding=GBK，Maven 控制台到 UTF-8 日志链路把四种中文节点名的 45 个字符串字段转录成乱码。**原 Surefire UTF-8 XML 保留正确中文**，Matrix system-out 没有 U+FFFD。对 221 个记录逐叶比较确认结构、数字、ID、耗时、SQL/计划均一致，只上述姓名字段不同。见 [XML-TRANSCRIPTION-CHECK.json](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/XML-TRANSCRIPTION-CHECK.json)。

没有覆盖日志、改写 RESULT、修改已封包脚本或重跑测试；中文名称以该原始 XML 和补充差异表为准。后续若继续使用此集成运行器，应由总控在新增量中修正输出编码；这不是领域修复任务。记录来源保留了原始 hash。

[FINAL-INDEX.json](D:/知识图谱/docs/assistant-team/integration/I2/R3/evidence/FINAL-INDEX.json) 收录本批文件、31 份本地 XML、环境 general log 的路径/大小/hash。general log 为本地累计日志，窗口 hash 表示当时截面；未来追加后需保留本批截面证据，不把旧 hash 改成新值。

## 10. 实际职责、模型和未完成事项

| 主体 | 本轮实际工作 |
| --- | --- |
| 原桌面 02 | 此前完成并交付 R3 质量域四文件补修及离线 68＋15＋10＋19；本总控执行轮中未启动 02 |
| 总控 | 已受理的增量复核、接收精确补丁、主项目统一回归、独立真实集成测试与脚本、全部本轮 MySQL 验证、证据整理及停库 |
| 01/03/04/05 | 本轮未开工、未修改、未承担真实复验 |
| 负责人 | 已授权本轮范围；最终是否验收待负责人决定 |

领域 R3 源码/领域测试实际修改者：原 02；总控仅受控接收。历史 R1 排名 SQL/15 项测试实际作者仍为此前总控，不改写历史。新集成 Java/PowerShell/报告实际作者为本轮总控。

原 02 模型要求 GPT-6 Sol / High，负责人表示已在桌面确认；工具独立回读未核实，**实际模型未核实，实际推理强度未核实**。总控实际模型/推理档位本轮亦未独立核实，不能以访问工作树或创建文件推断使用了哪一个模型。

本轮不能对外宣称：

- I2 已整体验收或跨领域真实性能全绿；
- 新发现的研究边查询已修复、已查明执行计划根因；
- 并发写入一致性、物理长原文链路及 I2 其他遗留项已完成；
- 所有可能范围/数据形态都获得真实数据库穷尽验证；
- 真实模型、生产宿主、API、鉴权、前端、正式测评或网站上线；
- W2 写操作、I3 或任何后续阶段已经获准启动。

**停点：保留当前受控 R3 基线、失败证据和原实例数据，全部执行停止。等待负责人对本批结果及跨领域阻断的后续安排作出决定。**

