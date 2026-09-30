# I2-R2 受控汇集与原隔离 MySQL 复验

日期：2026-09-29。依据负责人本轮明确授权执行；不是原 02 的新领域任务。

## 责任和增量边界

原 02 实际编写 R2 的 COUNT 补丁、更新一条旧测试 SQL 预期、新增 10 项领域回归及离线报告。
总控只接收这三份 Java 文件，归档原 02 报告和两份 R1 前像；新增集成测试、执行脚本及证据。
R1 排名实现/15 项测试的历史作者仍为此前总控。模型：原 02 用户已在桌面确认 GPT-6 Sol / High，工具独立回读未核实；总控实际模型/推理强度未核实。
无新子任务，无领域开发；不改变原 154 项及 R1/R2 已验收断言。

以 R2-PROTECTION-BEFORE.json 的 617 个文件保护当下主项目。
两份领域旧/新 hash、接收位置和归档见 R2-OVERLAY.json。
此前 R1 源码变化及 COORDINATION/REGISTRY 治理文档变化单列在保护快照 previousR1Differences；治理新文档也已纳入本轮 617 文件基线。旧 R1/I1 expected hash 不改，旧证据不改。

## 执行次序和计数

1. 全部前像及 62 文件基线、98 重复副本、105 冻结依赖、原环境包校验。
2. Offline 分组运行 Original 154、Ranking 15、IsolatedCount 10；使用唯一 JUnit reportNameSuffix，不覆盖历史 XML。
3. Real 启动原实例、确认身份/权限/规模/数据 DDL 与最后 R1 AFTER 快照一致；不装载、重置样本或解锁 loader。
4. CountPlan：执行当前仓储实际第 9 条 COUNT；独立读取节点/库归属/active 边在测试内计算预期，七种范围逐项比较；7901/项目范围真实 EXPLAIN。
5. Plan：原 R1 排名 EXPLAIN/前十；Semantics：原七小型完整洞察及项目范围独立 oracle；全部使用原 R1 测试不改标准。
6. 仅前组成功才运行既定 Base/Budget、20 单线程及 20 四并发完整结果（排序第 19 项为 p95，原 3000ms 阈值、零剔除）、LegacyScale。
7. 任一新 SQL 阻断立即终止后续组，保留失败结果；仅继续必要零写入快照/无残留检查/正常停机，不修复第二条 SQL。

独立 JUnit 定义、展开场景、方法内数据场景和性能采样分别记录，不相加冒充覆盖。每组运行一轮；若有执行故障或重跑必须单列。

## 原环境与预算

MySQL 8.4.11 / UUID 8c6cfcb3-ba49-11f1-bf64-005056c00001 / 127.0.0.1:33078 /
kg_assistant_i2_20260927_r01；D:/kg-i2-env 联接仍指向 D:/知识图谱-i2-env。
7901 的 10000 节点、30000 边、2000 资料、20000 chunk 和原 R1 合成样本保留。
连接 3000ms、socket 4000ms、statement 2s、session SELECT 1500ms 原样继承冻结 I2MySqlSupport。
只使用既有 DPAPI；root 仅实例身份/快照/日志开关及正常 SHUTDOWN，测试子进程仅 reader，loader 始终锁定。
每组前后 34 表数据/DDL/binlog 比较、reader 活跃查询为零和 general log hash。启动前后 binlog 轮转不能与静态窗口混淆。
禁止写业务数据/改索引/迁移/真实模型/API/前端/部署/W2，不自动推进剩余 I2 或 I3。

## 命令

在主项目使用：
`pwsh -NoProfile -File docs/assistant-team/integration/I2/R2/Invoke-R2Verification.ps1 -Phase Offline`
通过后才执行：
`pwsh -NoProfile -File docs/assistant-team/integration/I2/R2/Invoke-R2Verification.ps1 -Phase Real`

最终由负责人验收。即使全部通过也只解除本轮 COUNT 性能阻断，不代表 I2 所有剩余项目已完成。
