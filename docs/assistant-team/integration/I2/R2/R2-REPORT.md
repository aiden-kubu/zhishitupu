# I2-R2 受控汇集、主项目回归与真实复验报告

日期：2026-09-29；执行者：总控。最终验收：待项目负责人确认。

## 1. 结论与停止位置

受控汇集完成，主项目 179/179 个去重展开场景通过。新孤立节点 COUNT 的七个真实数据场景和两个真实 EXPLAIN 验证通过，原排名验证通过。

**本轮真实复验整体未通过：未修改的项目范围孤立节点列表查询触发 MySQL 3024 / HY000。已立即停止后续测试，未修复列表或其他 SQL。**
零业务写入及正常停机证据已核对；不将此结果表述成完整洞察性能通过、I2 通过或网站上线。

- [真实 RUN：success=false](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/RUN.json)
- [失败用例与实际 SQL](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Semantics-RESULT.json)
- [正常停机](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/SHUTDOWN.json)

## 2. 复用结论与本轮范围

复用已获负责人接受的 [R2 离线技术复核](D:/知识图谱/docs/assistant-team/integration/I2/I2-R2-TECHNICAL-REVIEW.md)，不重复全文审查旧任务/领域报告。

因本轮是主项目受控汇集，重新核对两份主项目文件前像、三份原 02 交付 hash；记录当下 617 个 Git 可见文件的保护快照，并核对受影响的基线、重复副本和冻结依赖。该完整保护检查是汇集边界检查，不是重新进行领域全文审查。

历史 R1 清单、I1 expected hash 和真实失败证据全部原样保留。两份批准治理文档（COORDINATION、REGISTRY）及此前新增规则/交接/任务/复核文档已纳入当下保护基线，旧 R1 前像差异单列，不修改旧校验器预期或忽略校验失败。

## 3. 实际接收文件与保护

| 主项目领域文件 | R1 前像规范化 SHA-256 | 原 02 的 R2 接收版本 |
|---|---|---|
| JdbcQualityReadRepository.java | fde4199bef96385ea87a3a0bfd671f170edd2501de1a2dab7adc8481b2757e52 | c977e453d407aa150404c8f5acecddb6a9dfd63ae27692acb4e057840a3bcc15 |
| JdbcQualityInsightRankingRegressionTest.java | 78d6e56c7ca07cdbf574ae2437fff94cfbf9fd6b94ce9582e15daf0cbbbc8100 | 454ad390e192749f41c5759ba55b7e4915ce203296ced09def72bc4aabcc25d6 |
| JdbcQualityInsightIsolatedCountRegressionTest.java | 新增 | 1fcb245eaac857476c7a8bb6c4d854f6bfea649d347079291029bb88fca10c66 |

接收后的 raw/规范化 hash 均与原 02 完全一致。仅 COUNT 实现及一条对应 SQL 预期演进，排名、isolatedWhere、孤立列表、共享过滤、契约保持不变。
归档原 02 报告 raw hash：d7d7050bfeb0b50395bbe5e7f72e3d88646ee52258da0eff8fc2d78ae8019855。两份 R1 前像也已归档。

本轮前后保护结果：

- 原有 617 个文件中只发生上述两个批准变化，其余 615 个 raw hash 未变。
- I1 的 62 份源码/测试按显式 R1/R2 增量核对通过；98 份重复副本和四份冻结清单累计 105 项均一致。
- 主项目 main / HEAD 86d1e1fcff6b7b492faec3d499837a42246df320 未变；无提交、清理或整体覆盖工作树。
- 原 02 工作树仅作接收来源和 hash 校验；本轮未在其中开发或运行测试。
- [增量记录](D:/知识图谱/docs/assistant-team/integration/I2/R2/R2-OVERLAY.json)、[操作前保护](D:/知识图谱/docs/assistant-team/integration/I2/R2/R2-PROTECTION-BEFORE.json)、[执行后保护](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/BASELINE-AFTER.json)、[包文件 hash](D:/知识图谱/docs/assistant-team/integration/I2/R2/R2-PACKAGE.json)。

总控新增的实现仅为集成验收用途：主项目 assistant/i2/I2QualityIsolatedCountTest.java、R2 目录下三个校验/运行脚本、计划/增量清单/归档/本报告及证据；不是新的领域适配或业务逻辑。

## 4. 主项目统一回归

| 分组 | 方法定义数 | 展开场景数 | 本轮结果 | 执行/重跑 |
|---|---:|---:|---|---|
| 原公共核心 + 01～05（19 类） | 145 | 154 | 154 通过，0 失败/错误/跳过 | 1 / 0 |
| R1 排名（1 类） | 12 | 15 | 15 通过，0 失败/错误/跳过 | 1 / 0 |
| R2 COUNT（1 类） | 7 | 10 | 10 通过，0 失败/错误/跳过 | 1 / 0 |
| 合计（21 类） | 164 | 179 | 179/179 | 各组一轮 |

三组测试定义互不重叠；原 02 的历史 93 项、总控上轮的 25 项及旧红灯不累加成本轮新增覆盖。
使用唯一 surefire.reportNameSuffix；原始 XML 留在 target 中，路径/hash 和脱敏结果写入独立 RESULT，不声称原始 XML 已复制归档到仓库。

[Original](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Offline-20260929T001520530/Original-RESULT.json) /
[Ranking](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Offline-20260929T001520530/Ranking-RESULT.json) /
[IsolatedCount](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Offline-20260929T001520530/IsolatedCount-RESULT.json)。

## 5. 真实环境和保留数据

仅启动原隔离实例：

- MySQL 8.4.11，UUID 8c6cfcb3-ba49-11f1-bf64-005056c00001，127.0.0.1:33078。
- kg_assistant_i2_20260927_r01；D:/kg-i2-env 联接真实目标 D:/知识图谱-i2-env；原 datadir、配置、客户端/服务端二进制 hash 不变。
- 7901 原规模 10000 节点、30000 active 边、2000 资料、20000 chunk；R1 小型样本全部保留。
- 启动后 34 表的数据/DDL hash 与 R1 最后 Semantics-AFTER 完全一致；未装载/重置/销毁数据。
- reader grants 与原环境记录一致；loader 全程锁定。测试子进程只收到 reader 凭据，不收到 root/loader。
- root 仅负责实例身份、状态快照、general log 开关和正常 SHUTDOWN；没有业务写入、账号调整、索引或迁移。
- 连接 3000ms、socket 4000ms、statement 2s、session max_execution_time=1500ms 原样继承冻结 I2MySqlSupport，未提高预算。
- 原 R1 Reader/测试/模型/API/前端配置未改。

证据：[身份与规模](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/IDENTITY.json)、[原数据一致](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/RETAINED-DATA.json)。
启动造成的 binlog 文件轮转不与跨阶段零写入混淆；零写入按同一 reader 静态窗口比较。

## 6. 真实验证结果

真实 JUnit 方法执行 3 个，2 个通过、1 个 SQL 错误，均单次执行、无重跑。方法内部场景和性能采样不冒充额外 JUnit 定义。

### 6.1 新 COUNT

直接执行当前仓储产生的第 9 条 SQL；测试独立读取实际节点/库归属/active 边，在 Java 测试内按双端可见性计算预期，不在生产适配层重写业务规则。

| 场景 | 预期/实际 COUNT | 本次单次耗时 |
|---|---:|---:|
| 库 7910 | 1 / 1 | 61ms |
| 库 7910 + 7911 并集/共享节点 | 1 / 1 | 22ms |
| 库 7911 自环/反向/多类型边 | 0 / 0 | 18ms |
| 库 7912 仅连接范围外节点 | 1 / 1 | 23ms |
| 空库 7913 | 0 / 0 | 15ms |
| 原规模 7901 | 0 / 0 | 261ms |
| 项目范围 | 3 / 3 | 105ms |

7901 与项目范围真实 EXPLAIN 显示 incident_nodes 的非相关物化/端点去重，再以 eq_ref/not_exists 判定；dependent 标记均为 false。
计划中仍可能有一次性边扫描或内部临时结构，这不等于此前逐节点重复相关扫描；没有人为创建索引。
[完整 SQL、EXPLAIN、独立预期和耗时](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/CountPlan-RESULT.json)。

这些是本次静态数据下的单次结果，不是 p95、并发一致性或生产性能保证。

### 6.2 原排名

当前 7901 前 10 为 100000～100009，degree 均为 6；直接排名单次 353ms。原 R1 EXPLAIN 标准通过，无 dependent per-node 扫描。
[原测试结果](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Plan-RESULT.json)。353ms 不是完整洞察耗时或 p95。

### 6.3 完整洞察及新阻断

原 R1 七个小型完整洞察场景全部通过，包括选库/并集、范围外边、自环、反向/多类型边、共享节点、零度节点、空库和 topLimit=1/3。

随后项目范围独立 oracle 为 10011 个节点、30009 条可见 active 边、3 个孤立节点。完整 insight 经过新 COUNT 后，在**第 10 条孤立列表查询**失败：

```sql
SELECT n.id,n.canonical_name,n.node_type,0
FROM visible_nodes n
WHERE NOT EXISTS (
  SELECT 1 FROM visible_edges e
  WHERE e.source_node_id=n.id OR e.target_node_id=n.id
)
ORDER BY n.id LIMIT ?
```

CTE 仍是原 PROJECT 范围及 active、双端可见边；此次 isolatedLimit=10。错误为 SQL state HY000 / error code 3024 / maximum statement execution time exceeded。
源码位置：[JdbcQualityReadRepository.java:246](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java:246)。
[完整实际 SQL 与错误](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Semantics-RESULT.json)。

本轮没有再次查询失败 SQL 或补做其 EXPLAIN，没有就地修复。不能把新 COUNT 已验证解读成完整洞察瓶颈全部解除。

### 6.4 未执行项

因新阻断停止，Base、Budget、Performance、LegacyScale 均未启动；没有把其历史通过记录当本轮重验。
20 次单线程采样执行 0 次、20 次四并发采样执行 0 次；**无完整洞察 p95**，未删除慢值或挑选最好一轮。
此前 I2 尚未完成的并发写入一致性、物理长原文链路等保持原状态；本次没有推进。

## 7. 零业务写入、收尾与证据保留

CountPlan、Plan、Semantics 三个 reader 静态窗口均满足：

- 34 表行数、数据 hash、DDL hash 前后一致。
- binlog 位置一致；loader 前后均锁定 Y。
- 活跃 reader 查询数为 0；general log hash 各自保留。
- 最后一组虽因 SQL 超时失败，仍完成窗口证据和收尾，未重置数据库。

[COUNT 窗口](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/CountPlan-ZERO-WRITE.json) /
[排名窗口](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Plan-ZERO-WRITE.json) /
[失败窗口](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Semantics-ZERO-WRITE.json)。

隔离实例通过正常 SHUTDOWN 停止，graceful=true，无残留隔离进程或 33078 监听；最后再次检查端口仍无监听。保留原 mysql-data，未触碰其他服务。
[证据索引](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/FINAL-INDEX.json) 列出两轮运行目录的 34 个结果/日志/快照文件 hash；24 份本轮 XML 的 hash 均核对一致。日志可能受仓库通用 ignore 规则影响，但磁盘文件已保留，不声称已提交或异地备份。

准备阶段新增集成运行脚本曾因字符串替换转义出现一次 PowerShell 解析错误；总控只修正未封存的本轮集成脚本，随后三脚本解析通过并封存包 hash。该次没有编译、测试或启动数据库，不算业务红灯/新增覆盖；本轮所有 Maven 运行及唯一真实 SQL 失败均保留，没有重跑。

## 8. 作者、运行者、模型与下一步边界

| 工作 | 实际执行者 |
|---|---|
| R2 COUNT 领域实现、一条 R1 预期调整、新增 10 项领域回归、原离线报告 | 原桌面 02 |
| 原 02 历史 68 + 15 + 10 离线验证 | 原桌面 02，引用其已接受交付，不是本轮重跑 |
| 此前 R1 排名代码/15 项测试 | 此前总控；原 02 后续接管维护，不倒改作者 |
| 本轮受控汇集、集成测试/脚本/报告、主项目 154 + 15 + 10 回归 | 总控 |
| 本轮原隔离 MySQL 身份/COUNT/排名/完整洞察验证、零写入及停机 | 总控 |
| 01、03～05 | 本轮无操作，未启动子任务 |

领域要求模型/推理档位：GPT-6 Sol / High；用户已在桌面确认，工具独立回读未核实，实际模型/推理强度未独立核实。总控实际模型/推理强度未核实，不将访问原 02 文件描述为原 02 本轮执行。

当前保持停止。若负责人决定处理新的孤立列表性能阻断，应由总控另拟精确定向任务，经负责人手动转发原 02；本轮未创建或派发该任务，不修领域代码。
本报告提交技术事实供负责人验收，不自动完成剩余 I2，不进入 I3/W2、模型/API/前端/部署。

