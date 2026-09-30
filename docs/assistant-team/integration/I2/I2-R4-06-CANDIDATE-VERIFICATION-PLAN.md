# 给负责人：I2-R4 候选补丁整批复验方案（待批准）

日期：2026-09-29。原 06 已完成只读准备核对；本方案不要求再次接令检查，也不是当前执行授权。总控本轮只编写方案，未派发、汇集、测试或连接数据库。

## 1. 目标、角色与批准边界

拟由原 06 在一次明确批次内完成：三文件受控汇集、统一离线回归、原隔离 MySQL 的真实边查询结果/计划/性能验证、零业务写入核对、正常收尾及报告。原 03 候选审核结论为“无离线阻断，待真实复验”，不是性能通过。

原 03 负责领域代码；06 不修改任何领域实现/SQL/单元测试，不调整索引、迁移、契约、权限、预算或样本。总控审核交付，负责人最终验收。01～05 待命，06 单独执行，只有获批采样进程可四并发。不继续其余 I2、I3、W2。

要求 GPT-6 Sol / High；沿用负责人 2026-09-29 对 06 的单独桌面确认，不重复询问。实际模型及推理强度的工具独立回读仍未核实。

## 2. 候选、主项目前像与允许落点

基线以 [候选审核记录](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-D2-03-PATCH-REVIEW.json)为准，其 raw SHA-256 为 `6875fc07a4989e37b82b81bf57abf47c08f2658b11d0e36faec59ae6544a5fd5`。不得改历史 expected hash。

从原 03 工作树只接收：

| 主项目相对路径 | 主项目前像规范化 SHA-256 | 03 候选规范化 SHA-256 |
| --- | --- | --- |
| `backend-java/src/main/java/com/knowledgegraph/assistant/research/JdbcResearchReadRepository.java` | `b922ec24fc397fd772524524c34c6837a3f044bff4bd18efcdd23c37ae67612f` | `7107873c220e44e5eb3e9638e98c70606503d6504fb6a99080e11e84f9c10c26` |
| `backend-java/src/test/java/com/knowledgegraph/assistant/research/JdbcResearchReadRepositoryTest.java` | `1b7c80d568cd0e75b982427c10c404b90f608ea8dee83daf4b92843a131277ce` | `2381dad233b3e22ff52209e59a1194d2954fb739855685b8268b2f774811f386` |
| `backend-java/src/test/java/com/knowledgegraph/assistant/research/JdbcResearchEdgeQueryPerformanceRegressionTest.java` | 不存在 | `39a8c48684640ca28ae7cc78cab2b3d45a6bf002eacf020d07a43219d6768ed2` |

规范化规则继续使用 UTF-8、CRLF→LF、保留结尾换行、不 trim。逐文件接收精确候选，不整体覆盖工作树，不在汇集时“顺手修正”。原 03 报告、离线清单和 8 份 XML 只原样归档，作者与原始 hash 保留。

拟批准 06 新增的实施文件仅为：

- 主项目 `D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2ResearchEdgeVerificationTest.java`，负责本批真实语义矩阵、候选 SQL/绑定取证和独立新分支采样；不得重写生产业务规则。
- 06 工作树 `D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-VERIFY/Invoke-R4Verification.ps1`、`Verify-R4VerificationBaseline.ps1`、`R4-VERIFY-OVERLAY.json`。
- 同一 `R4-VERIFY/` 下的 `archive/`、`evidence/<唯一 runId>/`、独立预期清单和保护清单；只放本批材料，不覆盖旧轮。
- 06 报告 `D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/reports/06-integration-I2-R4-verification.md`。
- 主项目 `backend-java/target` 内正常编译产物与唯一后缀 Surefire XML；原环境 `restricted-evidence/I2-R4-VERIFY/<唯一 runId>/` 内当前用户受限历史保全副本，以及正常实例运行日志追加。

上述新入口准备时均未占用；真正实施前再次确认，不覆盖未知文件。06 每条命令显式以自身工作树为 workdir；Maven 明确在主项目 `D:/知识图谱/backend-java` 执行。不得把 06 工作树当作可编译的当前主项目镜像。

## 3. 保全、runner 与离线关口

在汇集、编译或日志追加之前，记录主项目全部 Git 可见文件（含未跟踪）的保护快照及当前明确治理增量。仅本节白名单允许新增/变化；不能用旧 R3 guard 的预期差异为由关闭校验。历史版本与新 overlay 分层保留。

旧 R3、首批 R4、D2 的原始 XML、累计 general log 截面及本批引用的原 03 XML，先按索引核验并保全，记录实际份数/大小/raw hash，不硬编码沿用前一批数量。保全日志和含敏感信息的原始材料限制为当前用户可读；公开证据不包含密码。

复用已审核的输出收集、版本门禁、原环境身份核对、快照与正常停库逻辑，固定复用文件的 hash。新 runner 明确使用本批目录与唯一后缀，不能直接执行会写入旧证据目录的原顶层入口。修改仅用于编排/隔离/证据，不能替换领域 SQL 或正确答案。

真实阶段之前完成 PowerShell 解析、路径/版本/门禁、输出异常/截断处理、无数据库强杀分支、独立选择器和脱敏检查，保留离线结果。编译/离线失败不启动数据库。没有必要不重写已核准基础能力；需改变冻结 `I2MySqlSupport`、旧脚本或其他共享文件则停止申请。

统一回归分五组，各一轮：原 I1 19 类 154 项、R1 排名 15 项、R2 COUNT 10 项、R3 整体 19 项、R4 新增类 5 项。使用离线 Maven `-o`、精确 `-Dtest` 和唯一 `-Dsurefire.reportNameSuffix`；既有前四组合计 198 项，新组 5 项，预计去重 203 项，实际以 XML 为准。旧 E/D2 诊断测试不能因通配符被顺带执行或计入领域覆盖。保存所有失败与重跑，不借重跑增加覆盖。

## 4. 原环境和样本的已知限制

只使用 MySQL 8.4.11，UUID `8c6cfcb3-ba49-11f1-bf64-005056c00001`，`127.0.0.1:33078`，库 `kg_assistant_i2_20260927_r01`。英文联接 `D:/kg-i2-env` 真实目标必须仍为 `D:/知识图谱-i2-env`。只正常启动/停止身份已核实且本批拥有的原实例，不接管未知进程。

7901 原规模保持 10000 节点、30000 边、2000 资料、20000 chunk；原固定基础和 R1 样本保持。连接 3000ms、socket 4000ms、JDBC statement 2s、session 1500ms、SSL/UTC、reader SELECT 权限原样。管理凭据仅用于身份/快照/日志/正常停机，不能进入 Maven；不解锁 loader、不装载、不重置、不 ANALYZE、不改优化器设置。

需要明确的样本事实：`Invoke-I2Scale.ps1` 原装载只为规模节点建立证据，没有为规模边建立证据。最近封存 D2 快照的 `edge_evidence` 仅 3 行，与基础样本一致。因此：

- **SCALE_DOC**：库 `{7901}`、资料 `{300000..301999}`、节点不限。该范围的边结果应为空；不能把空结果冒充新分支的非空性能覆盖。
- **MIXED_DOC**：库 `{7101,7901}`、资料 `{7201,7202,300000..301999}`、节点不限。查询 7701 时预期仍只有 7801；这是不改数据条件下的“大范围资料参数 + 非空文档分支”，不是“30000 条具备正式证据的可见边”。它也不保证优化器实际材料化所有规模节点，必须按真实计划报告。
- 文档分支下跨资料分别取证、Long ID、多于十条合格边的截断，以及具备大量正式边证据的规模场景，现有样本不能完整覆盖。登记为缺口；本批不擅自补样，不据此宣称全语义/全负载验证完成。若需要补齐，另行申请精确数据变更范围。

样本依据（只读，不执行装载脚本）：`I2-FIXTURE.sql` raw hash `f2401d9bbe1833d8610d3c65080f17f845a5528b800fed10a8fa0b54dc523be1`；`R1/R1-FIXTURE.sql` 为 `d2a72adcb368908cbdafa2ef51de42a47a2bf92dcc85086ef41d0c31a40ad8f2`；`Invoke-I2Scale.ps1` 为 `9ec393bba42642013d8c183462ce748c8d69ba8a2f1310f59e00a1fc5188cece`。

## 5. 本批真实结果矩阵与执行计划

AB=`库{7101},资料{7201,7202}`；A/B 为相应单资料；LIB=`库{7101},资料不限`；R1A=`库{7910},资料不限`。未写节点维度均不限。除指定 limit 外为 10；空结果 hasMore=false；预期单项/短列表的 hasMore=false。结果按 ID 顺序完整比较，不能只看首条。

| ID | 真实调用/范围 | 独立预期 ID 列表 |
| --- | --- | --- |
| V01 | outgoingEdges(AB,7702,10) | `[]`，原失败调用 |
| V02 | outgoingEdges(AB,7701,10) | `[7801]`，source=7701、target=7702、has_source=true |
| V03 | incidentEdges(AB,7701,10) | `[7801]`，保留真实方向 |
| V04 | incidentEdges(LIB,7701,10) | `[7801,7802]`；7802 保留 inactive，has_source=false |
| V05 | outgoingEdges(仅资料{7201,7202},7701,10) | `[7801]` |
| V06 | outgoingEdges(A,7701,10) | `[7801]` |
| V07 | outgoingEdges(B,7701,10) | `[7801]` |
| V08 | outgoingEdges(AB且节点{7701},7701,10) | `[]`，目标端被排除 |
| V09 | outgoingEdges(AB且节点{7701,7702},7701,10) | `[7801]` |
| V10 | outgoingEdges(库{7102}且资料{7201,7202},7701,10) | `[]`，不能绕过节点库归属 |
| V11 | incidentEdges(R1A,900001,2) | `[910001,910002]`，hasMore=true |
| V12 | outgoingEdges(R1A,900001,10) | `[910001,910002,910004,910006]`；自环一次、多类型及 inactive 保留 |
| V13 | incidentEdges(PROJECT,7701,10) | `[7801,7802,7803]` |
| V14 | outgoingEdges(SCALE_DOC,100000,10) | `[]`，明确只是规模空结果 |
| V15 | outgoingEdges(MIXED_DOC,7701,10) | `[7801]`，大文档参数集合非空调用 |
| V16 | incidentEdges(MIXED_DOC,7701,10) | `[7801]`，同范围双向调用 |
| V17 | outgoingEdges(AB,7701,1) | `[7801]`，hasMore=false，不冒充正截断场景 |

每项只执行一次矩阵业务调用，并以未改动仓储实际生成的 SQL/命名参数进行一次 `EXPLAIN FORMAT=JSON`；记录 JDBC 展开 SQL、逐位绑定值/类型及错误。不能手改 SQL、删投影、删过滤、换固定答案或运行 EXPLAIN ANALYZE。EXPLAIN 失败不伪造计划；普通错误可继续独立安全项，禁止自动重试探针。

旧 `I2JdbcBaselineTest`、`I2ReadOnlyPrivilegeTest`、`I2QueryBudgetTest` 及 `I2ScaleQueryTest#scaledFixtureQueryWindowsAndScope` 使用冻结选择器和断言进行真实回归；重复包含的边调用分别记录为旧回归重测，不重复计算覆盖。禁止写入尝试仅限原获批的 reader 拒写测试，须权限门禁先通过；若意外成功即安全异常，立即停批次并报告，不自行清理。

## 6. 性能采样：原组与新分支分开

先完成正确性及安全门禁；失败调用依赖的采样组标阻断，不通过重复采样掩盖已知失败。

1. 原组：冻结 `I2QualityInsightRankingTest#fullScaleSingleAndFourConcurrentSamples`。原规模、完整 quality.insight + research.searchNodes 组合、20 单线程 + 四并发总计 20，原断言和 3000ms p95 不变，不把它算成新文档分支覆盖。
2. 新组：在新集成测试内固定每个样本依次调用 `outgoingEdges(MIXED_DOC,7701,10)` 与 `incidentEdges(MIXED_DOC,7701,10)`，分别校验完整 `[7801]`、方向、来源标记、hasMore，并分别记录两条调用与整组耗时。20 单线程 + 四并发总计 20，仍无预热丢弃，组合 p95 上限 3000ms，单条查询仍受原 session/statement/socket 限额。该组是本次新覆盖，不能声称与旧组合测量相同负载或有直接加速比。

两个组总计计划 80 个样本，不是 160/320 个；每组独立计算：20 条耗时升序后的第 19 条为 p95。每次包括序号、模式、开始/结束、调用、结果、耗时/错误及绑定范围；保留慢值，不补选最好轮。出现错误保留已执行及在途收尾结果，剩余依赖样本标阻断；少于 20 个完整样本不得用剩余成功项报合格 p95。所有重跑须另有指令、独立 runId，不默认进行。

## 7. 收尾与报告成功标准

入口与最近封存 D2 快照核对 34 表数据/DDL；reader 静态窗口前后核对同次启动 binlog、表数据/DDL、loader 锁定、reader 权限和残留查询。累计日志旧前缀保持，记录本批新增业务 SELECT/EXPLAIN/权限拒写尝试；测试日志写盘与业务零写入分开。

身份、权限、前像、样本漂移、未知修改或零写入异常立即停止，安全收尾。普通业务失败按上述预先批准矩阵继续独立项目并集中汇报，不现场改领域代码或追加诊断。所有工作线程/连接结束后只正常停止本批拥有的隔离实例，核实无残留查询/进程/监听，保留数据与全部历史证据。

交付至少包括：受控汇集前后像/真实作者、保护快照、归档清单、新集成实现版本、分组 XML 及实际定义/展开/重跑数、17 项矩阵逐项结果与计划、两个性能组逐样本与 p95/阻断、零写入与收尾、失败/未覆盖清单、模型确认来源/工具实况。每类数量据实际证据核算。

成功只表示：审核候选在本批已覆盖保留样本、原权限及原预算内通过复验。所有缺口保持未验证；不得称全部 I2、密集正式边证据负载、所有语义变体或网站上线完成。结束停止，总控审核证据、负责人决定验收。
