# I2-R3 整批受控汇集与真实整体验收

2026-09-29，依据负责人接受 R3 离线交付/技术复核后的明确授权执行。总控不修改领域算法或测试标准，不另派子任务。

## 汇集及保护

从原 02 接收 3 个既有领域文件和 1 个新增 19 项回归测试，严格匹配已审后像。前像/旧报告/旧失败及 expected hash 保留。
689 个既有主项目文件建立新保护快照；仅三个批准目标可变，其余 686 个内容保持。
R3 overlay 明列旧/新 hash、归档及真实作者：领域补修为原桌面 02；总控负责接收、集成验收测试/脚本/报告。
既有 R1/R2 程序和 I2MySqlSupport 冻结不改，新校验器显式叠加 R3，不忽略旧清单失败。
原 02 报告与 offline-results.json 原样归档。公共核心、契约和其他领域不改。

## 离线门槛

原 154、R1 15、R2 10、R3 19 分组运行，预计 198 个展开场景、170 个方法、22 个类；实际以唯一 suffix XML 核算。
各组只运行一轮。失败/重跑完整留存，不加成新覆盖。离线失败不启动数据库。

## 原环境

MySQL 8.4.11 / UUID 8c6cfcb3-ba49-11f1-bf64-005056c00001 / 127.0.0.1:33078 /
kg_assistant_i2_20260927_r01，英文联接仍指向原 D:/知识图谱-i2-env。
7901 为 10000 节点/30000 active 边/2000 资料/20000 chunk，原 R1 小型数据保留。
启动后与上次 LIST EXPLAIN 收尾的 34 表数据和 DDL 一致才测试；不重新装载、恢复、减量、ANALYZE 或改索引/迁移。
权限原样，loader 锁定；root 仅身份、快照、日志和停机；测试子进程只有 reader。
冻结 JDBC：connect 3000ms/socket 4000ms/statement 2s/session SELECT 1500ms，SSL REQUIRED/UTC，不提升预算。

## 整批顺序与独立预期

1. Matrix：新集成测试捕获当前仓储实际 SQL/绑定；逐条 EXPLAIN FORMAT=JSON 和真实执行，不运行 EXPLAIN ANALYZE。
   10 个组合：PROJECT、7901、7910、7910∪7911、7911、7912、7913 空库、7101 基础库、PROJECT 限制 1/1、并集限制 1/3。
   预计 100 个 SQL/计划检查和 10 次完整 insight；这是一个 JUnit 方法内场景，不冒充 110 个方法。
   独立读取实际节点、边、归属、资料、job 和候选行，在集成测试内按已冻结定义计算预期；不作为生产业务适配层。
   对 Q10 比较真实 ID/名称/类型/degree=0、顺序、截断及完整总数；其他统计、类型、排名也逐项比较。
   类型排序沿用数据库读取的 collation 顺序，计数在测试中独立归并；不增加 fixture、不宣称覆盖所有 collation/类型变体。
2. 原 CountPlan、Plan、Semantics 三组保持源码和断言原样，回归已有成功/失败路径。
3. Base 原 35 项、Budget 原 3 项按冻结标准执行。
4. Performance 使用原 R1 带逐样本记录及增强排名断言的方法，执行既定 20 单线程 + 20 四并发完整组合读取。
   全部 40 条逐样本结果保留；各组排序第 19 项为 p95，阈值 3000ms、warmupDiscarded=0，不删除慢值。
5. LegacyWindow 运行旧 I2ScaleQueryTest 的 scaledFixtureQueryWindowsAndScope 1 项。
   旧类另一性能方法与增强版使用同组合/规模/20+20/3000ms；不重复第二批 40 次，旧断言由增强方法全部包含，不修改旧类、不弱化标准。
   小样本 Budget 自带的 20+20 与规范大样本采样分开报告。

## 失败策略

逐项 SQL 或结果失败保留 SQL/参数/阶段/错误及已取得计划，继续其他独立 Q 检查，均受原预算约束；已确认失败范围不重放完整调用。
Matrix 方法结束汇总全部问题。独立诊断/基础回归继续记录结果，但 Matrix 失败不运行已知失败的完整 Semantics/性能采样，注明未执行原因。
静态窗口数据/DDL/binlog、权限/身份/环境保护或残留查询异常属于安全阻断，停止其他组并收尾。
领域问题整批提交原 02，不现场修改。采样自身失败保留实际样本和缺口，不补选“好的一轮”。

每组前后 34 表数据/DDL/binlog、loader 锁定、无活跃 reader、general log hash；最后正常 SHUTDOWN，无隔离进程/监听，保留数据。

## 命令与停止

`pwsh -NoProfile -File docs/assistant-team/integration/I2/R3/Invoke-R3Verification.ps1 -Phase Offline`
通过后：
`pwsh -NoProfile -File docs/assistant-team/integration/I2/R3/Invoke-R3Verification.ps1 -Phase Real`

结束形成整批结果。成功不代表整个 I2 验收；不推进并发写一致性、物理长原文等剩余项，不进入 I3/W2、API、模型、前端或部署。
模型：原 02 用户已桌面确认 GPT-6 Sol / High，工具独立回读未核实；总控实际模型/推理强度未核实。

