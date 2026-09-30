# 02 质量治理｜I2-R3 离线整体性能审查与集中补修

日期：2026-09-29。状态：领域离线交付，等待总控增量技术复核；**不代表真实 MySQL 性能、I2 或项目验收通过**。

## 1. 范围、前像与结论

- 原工作树 `D:/知识图谱-worktrees/assistant-02-quality`，分支 `codex/assistant-02-quality`，HEAD `bcd3b60ea4e6e18daad53f31787028a0f69bc6cf`。原有未提交文件全部保留。
- 开工复核四份领域文件前像，与 [任务单](D:/知识图谱/docs/assistant-team/integration/I2/I2-R3-QUALITY-INSIGHT-PERFORMANCE-TASK.md) 第 7 节完全匹配；主项目与历史冻结材料沿用接令检查，不在本轮改写。
- 审查对象为 `JdbcQualityReadRepository.insight()` 的十次顺序查询及组合成本。确认需要实施的性能补修为 **Q10 孤立节点 LIST**；Q01～Q09 均未因风格调整。查询次数、次序和公开结果结构保持 10 条/原样。
- Q10 将旧的逐节点、双端 `OR` 反关联改为先从有效可见边归并端点，再与可见节点反连接；复用 Q09 已有结构。原 LIST 的真实 3024 超时与 EXPLAIN 是问题证据；新 SQL 只有离线结构/绑定/映射验证，性能与兼容性尚待总控真实复验。
- 不修改范围、共享过滤、现有 Review/Insight 业务服务、公共契约、主项目或其他领域；没有连接数据库或调用项目真实模型。

## 2. Q01～Q10 全查询矩阵（当前交付代码）

`insightScope` 在首条 SQL 前拒绝文档/节点维度和无库的 SELECTED；`nodeCte` 只在 insight 内使用，提供以 `EXISTS` 去重的可见节点和 active、双端可见的真实边。代码位置：[JdbcQualityReadRepository.java](D:/知识图谱-worktrees/assistant-02-quality/backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java:199)。以下行号为本批次交付版本。

| Q | SQL/调用位置、绑定 | 共享片段、口径与成本审查 | 证据层级、决定和保护 |
|---|---|---|---|
| Q01 | `SELECT COUNT(*) FROM visible_nodes`，:213；SELECTED 绑定 `libraryIds` | `nodeCte/visibleNode`，每个共享节点计一次、不新增 active 节点过滤；单独扫描/聚合 | 小样本整组语义间接覆盖，缺逐条计划/耗时；**保留，真实性能待验证**；新全职责回归及旧基础断言 |
| Q02 | `SELECT COUNT(*) FROM visible_edges`，:214；SELECTED `libraryIds` | `nodeCte` 双端 INNER JOIN，`e.status='active'`，每条真实边计一次；连接成本待量化 | 小样本和项目 oracle 有语义覆盖，缺逐条性能；**保留，真实性能待验证**；新全职责/端点断言 |
| Q03 | `COUNT(*) FROM documents d`，:215；SELECTED `libraryIds` | `documentFilter` 用主库或 topic assignment `EXISTS`；不会因多归属放大资料行，OR/EXISTS 成本未实测 | 只有整体小样本/离线形状；**保留，真实性能待验证**；旧/新范围断言 |
| Q04 | `entity_candidates` 待审计数，:220；SELECTED `libraryIds` | `pendingBase` 与 job、document 连接，`PENDING` + 原四种 job 状态，资料范围在聚合前；一次往返 | 缺逐条真实计划/耗时；**保留，真实性能待验证**；新完整职责/独立候选计数断言 |
| Q05 | `relation_candidates` 待审计数，:221；SELECTED `libraryIds` | 与 Q04 同口径并做 `long` 加法，不跨候选表 ID 去重；第二次往返 | 缺逐条真实计划/耗时；**保留，真实性能待验证**；新完整职责断言 |
| Q06 | 完整 `node_type` 分组总数，:222；SELECTED `libraryIds` | `nodeCte` 全量分组后计组，包括原 null 分组；不能由最多 50 条 Q07 推断 | 缺逐条真实计划/耗时；**保留，真实性能待验证**；新独立 60/列表 2 条断言 |
| Q07 | `node_type,COUNT(*)`，:224；SELECTED `libraryIds`，`limit=50` | `nodeCte` 全量分组，按 `COUNT(*) DESC,node_type` 原数据库排序，再截 50；潜在临时表/排序未实测 | 缺逐条真实计划/耗时；**保留，真实性能待验证**；旧/新 SQL、绑定和 null 类型映射 |
| Q08 | 节点度数排名，:229–238；SELECTED `libraryIds`，`limit=topLimit` | `UNION ALL` 两端，排除自环第二次计度；真实平行/反向边保留，零度外连接，degree 降序/Long ID 升序；聚合排序 | R1/R2 原规模直测一次 353ms、正确前 10，非 p95/全调用；**保留，整体预算待验证**；R1 15 项全保留 |
| Q09 | 孤立总数，:239–244；SELECTED `libraryIds`，无 limit | 有效边端点 `UNION` 去重后反连接，全体可见节点计数，独立于 LIST 截断 | R2 七场景、7901 单次 261ms、PROJECT 105ms；**保留，整体预算待验证**；R2 10 项全保留 |
| Q10 | 孤立列表，:245–250；SELECTED `libraryIds`，`limit=isolatedLimit` | 改为 Q09 同端点集反连接，`n.id ASC` 后截断，`NodeItem.degree=0`、Long ID/完整名称/类型；没有额外权限或近似值 | 旧 PROJECT LIST 3024/计划高成本；**修改**；新回归核查端点去重、双端可见、排序、独立限制、映射与错误传播，真实性能待验证 |

十次顺序往返、可见节点/边 CTE 在多条 SQL 中重复工作，构成可能的组合成本；目前缺 Q01～Q07 单独实测及完整成功调用的分项时间，不能据此合并查询、跨查询缓存或宣布已达 3000ms。保留既有失败即上传、后续不执行的行为；未引入事务快照、并行执行、重试或 Java 全图计算。

## 3. 三类交付清单

### ① 实际修改及原因

| Q/文件 | 前像 → 本批次规范化 SHA-256 | 必要性、具体变化与保护 |
|---|---|---|
| Q10 `JdbcQualityReadRepository.java` | `c977e453d407aa150404c8f5acecddb6a9dfd63ae27692acb4e057840a3bcc15` → `6a17642e64bbfdb5f0d3bffa13ad9cde4ddbe6384e3b2d5f1a2e592a4db0d740` | [真实失败](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Semantics-RESULT.json)为 PROJECT 第 10 条 LIST 的 3024/HY000；[EXPLAIN](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/EXPLAIN.json)示节点/边 ALL 与约 232927149 **估算**边关联行。仅在 insight 私有局部提取 `incidentNodes` 字符串供 Q09/Q10 使用；Q09 输出 SQL 保持原样，Q10 用同一 `UNION` 端点集 LEFT JOIN `IS NULL` 代替相关双端 OR。`visible_edges` 的端点均与可见节点 INNER JOIN，端点不会为 null；端点去重不改变“有无边”，因此新旧孤立集合等价。仍以 n.id 排序、独立 limit、Long/字符串映射；不触及共享过滤。真实执行计划/耗时尚未验证。 |
| Q10 预期 `JdbcQualityInsightRankingRegressionTest.java` | `454ad390e192749f41c5759ba55b7e4915ce203296ced09def72bc4aabcc25d6` → `8c6c40ff5fc28254f7226a4013d45b04fe67972557de11814af51952eb2c9ced` | `nonRankingQueriesAndTheirLimitsKeepFrozenShape` 仅将第十条 SQL 的独立精确预期改为端点集反连接；其他八条非排名 SQL、Q08 排名、十条次序及绑定断言原样保留，未改成宽泛 contains。 |
| Q09/Q10 定位 `JdbcQualityInsightIsolatedCountRegressionTest.java` | `1fcb245eaac857476c7a8bb6c4d854f6bfea649d347079291029bb88fca10c66` → `10f46f136fc13a1ff7cf50f4e90c3ac0e9c0ffb74a7577619f1558eadddbfff9` | 因 Q10 也使用 `incident_nodes`，原“仅 Q09 含此标记”的定位条件改为精确 COUNT 反连接标记；原无界总数、scope、Long、失败即停等保护保留。第十条旧 SQL 精确预期改为新 SQL；方法名改为反映 COUNT 与 LIST 各有限制，断言未删减。 |
| 新 `JdbcQualityInsightPerformanceRegressionTest.java` | 不存在 → `5bcdee1ee353d22b6770242bf8683d502dc77f4bdb2a8e1b789306c523c3724f` | 新增 6 个方法定义、19 个展开场景；覆盖 Q01～Q10 顺序/职责、PROJECT/SELECTED 参数、候选相加、类型总数独立、Long/null 映射、端点集合、limits、十处失败即停和非法范围。独立 SQL 字符串预期，不调用生产私有构造器自证。 |
| 本报告与 `I2-R3/offline-results.json` | 新增 | 仅记录本轮离线审查、前后 hash、分组 XML hash/红灯与重跑；不含凭据或环境脚本。 |

`JdbcQualityReadRepositoryTest.java` 审查后未改：前后均为 `c99ac56054eee8f01d20aa3cac9f9f11be3c3a663d5aaeb5919bb5c956b9dabc`。上述 hash 及新增证据清单见 [离线结果索引](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/reports/I2-R3/offline-results.json)。R1 排名原作者为此前总控；R2 COUNT 原作者为原 02，本轮未重写历史作者或旧报告。

### ② 检查过但未修改的查询及原因

Q01、Q02：可见节点/有效边完整聚合是既有权限与计数定义，没有本版本逐条慢查询证据；真实性能待验证。Q03：资料主库/assignment 的 `EXISTS` 避免重复，但 OR 成本未知；真实性能待验证。Q04、Q05：原候选状态、job 四状态和各表独立计数无明确瓶颈证据；真实性能待验证。Q06、Q07：完整类型总数与最多 50 条展示必须分开，分组/排序成本未知；真实性能待验证。Q08：R1 改善后直测 353ms 且语义通过，不是整体达标证明；保留，组合性能待验证。Q09：R2 七场景及两种范围单次实测通过，不回退到逐节点相关查询；保留，组合性能待验证。Q10 已列①，因此此类不含 Q10。

### ③ 发现但需另批的事项

| 事项/目标 | 依据、影响与风险 | 替代路径/本阶段阻断 |
|---|---|---|
| 若总控真实复验仍显示 Q10 或其他 Q 慢，可能需要索引/迁移/统计维护等数据库方案 | 目前仅有旧 Q10 计划与单次 COUNT/排名结果，**没有新 Q10 真实计划**，不能预先指定索引。DDL/维护会影响写入、锁和冻结环境，超出领域授权 | 先按任务单第 10 节逐项真实验证，再由总控另批具体方案；**不阻断本轮离线交付**，但阻断宣布真实问题解决 |
| 若需改变共享授权逻辑、事务或公开结果契约来减少组合成本 | 重复扫描与十次往返是结构性组合风险，尚无分项预算证据；改共享过滤、事务或契约可能改变语义/权限及其他方法。纯 insight 私有重组本可在本单有证据时实施，但目前没有依据 | 先取得 Q01～Q10 分项计划/耗时与组合预算，再提交跨边界方案及等价证明申请；本轮不实施、不视为已确认优化必要性 |

## 4. 离线验证、红灯与证据

全部命令在原 02 `backend-java` 下以 `-o` 执行；仅显式选择指定类，不启动 Spring/MySQL/真实模型。原始 XML 在 `target/surefire-reports/TEST-<class>-<suffix>.xml`，hash 收录于 [独立轮次证据](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/reports/I2-R3/offline-results.json)；XML 是本机 ignored build output，**未声称复制入仓库或异地备份**。

| 轮次/命令选择与唯一 suffix | 方法定义 | 展开场景 | 结果 | 重跑 |
|---|---:|---:|---|---:|
| `-Dtest=AssistantRuntimeTest,DecisionParserTest,QualityAgentToolProviderTest,QualityEvidenceVerifierTest,JdbcQualityReadRepositoryTest` / `i2r3-original-01` | 59 | 68 | 68 通过，0 失败/错误/跳过 | 0 |
| `-Dtest=JdbcQualityInsightRankingRegressionTest` / `i2r3-ranking-01` | 12 | 15 | 15 通过，0 失败/错误/跳过 | 0 |
| `-Dtest=JdbcQualityInsightIsolatedCountRegressionTest` / `i2r3-count-01` | 7 | 10 | 10 通过，0 失败/错误/跳过 | 0 |
| `-Dtest=JdbcQualityInsightPerformanceRegressionTest` / `i2r3-whole-01` | 6 | 17 当轮已展开 | **1 错误**：测试参数源构造了公共 `Scope` 禁止的空 SELECTED；非生产 SQL 错误 | 1 |
| 同一新类 / `i2r3-whole-02` | 6 | 19 | 19 通过，0 失败/错误/跳过；仅将无效测试输入替为有效但 insight 不支持的文档范围，另将 null 类型断言改为 `assertNull` | 已计入上行 |

最终去重覆盖为 **84 个方法定义、112 个展开场景**（68+15+10+19），不是把首轮 17 再相加；旧主项目 179 项是 R2 历史基线，非本轮在主项目重跑。失败 XML 保留，重跑独立后缀。编译 101 个生产源文件、45 个测试源文件由首轮 Maven 触发，未运行全量测试。

模拟 JDBC 只证明代码生成的 SQL 文本、绑定、Java 映射和异常顺序，不证明 MySQL 优化器选路、真实 SQL 正确性或新 LIST 在 1500ms 内完成。R2 [诊断说明](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/LIST-EXPLAIN-DIAGNOSIS.md)的计划生成 3ms 不是 LIST 耗时，232927149 是估算不是实扫；本轮 20+20 p95 样本为 0。

## 5. 总控交接、责任与停止

- 对后续总控逐条实测：当前 Q01～Q10 对应仓储 `insight()` 行 213、214、215、220、221、222、224、233、244、245；SELECTED 每条绑定 `libraryIds`，PROJECT 不绑定，Q07 固定 limit 50、Q08 topLimit、Q10 isolatedLimit，Q09 无 limit。Q10 SQL/绑定需要更新主项目集成测试的旧 LIST 定位预期（若存在），02 不修改主项目；Q09 原直接定位和 Q08 排名定位不变。
- 真实复验须先校验 PROJECT/7901/选库并集/空库等各适用 Q 的执行计划、正确性与耗时，再跑完整洞察和原定 20 单线程+20 四并发预算；失败仍按统一批次退回原 02，不在集成现场改 SQL。真实 MySQL 验证者：本轮**无**，后续由获批的总控负责。
- 本轮实际代码/测试/报告修改者：原桌面 02；离线编译与测试运行者：原桌面 02，五次命令、含新类一次红灯和一次重跑。R1 历史作者为此前总控，R2 历史作者为原 02。模型要求 GPT-6 Sol / High；项目负责人表示发送正式指令前已于桌面确认。工具不能独立回读，故**实际模型未核实；实际推理强度未核实**。
- 未改 `JdbcQualityReadRepositoryTest.java`、QualityReadRepository/QualityAgentToolProvider、公共核心/source、其他质量业务代码、配置、索引/迁移、API/前端、主项目或旧 W1/R1/R2 报告；没有提交、汇集、部署、数据库凭据、真实模型或其他任务。原 02 W1 16 项冻结依赖沿用未变结论，未重算全部旧证据 hash。

本批次到此停止，等待总控增量技术复核及后续获批的真实环境验证；不进入下一阶段。
