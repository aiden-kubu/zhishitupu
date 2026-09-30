# 孤立节点 LIST：一次真实 EXPLAIN 诊断

日期：2026-09-29。执行者：总控。状态：仅诊断完成，停止待项目负责人决定；未修复，未派发原 02。

## 结论

在原隔离 MySQL 中，以原 reader、PROJECT 范围、isolatedLimit=10，对上次超时的原 SQL 执行了一次 `EXPLAIN FORMAT=JSON`。
计划显示：优化器将 NOT EXISTS 表现为嵌套循环中的反连接（`not_exists=true`），但外层节点和关联边均采用全扫描，端点 OR 条件仍留在边的过滤条件中，并出现临时表和 filesort。

**这一计划形态支持“孤立 LIST 存在大量节点—边匹配工作”的诊断，与既有 3024 超时相符。**本次未执行实际 LIST、未运行 EXPLAIN ANALYZE，因此不能把估算行数当作实际扫描/循环次数，也不能把计划生成耗时当作 LIST 性能。

## 执行计划要点

| 位置/指标 | 真实 EXPLAIN 输出 | 解读边界 |
|---|---|---|
| 外层可见节点 n | access_type=ALL；rows_examined_per_scan=8463 | 优化器估算；此前静态项目实际节点数为 10011 |
| 关联边 e | access_type=ALL；rows_examined_per_scan=27523 | 全扫描访问；不是实际扫描次数统计 |
| 边的候选索引 | possible_keys 包含 uk_edges_source_target_relation、idx_edges_target；没有选中的 key | 不能把 possible_keys 当作已经使用索引 |
| 节点与边关联规模 | rows_produced_per_join=232927149 | 约 2.33 亿估算行，不是实测扫描了 2.33 亿行 |
| 反连接与过滤 | not_exists=true；active 且 source_node_id=n.id OR target_node_id=n.id | 原 SQL 条件保留；计划不是简单显示 dependent=true 的原始子查询 |
| 排序操作 | using_temporary_table=true、using_filesort=true | 计划包含临时表与排序；不能单凭名称断言发生了磁盘落盘 |
| 两端节点存在性关联 | 两个 PRIMARY / eq_ref | 局部主键查找不消除前述边表 ALL 访问 |
| query_cost | 70775092.50 | 优化器代价估计，不是毫秒 |
| 本次 EXPLAIN 生成耗时 | 3ms | 只生成计划，绝非 LIST 在 3ms 内执行完成 |

[完整结构化计划](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/EXPLAIN.json)；
[原始 MySQL JSON 文本及其 hash](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/EXPLAIN-RAW.json)。

未据此制定或实施新算法，没有改 SQL、加索引、调整优化器/统计信息或提高预算。

## 与原超时证据对应

- 历史运行：Real-20260929T001613649，`I2QualityInsightRankingTest.realSemanticMatrixAndProjectOracle`。
- 7 个小型完整洞察场景通过后，PROJECT 完整 insight 的第 10 条孤立 LIST 查询失败。
- 原错误：SQL state HY000，error code 3024，Query execution was interrupted, maximum statement execution time exceeded。
- 当时孤立总数已走过新 COUNT；失败的是后续 LIST，不是再次将 COUNT 超时归因给新补丁。
- 原 PROJECT 独立预期：10011 个节点、30009 条可见 active 边、3 个孤立节点；这些引用既有结果，本轮不重新运行。
- [原 SQL、JUnit 错误及场景记录](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Semantics-RESULT.json)；
  [原真实运行失败结论](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/RUN.json)；
  [原失败窗口零写入](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Semantics-ZERO-WRITE.json)。

诊断 SQL 直接从已核对 hash 的历史错误记录提取，保留 JDBC 占位符和全部文本，仅加 `EXPLAIN FORMAT=JSON` 前缀；使用 PreparedStatement 对第一个参数绑定 int 10。没有拼改 WHERE/ORDER BY/LIMIT、添加 hint 或重写 CTE。
[原 SQL、实际 EXPLAIN SQL、绑定与预算记录](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/TARGET.json)。

## 环境、权限和预算保持

- MySQL 8.4.11；UUID 8c6cfcb3-ba49-11f1-bf64-005056c00001；127.0.0.1:33078。
- 库 kg_assistant_i2_20260927_r01；原 D:/kg-i2-env 联接与 datadir，未重初始化或改用其他实例。
- 7901 原规模仍为 10000 节点、30000 active 边、2000 资料、20000 chunk；R1 合成样本保留。
- 原 JDBC 驱动 mysql-connector-j 9.7.0，沿用上次测试 XML 中的 JDK 17.0.12 与驱动路径；没有安装依赖。
- 原 JDBC URL：connectTimeout=3000ms、socketTimeout=4000ms、sslMode=REQUIRED、UTC；PreparedStatement.queryTimeout=2s，session max_execution_time=1500ms。
- EXPLAIN 使用 kg_i2_reader@127.0.0.1；同连接核对 UUID、版本、端口、库、用户和 session 预算。loader 始终锁定。
- root 只做原有身份/快照和正常 SHUTDOWN，未改变 general_log 设置、账号权限、索引、迁移、统计信息或业务数据。

[实例、权限、规模](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/IDENTITY.json)；
[与 R2 最后快照的 34 表数据/DDL 一致](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/RETAINED-DATA.json)。

## 零写入、停机和代码保护

- 诊断前后 34 表行数、数据 hash、DDL hash 相同，binlog 位置相同。
- 无残留活跃 reader 查询，loader 前后均为 Y。
- 正常 SHUTDOWN：graceful=true，无残留隔离进程或 33078 监听，数据保留。
- 原源码/测试/集成脚本及配置包保护校验通过；没有修改项目代码、SQL、索引或样本，也没有编译或重跑回归/性能采样。
- 本次只新增诊断证据与本报告，旧 R2 报告、旧超时结果和 expected hash 均未改。

[零写入证据](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/ZERO-WRITE.json) /
[正常停机](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/SHUTDOWN.json) /
[文件保护](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/BASELINE-AFTER.json) /
[本次诊断完成记录](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/RUN.json)。

## 诊断工具异常留痕与次数

第一次临时诊断工具使用 JShell local 执行模式，出现 ClassNotFoundException / NoClassDefFoundError；失败发生在加载驱动时，未建立 reader 连接，未发送 EXPLAIN。原实例随后正常停止，异常与零写入/收尾全部保留：
[首次工具输出](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003614228/DIAGNOSTIC-OUTPUT.json) /
[首次停机](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003614228/SHUTDOWN.json)。

总控仅在内存中的一次性诊断命令移除 local 执行模式，先离线验证驱动与 java.sql 模块加载成功；没有创建/修改 Java 或 PowerShell 源文件，没有改变数据库环境。之后重新启动同一实例，以原预算完成唯一一次实际 EXPLAIN。

因此：诊断工具进程尝试 2 次，第一轮实际 EXPLAIN 为 0 次，第二轮为 1 次；实际 LIST 执行 0 次，EXPLAIN ANALYZE 0 次。失败未隐藏，不称两轮都成功。

## 责任与等待决定

领域代码作者/维护者不变：本轮原 02 未执行任何操作，总控只做获准诊断与归档。
领域模型要求 GPT-6 Sol / High；用户此前已在桌面确认，工具独立回读未核实，本轮未启动领域任务。总控实际模型/推理强度未核实。

该诊断不意味着 SQL 已修复、完整洞察性能已通过或 I2 已验收。当前停止，等待负责人决定是否将孤立 LIST 问题交给原 02；没有创建补修任务单、派发任务或继续剩余 I2/I3。

