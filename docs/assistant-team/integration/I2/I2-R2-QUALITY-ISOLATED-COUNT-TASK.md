# I2-R2｜质量洞察孤立节点总数查询定向补修任务单

日期：2026-09-28。准备者：总控。拟执行者：原 02「质量治理」。

状态：**范围草案已准备，等待项目负责人批准新任务范围；尚未派发、未开发。** 本文件不是正式执行指令。原 02 已完成的是 R1 既有成果接手核对，不是本轮补修的接令检查或开发授权。

## 1. 接手复核和问题边界

负责人转回的原 02 接手回执说明可以承接当前基线、未发现文件/冻结依赖阻塞。总控本次只读复核：

- 原工作树 `D:/知识图谱-worktrees/assistant-02-quality`，分支 `codex/assistant-02-quality`，HEAD `bcd3b60ea4e6e18daad53f31787028a0f69bc6cf`。
- Git 可见在途修改为 1 个已跟踪修改和 30 个未跟踪文件；只记录状态，不据此推定全部文件作者，不清理。
- 三份领域文件在主项目和原 02 两端规范化 hash 一致；冻结依赖 16/16、R1 包内 `files` 12/12 一致；没有读取包内环境文件或连接数据库。
- 原 02 对领域所有权、真实作者、已有测试与未完成真实验证的理解无新增阻塞。此结论只确认接手准备，不认定缺陷已解决或 I2 通过。

当前已有真实错误明确发生在 `insight()` 的 **`isolatedTotal` 总数 COUNT 查询**，项目范围触发 MySQL 3024 / HY000。此前排名补丁及其领域测试由总控编写；原 02 从本次接手开始承接后续领域维护，不倒改历史作者，不为换作者重写排名。

本轮拟解决该 COUNT 的计算方式与定向离线回归，保持查询返回什么不变。**不同时修改孤立节点列表，不再修改排名，不修改共享过滤规则。** 列表是否成为下一处瓶颈尚未被当前错误证明；如果后续真实复验在列表或其他 SQL 失败，仍需停止并另行定界。

## 2. 执行分工和授权关口

1. 总控准备本单，负责人先确认新范围。
2. 负责人手动转发原 02 做本任务的增量接令检查：重点确认四文件范围、COUNT 与列表隔离、测试预期调整和当前前像。已读历史材料无需假装重新开发，但需要确认新边界并停止。
3. 接令检查通过后，负责人另行授权正式离线实施。只有原 02 可以修改本单获准的领域文件、运行离线测试和生成新报告。
4. 原 02 汇报并停止；总控技术复核，发现问题仍退回原 02，不代做。
5. 受控汇集、统一回归、真实 MySQL 复验由总控负责，须另获授权；原 02 不负责数据库，也不修改主项目。

模型要求 `gpt-6-sol / high`。当前用户已在桌面确认原 01～05 均为 GPT-6 Sol / High；工具独立回读未核实。报告注明确认来源，不将其表述成工具回读；若正式开工前设置发生变化或无法保证符合要求，先报告，不擅自换模型。

不创建/切换工作树，不创建替代任务，不启动新的 App Server，不启动其他子任务。主项目保持只读参考。

## 3. 必读材料

首次接收本任务单时完整阅读新增任务要求；已阅读且未变化的规则、任务内容、历史材料和失败证据直接引用已有结论，不重复全文审查。只核对新增四文件边界、COUNT/列表隔离、测试预期调整、相关当前前像和新增占用风险；发现具体依赖/契约变化再说明原因并扩大。若本任务接令检查已经完成且上述内容未变，直接回报已有结论，不重新开始。本次仅更新阅读方式，四文件开发范围及正式实施须另获授权的要求不变：

- [COORDINATION.md](D:/知识图谱/docs/assistant-team/COORDINATION.md)、[CONTROLLER-RULES.md](D:/知识图谱/docs/assistant-team/CONTROLLER-RULES.md)、[既有成果接手单](D:/知识图谱/docs/assistant-team/integration/I2/I2-QUALITY-OWNERSHIP-HANDOFF.md)。
- 原 02 的 [W1 任务单](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/assignments/02-quality-W1.md)、[W1 报告](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/reports/02-quality-W1.md)、[R1 离线报告](D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/reports/02-quality-I2-R1.md)及 [16 项冻结清单](D:/知识图谱/docs/assistant-team/references/02-quality-W1-DEPENDENCIES.json)。原工作树旧协调副本不能推翻主项目现行规则。
- 当前 [JdbcQualityReadRepository.java](D:/知识图谱-worktrees/assistant-02-quality/backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java)、[排名回归](D:/知识图谱-worktrees/assistant-02-quality/backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightRankingRegressionTest.java)、[原 JDBC 测试](D:/知识图谱-worktrees/assistant-02-quality/backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepositoryTest.java)。
- 真实 [Semantics-RESULT.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/Semantics-RESULT.json)、[Plan-RESULT.json](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/Plan-RESULT.json)、[失败停止记录](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/FAILURE.json)、[停机记录](D:/知识图谱/docs/assistant-team/integration/I2/R1/evidence/Real-20260928T101016147/SHUTDOWN.json)。
- [真实测试源码](D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2QualityInsightRankingTest.java)、[合成样本](D:/知识图谱/docs/assistant-team/integration/I2/R1/R1-FIXTURE.sql)、[R1 overlay](D:/知识图谱/docs/assistant-team/integration/I2/R1/R1-OVERLAY.json)、[R1 package](D:/知识图谱/docs/assistant-team/integration/I2/R1/R1-PACKAGE.json)，仅作只读证据，不执行脚本或装载。

## 4. 拟申请的四文件实施范围（尚未授权）

全部相对原 02 工作树 `D:/知识图谱-worktrees/assistant-02-quality/`：

| 文件 | 拟允许的精确变化 |
|---|---|
| `backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java` | 仅 `isolatedTotal` 的 COUNT SQL 构造；可新增只供该 COUNT 使用的局部 SQL 字符串或必要的私有纯 SQL 构造方法。调用仍使用既有绑定及 Long 读取，不影响其他查询 |
| `backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightRankingRegressionTest.java` | 只在 `nonRankingQueriesAndTheirLimitsKeepFrozenShape` 内更新孤立 COUNT 对应的一个预期 SQL 条目；可为该条目新增方法内独立预期字符串。其他预期、断言、测试方法和公共测试辅助方法不变 |
| `backend-java/src/test/java/com/knowledgegraph/assistant/quality/JdbcQualityInsightIsolatedCountRegressionTest.java` | 新增 COUNT 的隔离回归；不能覆盖已存在文件 |
| `docs/assistant-team/reports/02-quality-I2-R2.md` | 新增本轮实际作者、差异、保护、测试及未验证项报告；不能修改 W1/R1 旧报告 |

总控准备时两条新增路径均未占用，原 02 接令与正式实施前仍须复查。接令检查阶段不得创建它们。

### 4.1 严格保持不变的部分

- `isolatedWhere` 现有声明/字符串及孤立列表 SQL、排序、参数绑定、RowMapper 保持原样；COUNT 使用独立 SQL 构造，不能改共享字符串顺带优化列表。
- 排名 SQL/聚合、其 topLimit、排序、返回映射与已有通过的排名语义保持原样。
- `nodeCte`、`visibleNode`、`documentFilter`、`bind`、`insightScope`、`scopedCount` 及其他已有方法/统计保持原样。
- 完整 `insight()` 仍是原有 10 次查询及原次序；仅第 9 次的孤立 COUNT 文本变化，其余 9 条 SQL、参数和读取方式不变。不通过省略/合并返回字段或额外读取全图到 Java 中来绕过慢 SQL。
- 公共/领域 DTO、schema、宿主及 SourceLocator、其他领域、既有业务代码、构建配置、迁移、索引、预算、API/前端和主项目全部不在修改范围。

### 4.2 测试调整为什么需要单独批准

R1 的旧测试用一个列表精确断言 9 条非排名 SQL，其中包括旧孤立 COUNT。此次计划修复这一条，原字符串预期需要随已批准实现演进；不能保留错误预期，也不能直接删除整条断言。

只替换其中孤立 COUNT 的预期，其余 8 条非排名 SQL、总查询数、顺序、库范围绑定、类型上限 50 与孤立列表上限断言全部保留。新 COUNT 预期由测试独立构造，不调用生产私有 SQL 构造器来生成“预期”。排名其他 14 个展开用例及此方法中的其他断言保持原样。

新测试须补上 COUNT 本身的范围、结构、参数、结果读取和异常传播检查。任何超出上述最小测试调整的需要先报告，不能以“让测试通过”为理由放宽断言。

## 5. 当前前像

按 UTF-8 解码、CRLF 转 LF、保留结尾换行、不 trim 计算：

| 文件 | 修改前规范化 SHA-256 |
|---|---|
| `JdbcQualityReadRepository.java` | `fde4199bef96385ea87a3a0bfd671f170edd2501de1a2dab7adc8481b2757e52` |
| `JdbcQualityInsightRankingRegressionTest.java` | `78d6e56c7ca07cdbf574ae2437fff94cfbf9fd6b94ce9582e15daf0cbbbc8100` |
| 只读 `JdbcQualityReadRepositoryTest.java` | `c99ac56054eee8f01d20aa3cac9f9f11be3c3a663d5aaeb5919bb5c956b9dabc` |

当前 R1 报告及归档副本 raw hash 为 `21ceb14153dee7c3829046182254e37c0a95033b13d7efd1d278f4af5afc7da5`，不得改写。旧 I1、R1 清单 expected hash 均保持不变；本轮以后若获准实施，要单列旧/新 hash，不能用旧清单已含本轮改动来掩盖差异。

总控准备时记录主项目 615 个、原 02 工作树 436 个 Git 可见文件内容。原 02 正式实施前须自行记录其当时文件前像，保护未知和既有修改；只允许上节四文件范围变化。未来总控接收前仍将独立核对主项目前像。

## 6. COUNT 必须保持的业务语义

1. 孤立节点总数是可见节点中没有任何可见有效边相接的节点数，与 `isolatedLimit`、`topLimit` 无关，不能从截断列表或排名推导。
2. 所选库是并集，共享节点只出现一次；项目范围按当前可见性定义读取，不暗中加库约束。范围外节点或边不能影响结果。
3. 只有 active 且双端均可见的边影响孤立性。只连接范围外节点的边不能让当前节点被误判为非孤立；仅有 inactive 边的节点仍可孤立。
4. 一个有效自环足以使节点非孤立。反向边、多类型平行边、多个共享库归属不能重复扣减节点或使孤立总数变负；不把边数当非孤立节点数。
5. 不新增节点 active 过滤；当前范围内 inactive 节点仍按有无可见有效边判断。
6. 空库/空节点返回 0；有节点无有效边时返回可见节点数；大 ID 与 Long 总数不能截断成 int。
7. 完整静态样本上的总数应与独立预期、未改的孤立列表口径一致；列表仅按既有 isolatedLimit 截断，空列表不一定意味着总数为 0。
8. 不支持的资料/节点范围仍在 SQL 前拒绝，不能拓宽能力。JDBC 异常仍传播，不能吞超时并返回 0 或旧缓存。
9. 不新增多语句一致性快照承诺，不借此处理读取期间并发写入一致性；那仍是后续独立验证项。

实现算法和离线补修代码由原 02 提出并执行。总控在此规定不变语义、边界和验收方法，不代写领域 SQL。

## 7. 离线测试计划（正式实施另获授权后）

使用当前 Java/Maven 缓存，fake/mock JDBC，不启动 Spring 应用、真实数据库或模型，不新增依赖。命令工作目录为原 02 的 `D:/知识图谱-worktrees/assistant-02-quality/backend-java`。

分别运行并计数，不在接令检查阶段执行：

```powershell
# 原核心 + 原质量领域，历史基线为 68 项；源码和断言不变。
.\mvnw.cmd -o '-Dtest=AssistantRuntimeTest,DecisionParserTest,QualityAgentToolProviderTest,QualityEvidenceVerifierTest,JdbcQualityReadRepositoryTest' test

# R1 排名 15 项；仅上述一个 COUNT 预期条目按授权演进。
.\mvnw.cmd -o '-Dtest=JdbcQualityInsightRankingRegressionTest' test

# R2 新增数量以本次实际 JUnit 结果为准，不预先宣称通过数。
.\mvnw.cmd -o '-Dtest=JdbcQualityInsightIsolatedCountRegressionTest' test
```

新增用例至少区分以下验证层次：

- 捕获真实仓储生成的 SQL/绑定，核对项目/选库、范围先过滤、双端有效边、共享节点去重、自环/平行边的结构约束；不能仅在 fake 中手工返回预设计数后声称验证了 SQL 语义。
- 验证总数不带 LIMIT、不会从排名/孤立截断列表推导；Long 返回值及大于 32 位的 ID 参数绑定正确；非法范围不开始查询。
- 验证 COUNT 异常直接传播，不输出伪成功或启动后续列表查询；既有其他查询顺序、参数和原样结构保持。
- 设计空库、无边、自环、反向/多类型边、共享节点、范围外边、inactive 节点/边及截断的独立预期。若离线只能验证 SQL 结构和结果映射，报告明确这不是 SQL 引擎执行，真实语义由总控后续验收。
- 在全新测试中证明新设计能检测到目标旧结构；若保留红灯，注明结构回归红灯不是在 MySQL 复现 3024。不能为做红灯来回覆盖主项目或改其他领域。

按当次明确选择的测试类/XML 统计方法定义、参数展开场景、去重数、失败/错误/跳过及执行轮次。原 68、R1 15、R2 新增分别报告；失败重跑、同一项重复运行不算新增覆盖。旧 target 中未由当次命令产生的报告不能混入结果。

报告如记录多轮结果，保留首轮失败的具体用例与原因；不要把被后续覆盖的 target XML 称为完整留存的原始文件。如需要四文件以外的持久证据文件，先申请精确路径。

## 8. 总控后续真实复验边界（不在本单执行授权内）

原 02 交付并通过总控技术复核后，另申请受控汇集和真实复验。总控须为两个既有变更文件及新增测试建立新的增量记录，保留 R1 前像和失败证据，并单列已批准的治理文档变化；不改旧 expected hash，不运行旧保护器后忽略失败。

后续仍使用原 MySQL 8.4.11、UUID `8c6cfcb3-ba49-11f1-bf64-005056c00001`、`127.0.0.1:33078`、库 `kg_assistant_i2_20260927_r01`；不换实例，不重初始化，不向原 02 交接账号。

保留 7901 的 10000 节点、30000 边、2000 资料、20000 chunk，以及 R1 已有合成样本。连接 3000ms、socket 4000ms、statement 2s、session SELECT 1500ms 不变，不改索引/迁移、不缩样本。

总控届时需验证新 COUNT 的独立预期和真实执行计划、完整洞察及原排名无回归，再按正式获批计划采集全部 20 次单线程/20 次四并发结果、静态窗口零业务写入与停机证据。当前项目范围预期来自静态 R1 样本，不能在数据变化后未经核对直接套用。

如果保持原样的孤立列表或其他 SQL 出现新的 3024，立即停止并报告；不得就地修第二条 SQL、提高预算或把未执行采样标为通过。R2 离线完成不等于真实瓶颈解除，更不等于 I2 或网站上线。

## 9. 交付和停止条件

正式实施后的报告路径为 `D:/知识图谱-worktrees/assistant-02-quality/docs/assistant-team/reports/02-quality-I2-R2.md`，本轮准备和接令检查均不创建。

报告须列：实际修改者为原 02、总控此前 R1 代码的来源、精确文件/区域变化、前后 raw/规范化 hash、其余文件/16 项依赖保护、三组测试各自结果、实际模型确认来源、未执行的真实验证及下一步建议。总控本轮实际只做接手回执复核和任务单准备；不把未来原 02 开发写成已发生。

遇到未知前像/文件占用、需改列表/排名/共享方法、需扩大既有测试修改、需改索引/迁移/预算或依赖、范围和语义无法保持时，停止报告。不要“先顺手修完再说明”。

交付后停止，等待总控复核和负责人决定。不得自行汇集、连接数据库、推进剩余 I2、I3 或 W2。

## 附录：负责人批准范围后可转发的增量接令检查

> 原 02「质量治理」，请仅对主项目 `D:\知识图谱\docs\assistant-team\integration\I2\I2-R2-QUALITY-ISOLATED-COUNT-TASK.md` 做增量接令检查。原有成果接手已经完成，这次重点确认新任务四文件边界，不重复声称完成开发。
>
> 使用原 `D:\知识图谱-worktrees\assistant-02-quality`、分支 `codex/assistant-02-quality`，保留全部已有修改。用户已在桌面确认 GPT-6 Sol / High；工具独立回读情况如实记录。
>
> 核对当前三文件前像与新增测试/报告路径是否未占用，确认 COUNT 将独立修改、isolatedWhere 和列表/排名保持原样；说明 R1 测试只改一个 COUNT 预期条目、其余断言保留的理解，并给出新 COUNT 的离线回归计划及阻塞。
>
> 不修改任何文件、不创建报告、不编译/测试、不启动或连接数据库、不运行总控脚本、不启动其他任务。完成后回复“I2-R2 接令检查完成，尚未开发，等待正式执行指令”并停止；存在阻塞则列证据停止。本消息不是正式开发指令。
