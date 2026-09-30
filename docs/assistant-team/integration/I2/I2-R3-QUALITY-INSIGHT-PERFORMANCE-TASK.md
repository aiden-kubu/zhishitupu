# I2-R3｜quality.insight() 整体性能审查与集中补修任务单

日期：2026-09-29。领域执行者：原桌面 02「质量治理」。总控负责证据交接、增量复核、受控汇集与获批的真实整体验收，不代做领域实现。

**当前仅准备任务与接令检查；没有授权本轮开发、编译测试、数据库连接或总控汇集。**
负责人已决定下一轮从单条 SQL 补修调整为整体性能审查。原 02 完成本单一次增量接令检查并停止，负责人下达正式执行指令后再开始离线集中实施；不再重复接手或对每条 SQL 单独做接令循环。

## 1. 本轮目标与替代边界

审查现有 `JdbcQualityReadRepository.insight(Scope,int,int)` 的全部查询和调用链成本，保持业务语义、权限、返回契约、异常行为与预算不变，集中修复有依据确认的性能问题。

本任务的审查单元是**整个 insight()**，不是“只修 LIST”。旧 R1/R2 的单条 SQL 限制是历史阶段边界；正式执行本单时，由本单对 insight 内部的整体性能范围取代，不把旧“其他九条 SQL 永远不变”继续当作本阶段授权规则。历史证据及旧 expected hash 原样保留。

- 无问题的查询不得为了格式、命名或统一实现风格而改动。
- 总控不指定 SQL 算法、优化器提示、索引方案或必须使用某种聚合/关联写法；具体分析与实现由原 02 提出。
- 排名与 COUNT 有单次真实通过记录，但不是全流程性能保证；整体审查要检查，不能因此回退到旧慢版本，也不能无依据重写。
- 有关索引、迁移、公共/领域公开契约、其他领域、权限扩大等需求，统一列为“需另批”，不得先实施。
- 缺少逐条实测的查询必须标为“真实性能待验证”，不能把没见过超时写成“已证明无性能问题”；也不能仅凭不喜欢写法就修改。
- 基于现有真实证据或可说明的算法/执行结构依据确认的问题可以进入集中补修；只基于静态分析的结论须明确其证据层级，真实兼容性和性能仍待总控复验。

## 2. 工作树、模型和主项目只读

- 原工作树：D:/知识图谱-worktrees/assistant-02-quality
- 分支：codex/assistant-02-quality
- 准备时 HEAD：bcd3b60ea4e6e18daad53f31787028a0f69bc6cf
- 主项目：D:/知识图谱，仅作只读资料来源。
- 保留所有已有修改；不新建/切换工作树或替代会话，不启动 App Server，不自动派发其他任务。
- 要求 GPT-6 Sol / High。此前用户已在桌面确认；正式开工前由负责人确认本轮当前配置。工具无法独立回读时如实写“实际模型未核实；实际推理强度未核实”，同时单列用户桌面确认的来源，不能以任务单当作实测。
- 原 02 不接收 root/loader/reader 账号，不启动或连接数据库、不自行跑主项目集成脚本、不自行汇集。

## 3. 证据交接与增量阅读

[证据目录清单](D:/知识图谱/docs/assistant-team/integration/I2/I2-R3-QUALITY-INSIGHT-EVIDENCE.json) 汇集现有 149 份相关文件的路径、大小、raw hash 和分组，包含原 I2 排名首次/统计更新后超时、EXPLAIN、预算样本、R1/R2 结果/日志/归档、LIST 诊断及收尾记录；没有数据库凭据。通过原路径只读交接，不整体复制工作树。

**首次完整阅读本任务单。** 已读且未变的 W1、R1、R2 离线材料、协调规则及冻结依赖直接复用已有结论；不要求再全文审查 149 份材料，也不要求每轮重算全部 hash。接令阶段核对四份领域前像，以及清单 `newEvidenceForReceipt` 的七份新增关键证据；剩余索引按查询与问题按需追溯。已有冻结依赖 16/16 结论可复用，发现相关变化再检查受影响项并说明原因。

新证据重点：

1. [R2 受控汇集与整组结果](D:/知识图谱/docs/assistant-team/integration/I2/R2/R2-REPORT.md)：主项目 179/179；原 154 + R1 15 + R2 10，不代表网站上线。
2. [R2 新 COUNT 实测](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/CountPlan-RESULT.json)：七场景通过；7901=0、单次 261ms，PROJECT=3、单次 105ms；两个真实计划。均不是 p95。
3. [R2 原排名实测](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Plan-RESULT.json)：100000～100009、degree=6，单次 353ms。
4. [R2 完整洞察失败](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/Real-20260929T001613649/Semantics-RESULT.json)：七个小场景通过，PROJECT 第十条 LIST 3024/HY000；后续组/20+20 采样未运行。
5. [LIST 诊断说明](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/LIST-EXPLAIN-DIAGNOSIS.md)、[计划](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/EXPLAIN.json)、[原 SQL 与绑定](D:/知识图谱/docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/TARGET.json)：节点/边 ALL、边关联估算 232927149、临时表/filesort，isolatedLimit=10。估算不是实测；计划生成 3ms 不是 LIST 性能。诊断首轮工具加载失败未发 EXPLAIN，第二轮仅一次实际 EXPLAIN；异常留痕不删除。

原 I2 和 R1 证据通过清单追溯：R1 排名已改善但完整洞察在 COUNT 超时；R2 COUNT 已改善但在 LIST 超时。不能把三次不同版本/不同语句的失败混作当前三个 SQL 都仍超时。

只读参考源码：当前仓储、QualityReadRepository、QualityAgentToolProvider 和冻结 I2MySqlSupport / I2QualityInsightRankingTest / I2QualityIsolatedCountTest / I2ScaleQueryTest / I2QueryBudgetTest。主项目集成测试只读，不能为领域离线测试启动它们。

## 4. 全部查询审查清单

以下 Q 编号表示当前基线职责，不预设补修后的 SQL 算法；当前共十次查询。报告必须逐项覆盖，不能只填 Q10。

| 编号 | 当前职责 | 必须保留的口径 |
|---|---|---|
| Q01 | 可见节点总数 | 原范围内去重节点；不新增节点 active 过滤 |
| Q02 | 可见有效边总数 | active 且双端可见；每条真实边计一次 |
| Q03 | 可见资料总数 | 原主库或 topic assignment 可见性，资料不因多归属重复 |
| Q04 | 待审实体候选数 | PENDING 且所属 job 状态在原四状态集，受资料范围约束 |
| Q05 | 待审关系候选数 | 同上；与 Q04 相加，不按跨候选表相同 ID 去重 |
| Q06 | 节点类型分组总数 | 完整可见节点分组口径，不能从最多 50 条类型列表推算 |
| Q07 | 类型分布列表 | 每类型完整计数，按 COUNT 降序/node_type 原数据库顺序，LIMIT 50 |
| Q08 | 节点度数排名 | 真实边计度、自环一次、零度节点、degree 降序/数值 ID 升序、topLimit |
| Q09 | 孤立节点总数 | 完整孤立节点数，不受 isolatedLimit/topLimit 截断 |
| Q10 | 孤立节点列表 | 与 Q09 同口径，ID 升序、isolatedLimit，NodeItem.degree=0 |

每项记录：当前 SQL/参数/调用位置、是否使用共享片段、已有真实证据与缺口、范围过滤/去重/连接与聚合成本、结果映射/Long/排序/边界、决定（修改/保留/待验证/需另批）、理由及对应回归。

同时检查整个调用的往返次数、重复工作和组合耗时风险，但不得为了减少条数丢失统计、转成近似值、加载全图到 Java 算结果、持久缓存旧值、吞异常或绕开权限。不得给未变化内容做全文件格式化。

查询数量和私有执行组织不是新增公开契约：如确有性能依据需要调整，必须给出 Q01～Q10 的前后职责映射、语义等价说明及测试影响；不能单纯为统一风格合并/改序。任何查询异常均向上传播，异常后不继续后续仓储查询、不返回伪成功。

## 5. 不变语义和边界

1. 原 PROJECT/SELECTED 范围与拒绝规则保持。资料/节点维度不支持时仍在 SQL 前拒绝；不得扩展工具能力或引入自动全项目回退。授权过滤与所有聚合、排序、截断口径一致。
2. 库选择按原并集；共享节点不重复。有效边须 active 且两端均在可见节点集中，范围外边不影响排名或孤立性。当前可见节点不因 inactive 被新增排除。
3. 自环计一次度数；反向边、不同关系类型平行边是不同真实边分别计数。零度节点可以入排名；绑定与返回均保持 Long，不截为 int。
4. 无边、仅 inactive 边、仅范围外连接、空库、多库共享场景保持原结果。孤立 COUNT 对全体计数，LIST 只截断展示，不从 LIMIT 结果推总数。
5. 待审 job 状态精确保持 AWAITING_REVIEW / AI_REVIEWING / FAILED / CANCELLED，候选状态 PENDING；不混淆 AI 判断、人工决定或入库结果。
6. 保持 Insight/NodeItem/TypeCount 返回结构和工具 coverage/truncated/限制处理；不把缺失值、null 类型或默认值偷偷改口径。保留字符串排序/数据库 collation 的既有行为。
7. 不新建事务快照/隔离级别保证，不改连接池/超时设置，不使用后台并行执行、异步延后或重试掩盖单条慢查询。四并发只读不是并发写入一致性验收。
8. 不引入 CREATE/TEMP TABLE、DDL/DML、权限扩大、数据库统计维护、外部服务、模型或缓存服务。

## 6. 正式实施后的领域文件边界

仅在原 02 工作树内开放以下文件；接令阶段全部只读：

| 文件 | 后续正式实施允许范围 |
|---|---|
| backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java | insight() 内经本轮分析确认需要的 SQL/私有执行组织；必要的 insight 专用私有 helper。nodeCte/scopedCount 只有证明其调用均属 insight 且有必要时才动；列出影响查询，非 insight 方法原样 |
| backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepositoryTest.java | 仅 insight 相关 SQL/绑定断言的必要调整与补充；candidate、chunkHash 等无关方法不改 |
| backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightRankingRegressionTest.java | 仅对应有依据改动的 SQL 预期或必要测试结构；原排名语义与其他未改查询断言保留 |
| backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightIsolatedCountRegressionTest.java | 同上；COUNT 的完整性、Long、范围、错误传播保护不得减少 |
| backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightPerformanceRegressionTest.java | 新增整体审查后的回归；不是 mock 计时性能证明 |
| docs/assistant-team/reports/02-quality-I2-R3.md | 新增阶段报告，必须包含第 9 节三类清单与全查询矩阵 |
| docs/assistant-team/reports/I2-R3/** | 只新增本轮离线测试/前后 hash 的脱敏证据，独立轮次文件，不含源码副本、凭据或可执行环境脚本；具体文件须交付清单列明 |

现有共用 reviewScope/insightScope、visibleNode、documentFilter、bind 及其他公开查询方法保持只读；不可通过影响别的方法扩张任务。需要改真实共享逻辑时先列方案申请，不借 insight helper 重定义授权/归属规则。

QualityReadRepository/QualityAgentToolProvider、核心、source、其他领域、业务 Review/Insight 服务、构建依赖、迁移、索引、API/前端、全部主项目文件和旧 W1/R1/R2 报告均不在修改范围。
新测试/报告/证据目录准备时未占用；接令核对后不得覆盖来源不明内容。若需新增其他源码/测试文件，先明确需求和路径申请。

旧测试中字符串、查询次序/数量等实现形状断言如因有依据的改动需演进，逐条记录“旧断言—新断言—仍受保护的业务语义—必要原因”。不能删除整个保护方法或把精确验证改成宽泛 contains 来让测试变绿。独立预期不得调用新生产私有构造器自证；未受影响断言原样保留。

## 7. 当前前像与保护

规范化方式：UTF-8 解码，CRLF 转 LF，保留结尾换行，不 trim。准备时主项目与原 02 四份文件全部匹配：

| 文件 | 规范化 SHA-256 |
|---|---|
| JdbcQualityReadRepository.java | c977e453d407aa150404c8f5acecddb6a9dfd63ae27692acb4e057840a3bcc15 |
| JdbcQualityReadRepositoryTest.java | c99ac56054eee8f01d20aa3cac9f9f11be3c3a663d5aaeb5919bb5c956b9dabc |
| JdbcQualityInsightRankingRegressionTest.java | 454ad390e192749f41c5759ba55b7e4915ce203296ced09def72bc4aabcc25d6 |
| JdbcQualityInsightIsolatedCountRegressionTest.java | 1fcb245eaac857476c7a8bb6c4d854f6bfea649d347079291029bb88fca10c66 |

原 02 W1 16 项冻结依赖仍只读。旧报告、失败日志/计划、原 179 项基线及历史 expected hash 全部保留；未来通过新 overlay 记录改动，不能倒改历史清单。主项目现有 R2 保护器不知道本单新增任务文档；本阶段不得运行旧保护器后忽略新增路径失败，后续总控另建受控增量边界。

## 8. 离线验证计划（正式授权后）

不开 Spring、不连接 MySQL、不使用账号或真实模型、不新增依赖。

原 02 在自己的 backend-java 中分组执行，显式选择测试类，使用每轮唯一 surefire.reportNameSuffix，并将脱敏摘要/hash 放入本单证据目录；不覆盖旧 XML 后声称已保留：

```powershell
# 原核心与质量域：历史 68 场景
.\mvnw.cmd -o '-Dtest=AssistantRuntimeTest,DecisionParserTest,QualityAgentToolProviderTest,QualityEvidenceVerifierTest,JdbcQualityReadRepositoryTest' '-Dsurefire.reportNameSuffix=<本轮唯一编号>-original' test

# R1 历史 15 场景、R2 历史 10 场景分别运行
.\mvnw.cmd -o '-Dtest=JdbcQualityInsightRankingRegressionTest' '-Dsurefire.reportNameSuffix=<本轮唯一编号>-ranking' test
.\mvnw.cmd -o '-Dtest=JdbcQualityInsightIsolatedCountRegressionTest' '-Dsurefire.reportNameSuffix=<本轮唯一编号>-count' test

# 新整体回归 N 项，按真实结果统计
.\mvnw.cmd -o '-Dtest=JdbcQualityInsightPerformanceRegressionTest' '-Dsurefire.reportNameSuffix=<本轮唯一编号>-whole' test
```

若在现有测试类追加用例，单独列出追加数，不能把“68/15/10 历史基线”直接替换成更大数字而不说明。参数展开、方法定义、执行轮次、红灯/重跑分别记录，不累加重复覆盖。

测试至少覆盖全部 Q 职责的生成 SQL/参数与结果映射；范围先于聚合和截断；空/无边、自环、反向/多类型边、多库共享、越界/inactive 边、零度/大 ID、不同 topLimit/isolatedLimit、COUNT 与截断独立、类型统计上限、多库资料/候选统计和非法范围。
任何被改实现须有针对性缺陷/退化保护及可说明的独立预期。SQL 异常立即传播，后续仓储查询不再执行。原 SQL 文字不同不等于语义回归；mock 返回预设数字也不证明 MySQL 算对或更快。
离线时间不能替代真实 MySQL 预算；真实 LIST 超时尚未解除的结论持续到总控复验。

## 9. 原 02 交付必须附的三类清单

报告首先给出 Q01～Q10 全覆盖矩阵，再明确三类清单（即使某类为空，也写“无”及依据）：

① **实际修改及原因**：Q 编号、文件/方法、前后 hash、确认问题的证据、修改必要性、影响 SQL/断言、语义等价、对应回归与未验证项。
② **检查过但未修改的查询及原因**：逐条列举，说明现有证据、审查结论、保留理由；证据不足明确写“真实性能待验证”，不得统称全通过。
③ **发现但需另批的事项**：索引/迁移/公开契约/跨领域/共享逻辑/权限等方案的目标、依据、影响、风险、替代路径及是否阻断本阶段。只有建议，没有实施。

还须包含：

- 实际文件修改者、测试运行者和具体轮次、模型要求与确认来源；真实 MySQL 验证者本轮为“无”。
- R1 原作者是此前总控；R2 是原 02，本次 R3 实际作者按真实操作记录，不倒改历史。
- 需要总控整体验证的 Q→SQL/绑定映射；如私有查询数量/位置变动，指出旧集成测试依赖的定位断言及适配需求。02 不修改主项目集成测试。
- 可信证据引用、失败历史、全部新增证据路径、冻结/非范围文件保护；不包含账号。
- 完成后停止，不自行汇集、不连接数据库、不进入下一阶段。

## 10. 总控后续一次性整体复核与验收方案

这是后续范围说明，本轮不执行，也不由接令指令自动触发。

1. 对原 02 的整个交付批次做一次增量技术复核：检查三类清单、十项覆盖、必要差异、语义/权限/测试等价及待审批事项；不拆回“LIST 一轮、下个 COUNT 一轮”。
2. 复核无阻断后，按阶段授权受控汇集必要文件，核对主项目前像，建立 R3 overlay；不覆盖其他在途修改，不改变历史 expected hash。
3. 统一回归按原主项目 154 + R1 15 + R2 10 和本轮实际追加场景分别报告；修改前已有 179 个去重展开场景是保护基线，不得弱化/跳过。
4. 真实 MySQL 整体验收不只测新 LIST：先按 Q01～Q10 实际映射对 PROJECT、7901 和小型选库/并集/空库进行适用的逐条执行计划、SQL 正确性与耗时核对，再验证完整 insight 结果与调用预算。缺少旧逐条计划时如实标注，不伪造“之前已通过”。
5. 保持原实例、原 7901 规模/R1 语义样本、原 reader 权限、连接 3000ms/socket 4000ms/statement 2s/session SELECT 1500ms。索引/迁移/统计维护/样本新增与写入不因本单自动获准。
6. 保留既定 20 次单线程与 20 次四并发组合读取标准：quality.insight + research.searchNodes，每组按 20 个值排序第 19 项计算 p95，阈值 3000ms；不删慢值、不改预算、不减少样本。其他既定回归与预算标准保持。
7. 对失败按统一批次报告所有已取得结果/缺口。即使多条需要补修，也统一退回原 02，不在总控复验现场改领域 SQL；具体有界逐项诊断和失败收尾顺序在接收交付后的集成计划中写清，不拿失败后未测项目当通过。
8. reader 静态窗口提供 34 表数据/DDL/binlog 一致、loader 锁定、无残留 reader 查询和正常停机证据。身份、权限、数据前像不符等安全阻断立即停止，不现场纠偏。
9. 整体复验完成后停止，交负责人最终验收。并发写入一致性、物理长原文等原 I2 剩余事项不自动通过；不进入 I3/W2、真实模型、API、前端或部署。

## 11. 接令检查与停止条件

原 02 只做一次接令检查：核对工作树/分支/既有状态、四个前像、三个新增位置是否未占用；阅读新任务与新增关键证据；确认整体审查范围、三类清单、测试与只读限制；指出文件/契约/权限阻塞。此时不要求完成整体性能审查，不要求选择 SQL 方案，不创建报告。

如此前同版接令已完成且上述内容未变，复用已有结论，不重新接令。接令结束回复：
“**I2-R3 整体性能审查接令检查完成，尚未开发，等待正式执行指令。**”

正式实施过程中如遇主/子树前像不符、未知修改、需越界文件/共享契约/索引/迁移/权限调整，停止相关变更并汇总汇报；不以获准审查整个 insight 为名扩大到质量域全部业务。

## 12. 本轮总控实际工作

只编写本任务单、149 份既有证据路径/hash 索引和可转发接令文本，核对四份领域文件两端前像与新增路径。没有开发、改源码/测试、编译、运行回归、启动子任务或连接数据库。总控实际模型/推理强度未核实；原 02 尚未接收本轮新指令。
