# 02 质量治理｜I2-R1 离线补修报告

日期：2026-09-28。状态：**离线实施和限定回归已完成，等待总控技术复核及项目负责人验收；尚未汇集主项目，尚未真实 MySQL 复验。**

## 1. 授权、执行方式与结论

依据 [I2-R1 任务单](D:/知识图谱/docs/assistant-team/integration/I2/I2-R1-QUALITY-PERFORMANCE-TASK.md)，接令检查后，项目负责人以“开始”授权本轮三文件离线实施。

实际目录为 `D:/知识图谱-worktrees/assistant-02-quality`，分支 `codex/assistant-02-quality`，HEAD 保持 `bcd3b60ea4e6e18daad53f31787028a0f69bc6cf`。当前总控会话直接在指定工作树串行执行，没有向原 02 桌面会话自动派发、接管或创建替代会话；没有启动其他子任务。当前模型/推理强度无法可靠回读，不将 `gpt-6-sol / high` 要求冒称为已核实的运行设置。

已将度数排名由逐节点相关 COUNT 改为可见边端点预聚合后关联可见节点。仅修改该排名区域，没有私有辅助方法新增，没有修改孤立节点查询或其他统计。原 68 项及新增 15 项隔离测试共 **83/83 通过，0 失败、0 错误、0 跳过**。

这不证明真实 MySQL 性能缺陷已经解决；I2 的原性能阻断仍待总控在原实例、原样本规模和原预算下复验。没有网站新增功能可验收。

## 2. 实际三文件清单

全部相对本工作树，主项目没有接收这些变化：

| 动作 | 文件 | 实际范围 |
|---|---|---|
| 修改 | [JdbcQualityReadRepository.java](D:/知识图谱-worktrees/assistant-02-quality/backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java:228) | 仅 `insight()` 排名构造的原 3 行替换为 9 行 |
| 新增 | [JdbcQualityInsightRankingRegressionTest.java](D:/知识图谱-worktrees/assistant-02-quality/backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightRankingRegressionTest.java) | 11 个普通测试 + 1 个参数化测试的 4 个用例，共 15 项 |
| 新增 | 本报告 `docs/assistant-team/reports/02-quality-I2-R1.md` | 变更、测试、保护与后续交接记录 |

原 W1 报告、既有测试、协调文档、依赖清单、公共核心、原文定位、其余领域、既有业务代码、pom、配置和迁移均未改动。未提交、合并、push、部署或运行应用。

Maven 正常更新了本工作树忽略目录 `backend-java/target` 下的编译与测试产物；它们不属于新增业务源码或主项目汇集。

## 3. SQL 变化与语义核对

保留原 `visible_nodes / visible_edges` CTE，不改变其过滤和绑定。新增两个仅用于排名的 CTE：

```sql
incident_endpoints AS (
  SELECT source_node_id AS node_id FROM visible_edges
  UNION ALL
  SELECT target_node_id AS node_id FROM visible_edges
  WHERE target_node_id<>source_node_id
),
node_degrees AS (
  SELECT node_id,COUNT(*) AS degree
  FROM incident_endpoints
  GROUP BY node_id
)
```

随后从可见节点 LEFT JOIN 度数，使用 `COALESCE(d.degree,0)`，按 `degree DESC,n.id` 排序，最终应用参数化 `LIMIT :limit`。原 RowMapper、topLimit 参数绑定和返回类型未变。

| 不变规则 | 本轮落实方式 |
|---|---|
| 授权范围先于统计 | `nodeCte / visibleNode / bind / insightScope` 原样保留，资料或节点洞察范围继续在 JDBC 前拒绝 |
| 共享节点不重复 | 可见节点仍通过 EXISTS 查库关联，而非 JOIN 多条库归属 |
| 节点状态口径 | 不新增节点 active 过滤；原 inactive 节点仍可见 |
| 有效边范围 | 边仍须 active，且两端均可见；跨两个已选库的边可以纳入，连接范围外节点的边不纳入 |
| 自环一次 | source 分支计一次；target 分支排除 source=target |
| 反向边、多类型边分别计数 | UNION ALL 保留每条边的端点贡献，不 DISTINCT 邻居或端点对 |
| 0 度节点 | 从可见节点 LEFT JOIN，缺失聚合值补 0；容量足够时仍可排名 |
| 排序和截断 | 度数降序、数值节点 ID 升序；仅最后一处 LIMIT |
| 非排名查询 | 新测试精确对照其他 9 条洞察 SQL 及原类型上限 50、独立孤立列表上限 |
| 失败与一致性 | 不吞 JDBC 异常、不返回伪成功空结果；不新增事务快照或并发一致性承诺 |

原相关 COUNT 已有正确的自环计一次语义；本轮是保持语义的计算方式调整，不把它描述为此前自环计数错误。UNION ALL/分组的数据库执行计划尚未观测，不声称物理上只扫描一次，也未报告任何新性能数字。

## 4. 测试命令、结果和证据分层

使用已有 Java 17 和已缓存的 Maven Wrapper 分发，`-o` 离线运行；没有安装依赖、启动 Spring 上下文或连接任何数据库/模型。工作目录均为本工作树的 `backend-java`。

### 4.1 先验证新增测试能检出旧结构

目标实现尚未修改时运行：

```powershell
.\mvnw.cmd -o '-Dtest=JdbcQualityInsightRankingRegressionTest' test
```

2026-09-28 09:45:36 +08:00：15 项执行，11 通过、4 失败、0 错误、0 跳过，BUILD FAILURE（预期红灯）。四个失败为：

- `rankingAggregatesScopedEndpointsInsteadOfCountingEdgesForEachNode`
- `selfLoopContributesSourceOnceAndIsExcludedOnlyFromTargetBranch`
- `reverseAndDifferentTypeEdgesKeepRowMultiplicity`
- `zeroDegreeNodesRemainInOuterLeftJoinWithZeroValue`

这些是对拟采用新 SQL 结构的断言，不是一次真实查询超时复现，也不说明旧实现在上述三类计数语义上有错误。该次 XML 原始 SHA-256：`d0bc5c7262c80a75f147d704a2128a701c0461f4b36438ea0453c4392368e965`。

本节保留红灯结果摘要及散列；target 下同名 XML 随后由正常绿灯运行更新，未另建第四个归档文件。原 I2 两次真实超时的失败证据和报告均未改动。

### 4.2 修改后运行明确的六个测试类

```powershell
.\mvnw.cmd -o '-Dtest=AssistantRuntimeTest,DecisionParserTest,QualityAgentToolProviderTest,QualityEvidenceVerifierTest,JdbcQualityReadRepositoryTest,JdbcQualityInsightRankingRegressionTest' test
```

2026-09-28 09:46:33 +08:00：BUILD SUCCESS。按当次六份 JUnit XML 的 classname + name 去重为 83 项，不混入旧 target 内其他报告。

| 测试类 | 当次通过项 | 分类 |
|---|---:|---|
| AssistantRuntimeTest | 28 | 原核心 |
| DecisionParserTest | 15 | 原核心 |
| QualityAgentToolProviderTest | 15 | 原质量域 |
| QualityEvidenceVerifierTest | 6 | 原质量域 |
| JdbcQualityReadRepositoryTest | 4 | 原质量域，文件及断言未改 |
| JdbcQualityInsightRankingRegressionTest | 15 | R1 新增 |

合计原核心 43 + 原质量 25 + 新增 15 = 83。红灯 15 项属于先前执行，不再加到“新增用例数”或最终去重数里。

新增覆盖是 **SQL 结构/参数约束、真实 RowMapper 在 mock ResultSet 上的映射、异常传播**：选库/项目范围、共享节点 EXISTS、双端 active 边、自环分支、边记录多重性、零度 LEFT JOIN、排序及 LIMIT、绑定与映射大于 32 位的 ID/度数、空结果，以及 4 种不支持的资料/节点范围在 SQL 前拒绝。没有在 fake 中重做 SQL 引擎，也没有把手工返回的 mock 行当作 SQL 语义证明。

当前绿灯 XML 在本工作树 `backend-java/target/surefire-reports/`；散列记录如下，后续重跑可能更新这些生成文件：

| 类（包前缀 com.knowledgegraph.assistant） | XML 原始 SHA-256 |
|---|---|
| quality.JdbcQualityInsightRankingRegressionTest | `e180f98bd125623e40518392e4ee2e399d4b5958920fd0b60d2635ab83162642` |
| quality.JdbcQualityReadRepositoryTest | `ba1b480811fe1931d09919f0df509b37dc7ac5907291e1f4b0c4bf0ec212c154` |
| quality.QualityAgentToolProviderTest | `90eee2f6ee3f8cb2fc50699b0b4fd9af8a6c619bc84c9d8959ce09883497674c` |
| quality.QualityEvidenceVerifierTest | `a62dfcc09093ec6e30727164e1fea0e78eba78ef9ce677f57d5b6c6b89ef0a15` |
| runtime.AssistantRuntimeTest | `8d18b005b1cc4b69e264f417b355a66a4045c92ee87bb6ef671e5e1986f0e286` |
| runtime.DecisionParserTest | `1d6f8ca15a39aeef54e977494b991cdc6211751da4bf720cafb2b8d0c8081f48` |

## 5. 前后 hash 与范围保护

规范化方法：UTF-8 解码，CRLF 转 LF，保留结尾换行，不 trim。以下两个源码文件本轮 raw hash 与规范化 hash 相同。

| 文件/阶段 | SHA-256 |
|---|---|
| 目标实现修改前 | `cc2c7aa2e5a401fd6f1c3dbc05d631be563e0dad9551f7f70de7be0ab17ebee6` |
| 目标实现修改后 | `fde4199bef96385ea87a3a0bfd671f170edd2501de1a2dab7adc8481b2757e52` |
| 新增测试 | `78d6e56c7ca07cdbf574ae2437fff94cfbf9fd6b94ce9582e15daf0cbbbc8100` |

既有 `JdbcQualityReadRepositoryTest.java` 保持 `c99ac56054eee8f01d20aa3cac9f9f11be3c3a663d5aaeb5919bb5c956b9dabc`。

执行前在内存中记录了本工作树 434 个 Git 可见文件的 raw hash、Git 状态和目标实现原文。执行后核对：

- 原 434 个文件中仅目标实现发生批准的变化，其余 433 个内容保持原样；新增文件仅为本次测试和报告。
- 对原实现做“仅替换该排名文本块”后，与修改后全文精确一致；不仅是靠 Git 未显示未跟踪文件差异来判断。
- 原 02 的 16 项冻结依赖全部匹配；分支与 HEAD 未变。
- 主项目 499 个保护文件 raw hash 全部匹配，62 个 I1 汇集文件规范化 hash 全部匹配；本轮未执行主项目汇集。
- 原目标实现前像仍保留在未修改的主项目中，与 I1 清单和 I2 保护快照相符。本轮未为备份新增第四个文件，也未更新旧清单 expected hash。

主项目与本工作树目标实现**现在应当不同**，这是本轮批准的离线补修，不应覆盖主项目使其“重新一致”。总控接收前仍须核验前像、归档、建立批准路径的增量清单；不能直接运行要求原 owner hash 不变的检查后忽略报错。

## 6. 尚未验证与交接停点

1. 未连接真实 MySQL，没有执行新 SQL、EXPLAIN、1 万节点/3 万边性能采样、四并发采样或零写入窗口验证。因此不能宣称 MySQL 兼容、语义样本或 p95 已通过。
2. 任务单中 7910/7911/7913 语义样本与 7901 性能样本均未装载/更改。自环、跨库共享节点、反向/不同关系类型、范围外边、0 度及前十排名，仍须总控用独立预期在真实库执行核对。
3. 孤立节点查询原样保留；若后续成为新瓶颈，停止报告，不自行扩大 SQL、索引、迁移、超时或其他领域修改范围。
4. 未修改原预算、缩小样本、放宽断言、跳过测试、改索引、接入缓存或变更契约。I2 原有未完成项继续保留。
5. 没有数据库账号接收、实例启停、真实模型、生产宿主/API、前端、生产注册、部署、W2 或 I3 工作。
6. 本轮未运行主项目统一 154 项回归；那属于总控受控汇集后的验证，不把本轮 83 项冒称全领域通过。

本轮到此停止。请总控先复核三文件差异及测试，再在另获批准的范围内安排受控汇集和真实实例复验；最终是否接受由项目负责人确认。

