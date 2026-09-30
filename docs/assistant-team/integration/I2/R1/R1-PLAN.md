# I2-R1 受控汇集与真实复验执行范围

日期：2026-09-28。本轮获准受控汇集和原隔离实例复验，不代表 I2 验收或后续阶段授权。

前置核对：主项目既有 577 个 Git 可见文件纳入本轮保护；原保护 499、I1 文件 62、重复依赖 98、冻结记录 105 均符合清单。仅原 02 已批准的目标实现 hash 为新版本。

## 允许文件

- 主项目唯一既有源码变化：`backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java` 的排名 SQL 文本块。
- 从 02 接收新增 `backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightRankingRegressionTest.java`。
- 新增 `backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2QualityInsightRankingTest.java`。
- 本目录新增：`R1-PROTECTION-BEFORE.json`、`R1-OVERLAY.json`、`R1-PACKAGE.json`、本计划、`Verify-R1Baseline.ps1`、`Invoke-R1Tests.ps1`、`Invoke-R1Verification.ps1`、`R1-FIXTURE.sql`、`R1-REPORT.md`。
- 本目录归档：`archive/JdbcQualityReadRepository.java.before.txt`、`archive/02-quality-I2-R1.md`。
- `evidence/<唯一运行编号>/` 仅新增运行身份、逐类/逐方法测试结果、日志、排名执行计划、语义结果、逐次采样、装载前后及 reader 窗口快照、权限/零写入、收尾记录。旧文件名不得覆盖。

## 执行与停止

先单独运行原 19 类 154 项，再单独运行新增 15 项。真实验证顺序为：核对原实例 → 核对原 7901 规模与权限 → 独立事务装载 7910～7913 合成样本 → reader EXPLAIN/直接排名 → 小型完整洞察语义 → 原基础回归 → 原预算回归 → 20 次单线程及 20 次四并发完整洞察+检索 → 原规模测试回归。每组独立 reader 静态窗口，失败后不进入下一组。

新增真实测试只复用冻结 I2MySqlSupport 的连接和超时配置。EXPLAIN/直接排名使用从当前真实仓储捕获的 SQL，不另写替代生产查询。语义/性能再调用完整仓储方法，避免只验证一条独立快 SQL。

如完整洞察在另一条 SQL 超时，立即停止并记录原异常，不改第二条 SQL、不调预算。单线程首次失败即停止；并发失败取消尚未开始的采样并回收已在途读取，记录全部已尝试结果。每轮保留原顺序、慢值和第 19/20 顺位 p95。

root 仅用于原隔离实例管理和证据；loader 仅用于新合成样本装载、随后锁定；测试子进程仅有 reader 凭据。使用原 DPAPI 凭据，不采用聊天中未确认归属的另一个密码。仅端口 33078、版本 8.4.11、UUID 8c6cfcb3-ba49-11f1-bf64-005056c00001、库 kg_assistant_i2_20260927_r01。

不重载 7901，不做 ANALYZE/索引/迁移/超时调整。合成样本保留供复核，不擅自销毁。账号维护及装载在静态窗口外，不宣称全阶段无技术写入。每组窗口内核对 34 表数据/DDL、binlog、loader 锁定及无残留 reader 查询；结束正常 SHUTDOWN 并核对原端口/进程。停止后等待负责人，不继续 I2 遗留项或 I3/W2。
