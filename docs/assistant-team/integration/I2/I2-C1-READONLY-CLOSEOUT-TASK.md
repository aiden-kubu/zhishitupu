# I2 C1 现有数据整批只读补验任务

2026-09-30，总控准备；执行者为**原 06「集成测试」**。本文件是待负责人一次批准的执行范围，不是已获准开库或运行测试的记录。批准后直接正式执行，必要门禁无异常即继续，不另设接令审批。完成后停止，总控审核，负责人验收。

## 1 目标与已验收基线

03 outgoingEdges 候选修复已完成，并通过当前合法真实场景验证；V14～V16 及新性能组仍有覆盖缺口。本批只补现有数据、原权限与原预算可完成的部分，不放宽每个 Scope 维度最多 1000 个 ID 的契约。

复用 [R4 阶段验收](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-OWNER-ACCEPTANCE-20260930.md)及 `R4Verify-20260930T104031320` 已审核结论：203 项离线回归、14 项真实边查询与 14 次 EXPLAIN、旧质量洞察与节点搜索组 40 个样本均不重跑。旧 p95 为 1355/1920ms，不算成本批结果。既有质量 Q01～Q10 验收及已通过的原文归属、故障变体、权限拒写和超时保护也直接引用，不重复制造新覆盖。

本批不汇集新领域补丁。只新增 06 集成验证代码和证据；研究、质量、资料、学习、测评、公共契约及其单元测试全部只读。领域异常集中退回总控，不现场修复。

## 2 执行前门禁与允许文件

原 06 工作树为 `D:/知识图谱-worktrees/assistant-06-integration-test`，分支 `codex/assistant-06-integration-test`，HEAD `86d1e1fcff6b7b492faec3d499837a42246df320`。会话默认目录不同不等于工作树变化；命令显式指定 workdir，编译子进程明确在 `D:/知识图谱/backend-java` 运行。

模型要求 GPT-6.1 Sol / High；负责人于 2026-09-30 已确认原 01～06 桌面设置。记录该来源，不要求重复确认，不把它写成工具独立回读；后者仍写“实际模型未核实；实际推理强度未核实”。

本批允许新增以下文件，已有文件不得覆盖。路径前像及输入 hash 见同目录 `I2-C1-READONLY-CLOSEOUT-BASELINE.json`。

| 位置 | 允许新增及用途 |
| --- | --- |
| 主项目 `backend-java/src/test/java/com/knowledgegraph/assistant/i2/` | `I2ReadOnlyCloseoutTest.java`：C01～C12 与 P01；`I2ReadOnlyCloseoutConnectionTest.java`：C13；`I2ReadOnlyCloseoutHarnessTest.java`：新验证驱动离线自检 |
| 06 工作树 `docs/assistant-team/integration/I2/C1-READONLY/` | `Invoke-C1ReadOnly.ps1`、`Verify-C1Baseline.ps1`、`C1-OVERLAY.json`；本批 `evidence/<唯一runId>/`、`archive/` 中的日志、JSON、XML 副本、只读校验及归档材料 |
| 06 工作树 `docs/assistant-team/reports/` | `06-integration-I2-C1-readonly.md` |
| 构建与受限证据 | 主项目 `backend-java/target` 正常构建输出及唯一后缀 XML；`D:/知识图谱-i2-env/restricted-evidence/I2-C1/<runId>/` 的受限历史/本批日志副本；原实例正常运行日志 |

不得修改旧 `I2ResearchEdgeVerificationTest.java`、旧 R4 runner、`I2MySqlSupport.java`、POM、业务配置、冻结样本 SQL、领域源码/测试或治理文件。旧 V14～V16 的参数、失败记录和 expected hash 保持原样；本批用新 ID 记录合法替代范围，不把旧失败改绿。

保护基线承接顺序：旧 R4 `PROTECTION.json` 的 817 项 → 已批准的 `20260930-sol61-high-and-r4-acceptance/GOVERNANCE-OVERLAY.json`（五文件后像、五份旧版归档、验收记录及该 overlay）→ 本任务、指令、基线三个新增文件 → 本批白名单。各层显式桥接，旧清单不改，不豁免整个目录。用 NUL 分隔的 Git 文件列表同时覆盖已跟踪及未跟踪文件；未知新增、删除、修改先停报，不自行认可。任务准备不要求重复全文审查；执行安全门禁核对版本不等于重复技术审核。

## 3 原环境和不变预算

- MySQL 8.4.11；UUID `8c6cfcb3-ba49-11f1-bf64-005056c00001`；`127.0.0.1:33078`；库 `kg_assistant_i2_20260927_r01`。
- 实体 `D:/知识图谱-i2-env`，目录联接 `D:/kg-i2-env`；不移动、初始化、重置、装载或改配置。
- 7901 的 10000 节点、30000 边、2000 资料、20000 chunk 全部保留。合法请求只选其中 1000 个文档，不是删除或缩小数据库规模。
- reader 原 SELECT 权限、loader 锁定保持；不获取/使用 loader 凭据，不运行 INSERT/UPDATE/DELETE/DDL 或重复拒写探针。已有权限证据沿用，运行前后核对实际 grants。
- JDBC URL 沿用 `jdbc:mysql://127.0.0.1:33078/kg_assistant_i2_20260927_r01?sslMode=REQUIRED&connectTimeout=3000&socketTimeout=4000&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`；连接 3000ms、socket 4000ms、statement 2s、session 1500ms；P01 组合 p95 上限 3000ms。不得提高预算、换端口/库/账号、关 TLS 或开启重试。
- 已批准的原环境管理连接仅用于身份、权限、快照、日志、残留查询核对和正常停库，不传入 Maven，不执行业务写入。测试子进程只接收 reader 凭据和本批授权标识，凭据不写命令行/报告。

## 4 固定范围及独立预期

所有集合去重且按数值记录，ScopeMode 为 SELECTED。未列节点 ID 时为空集合（不限），不是绕过文档或库授权。

| 名称 | 库 ID | 文档 ID | 文档数量 |
| --- | --- | --- | ---: |
| S1000 | `{7901}` | `300000..300999`，两端包含 | 1000 |
| M1000 | `{7101,7901}` | `{7201,7202}` 加 `300000..300997`，两端包含 | 1000 |
| AB | `{7101}` | `{7201,7202}` | 2 |
| LIB | `{7101}` | 空集合 | 0 |

预期依据冻结 I1/R1 样本 SQL、规模生成记录及最新 34 表封存快照，不从待测实现的输出反生成预期。7901 无正式边证据，因此 S1000 仅为空结果负载；M1000 的非空边来自保留的 A/B 样本，不能冒充密集正式边证据负载。

### 4.1 研究补验

| ID | 固定调用 | 独立预期 |
| --- | --- | --- |
| C01，替代 V14 目标 | `outgoingEdges(S1000,100000,10)` | 完整 ID 列表 `[]`，hasMore=false |
| C02，替代 V15 目标 | `outgoingEdges(M1000,7701,10)` | 完整 ID 列表 `[7801]`；7701→7702、active、来源可用、hasMore=false |
| C03，替代 V16 目标 | `incidentEdges(M1000,7701,10)` | 同 `[7801]`，保留真实 7701→7702，hasMore=false |
| C04 | AB 下真实 `research.find_paths`，sourceNodeId=7701、targetNodeId=7702、maxDepth=1、maxPaths=10 | 一条路径且只有边 7801；direction=`stored_source_to_target`；truncated=false、noPathWithinDepth=false；样本未提供人工复审属性，保留 PARTIAL 和 `GRAPH_SOURCE_OR_REVIEW_UNVERIFIED`，不将引用有效当人工核验 |
| C05 | 同工具与 AB，sourceNodeId=7702、targetNodeId=7701、maxDepth=1、maxPaths=10 | paths 为空、truncated=false、noPathWithinDepth=true、方向标记不变；只证明此范围与深度无路径，不证明全图无路径 |

C01～C03 各一次业务调用和一次 `EXPLAIN FORMAT=JSON`，共最多 **3 次 EXPLAIN**。从未修改仓储截取实际 SQL/命名参数，在 JDBC 边界记录展开 SQL、逐位绑定及类型；不得手写替代查询、去条件、加 hint 或 EXPLAIN ANALYZE。C04/C05 验证此前未真实覆盖的工具路径结果，内部必要数据库读取不另算新增案例。其余项目不追加诊断探针。

### 4.2 质量与原文交接边界

C06～C09 均在 AB 下调用真实 `quality.read_candidate_sources`：jobId=7501、candidateKind=`entity`、candidateId=7601、maxChars=1200。expectedRefs 每项只有 chunkId、expectedHash。该候选全部证据为 `{7401,7402}`；所有返回的 semanticSupport 保持 `not_evaluated`。

冻结 hash：7401=`ababdb0a59f50f87c8b435ab6d6374822906848cbdf8b2416da9d16c2589c83a`；7402=`2c1654cf600f667a2bae2d87f8a49878c54533f9a83f864417147026a0ba22de`；7403=`a2e327c6053f1dc94c078d12a098947e81ef2867826909a78fe04b26cee1a7b4`。

| ID | expectedRefs | 独立预期 |
| --- | --- | --- |
| C06 | 仅 7401 和其正确 hash | PARTIAL；1 条有效证据；requestCompleteCoverage=true、candidateEvidenceCompleteCoverage=false、completeCoverage=false、candidateEvidenceUnchecked=1 |
| C07 | 7401、7402 及各自正确 hash | OK；2 条不同 chunk 证据；两种覆盖及 completeCoverage 均为 true，unchecked=0；按 chunkId 核对内容、documentId、unitIndex、locator 与 hash |
| C08 | 7401，expectedHash 为 64 个 `0` | ERROR/SOURCE_UNAVAILABLE；check.status=STALE；无有效证据、不返回该原文内容；两种覆盖为 false。只模拟客户端陈旧 hash，不修改数据库 |
| C09 | 7403 和其正确 hash | ERROR/SOURCE_UNAVAILABLE；check.status=NOT_CANDIDATE_EVIDENCE；无有效证据、无 7403 内容；同一授权范围的 B 文档不能冒充该候选的 A 证据 |

仓储及 SourceLocator 必须是真实 JDBC。可按现有构造器提供不使用的空宿主适配，但不得 mock 数据/结果或宣称生产可信宿主已接入。C08 不是“内容被篡改但存储 hash 未变”的验证；后者仍待另批。

### 4.3 学习路径与测评基线异常

C10～C12 均在 LIB 下调用真实 `assessment.read_context`，pathId=1000。现有路径的 current step=8101、locked step=8102。旧正常路径读取不重测；C10 读取当前指纹仅作新负例的必要前置。

| ID | 额外输入 | 独立预期 |
| --- | --- | --- |
| C10 | `snapshotFingerprint` 使用真实当前指纹，只确定性改动一位小写十六进制字符，格式仍合法 | ERROR/STALE_PATH_BASELINE |
| C11 | stepId=8102 | ERROR/STALE_STEP_BASELINE，不将 locked 当 current，不推进阶段 |
| C12 | stepId=999999，冻结样本内不存在 | ERROR/STEP_NOT_IN_PATH，不返回可用于作答或判分的凭证 |

三项不能证明真实并发路径变更、错误持久化外键或正式评分可用；这些不由客户端参数负例替代。

### 4.4 连接异常

C13 在本批其余真实项目结束、快照完成并正常停止本批实例后执行。先只读确认原实例进程和 33078 监听均不存在；只向原 `127.0.0.1:33078` 做一次 reader JDBC 建连/仓储调用尝试，原 URL、TLS、连接/socket 预算不变，无自动重试。不得启动新实例、改服务配置、断网/防火墙、强杀进程、用错误密码或改成其他端口。

使用独立测试类，不继承会自动连接/发身份 SQL 的 `I2MySqlSupport`。通过受计数的真实 DataSource 调用一次未改仓储方法，验证 Spring 数据访问异常及 SQLState `08` 类/连接拒绝原因，记录调用计数、真实异常链和耗时，确认不是成功空列表。若无真实连接尝试，或在尝试前由自检抛错，不算通过。业务 SQL 执行数应为 0。

若端口被新进程占用，安全停止，不向身份不明服务连接；若本批未成功拥有并正常停止原实例，C13 标依赖阻断。它只覆盖停库后的连接拒绝传播，不证明网络黑洞超时、查询中断线、连接池恢复或生产宿主总预算；不为此额外启停/重连。

## 5 新性能组 P01

C02、C03 业务结果符合独立预期且安全门禁通过后运行；EXPLAIN 普通失败单独记缺口，不自动阻断独立的正确性/性能结果。每个样本顺序调用 `outgoingEdges(M1000,7701,10)`、`incidentEdges(M1000,7701,10)`，核对完整 `[7801]`、方向、状态、来源及 hasMore。性能计时不夹带 EXPLAIN。

20 次单线程，加四并发**总计 20 次**，合计 **40 个组合样本、80 次仓储调用**。不是每个线程 20 次；不预热后删值，不剔除慢值，不补选最好轮。每样本保留序号/模式/起止、两次调用各自状态与耗时、组合耗时、参数范围、错误。组合耗时只计算一次，落盘值即 p95 输入。

每组 20 个完整样本按耗时升序第 19 个为 p95，分别不超过 3000ms，且单条调用不得触发原预算超时。出现调用错误留存已运行和在途收尾结果，其余依赖样本标阻断；少于 20 个完整样本不报告合格 p95。不得自动重跑整组或失败项。旧 quality/search 性能组本批执行数为 0，不能合并统计。

## 6 实施顺序及验证命令

批准后原 06 一次完成：门禁与保全 → 新驱动及离线自检 → 原实例启动/身份与静态窗口 → C01～C12 及三份计划 → P01 → 后快照/残留查询/正常停库 → C13 → 证据核对和报告。仅 P01 内部允许四并发；01～05 不启动。

1. 先核对本基线及治理桥接；保全旧 XML 与累计日志。旧轮历史 46 份加新增 47 份按索引去重核对，预计 93 份；实际数量与缺项逐份记录，不靠数量替代 hash。日志旧前缀 13,712,701 字节，raw SHA-256 `2542260dcc038c870cdeb613b954aa73ac01ab3b9ad157c47e02beeb550f17a6`。受限副本仅当前用户可读，不把原日志正文放公开报告。
2. 新 overlay 固定测试、runner、校验器和所复用函数的精确版本；复用已验证输出收集/收尾函数时核对封存版本，不执行旧 runner 顶层。只修改本批新增实现。离线自检至少覆盖 1000-ID 集合和 1001 拒绝、独立预期的拒错、失败/未执行分开、p95、依赖与安全停点、证据缺失不得成功。自检与编译不计真实覆盖，旧自检未变不重复运行。
3. 主项目 Maven wrapper 使用离线依赖和唯一 Surefire 后缀，不 `clean`。命令形态为 `./mvnw.cmd -o -DskipTests test-compile`；`./mvnw.cmd -o "-Dtest=I2ReadOnlyCloseoutHarnessTest" "-Dsurefire.reportNameSuffix=<runId>-harness" test`。真实阶段只选新 `I2ReadOnlyCloseoutTest#<固定方法>`，每个案例一次；C13 另选 `I2ReadOnlyCloseoutConnectionTest`。最终准确命令、方法映射、数量和实现 hash 在本批 overlay 中固定，不能用通配符带起旧测试。
4. 不以本批批准为由重跑 203 项、旧 14 项矩阵、旧 40 样本、历史故障变体、拒写和超时注入测试。新增驱动编译/离线红灯可在白名单内修正并保留各轮证据；不可弱化本任务独立预期，真实批次失败不自动重试。
5. C01～C12 是 12 个真实功能场景；C13 是 1 个真实连接失败场景；P01 的 40 样本和 3 次 EXPLAIN 单列，不算 53/56 个业务测试定义。实际定义数、展开数、执行次数、重跑次数分别报，保全副本不计覆盖。设置授权门禁防止无批准的普通 Maven 扫描连库；本批显式选择器出现 skipped 不算完成。

## 7 零写入与失败处理

每轮必要身份/权限、预算、实例所有权、34 表数据/DDL、同次启动 binlog 前后、loader 锁定及残留查询核对必须执行；这是本轮安全证据，不是重复业务回归。启动后入口快照须与 R4 最终窗口后像一致。原环境日志正常追加与业务零写入分开报告。

身份、权限、样本漂移、未知修改、前像不符、预算失效或零写入异常立即停批并安全收尾，不擅自补环境。普通断言/查询失败、单项计划失败或覆盖缺口逐项留证，继续其他独立安全项目，不再次请求整批授权，不现场追加诊断。确实缺样本则标未覆盖，不临时装载。尚未执行的依赖项保留 `blocked_dependency` 与 null 耗时，不补造结果。

无残留 reader 查询后，只正常停止本批启动且身份已核实的原实例；不强杀、不停止未知实例、不销毁数据。证据写盘失败、日志收集截断或必需文件缺失不得报完整成功；保留首个错误及收尾错误。最后核对保护文件集合、内容和历史日志前缀，生成最终大小/hash 索引。

## 8 下一批待单独批准

以下只登记，不执行、准备装载或变更数据；下一批先给精确数据变更清单、独立预期、账号/时窗和回收办法，再由负责人批准。

| 项目 | 本批不能替代的验证及所需授权 |
| --- | --- |
| 密集正式边证据、文档范围超过十条合格边的分页、两端/边跨资料取证、Long ID 专项 | 新增或修改精确节点/边/证据样本；保持请求每维最多 1000，不放宽契约 |
| 质量超过 50 种关系类型、真正可计入 pending 的复审作业与候选 | 需要对应的真实数据组合；不把当前 COMPLETED 作业里的 PENDING 候选当此负载 |
| 物理 LONG_A 原文链路 | 需要真实物理文件及其存储/解析状态映射；已有 DB 长文本变体不等价 |
| 内容变了但存储 hash 未更新、错误持久化阶段归属等损坏样本 | 需要受控修改数据；客户端错误 hash/不存在 step 负例不能代替 |
| 并发 loader 写入与 reader 一致性、真实路径/来源在读取中变更 | 需要 loader 解锁/写入及独立窗口；不在静态零写入窗口中混做 |
| 装载失败恢复、重置、销毁或清理验收环境 | 涉及环境和数据生命周期，逐项另批；本批只正常停机，保留原数据 |
| 其他连接故障，如网络黑洞、查询中断线、连接池恢复 | 需要先说明环境影响及额外授权；本批 C13 仅连接拒绝，不扩大事故注入 |

索引、迁移、公共契约、扩大权限、领域修复一律另报，不包含在“测试驱动修改”里。真实模型、生产宿主/API/鉴权/前端、部署及 I3/W2 均不在本批。

## 9 交付与停点

交付报告列出：实际新增文件及作者；基线/治理桥接；沿用旧结论与理由；C01～C13/P01 的通过、失败、阻断和未覆盖；三个实际计划；40 个实际样本及可复算 p95；预算、零业务写入、保全与收尾；准确命令、XML 定义/展开/轮次数；本批与下一批缺口；模型确认来源与回读实况。

预期最大完成范围为 13 个新增功能/异常场景、3 次 EXPLAIN、40 个新性能组合样本，实际数量必须由证据支持。C01～C03 的合法范围通过也不能倒改旧 V14～V16，或证明密集正式证据性能。任何结果都不自动代表 I2 整体通过。

本批集成实现、测试/真实环境与报告作者为原 06；领域作者仍为原 01～05 的既有记录；总控只准备任务和技术审核；负责人最终验收。完成即停止，不自动继续下一批、I3 或 W2。
