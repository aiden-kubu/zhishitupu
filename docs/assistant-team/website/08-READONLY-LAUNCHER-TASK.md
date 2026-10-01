# 原08：隔离只读启动接线（正式执行任务）

日期：2026-10-01。负责人在总控提出原08先实施、原06后续真实联调的方案后回复“开始吧”。本任务是正式开发及离线验证指令，不是单独接令检查。必要门禁无异常就直接执行，完成后停止。

## 目标、执行者与基线

执行者：现有原08「智能助手 08｜只读宿主与 API」，GPT-6.1 Sol / High。工作树 `D:/知识图谱-worktrees/assistant-08-host-api`；分支 `codex/assistant-08-host-api`；HEAD `8544b19f89bbcf477b9b458be7f48117775ff621`。主项目仅作只读参考，不在主项目实施或汇集。

基线见主项目 `docs/assistant-team/website/08-READONLY-LAUNCHER-BASELINE.json`：30项直接依赖及9个新增目标路径。17项原宿主源码、5项测试/fixture等既有文件均只读。5项公共/领域依赖两端raw hash不同，但已核对仅CRLF/LF差异，规范化内容相同；使用各根自己的raw前像保护，不为统一换行改文件。既有四个未跟踪目录是已知状态，不据此清理或重复接手。

复用你已读且未变的长期规则与08审核结论；本轮新增必读：本任务、上述基线、`WEBSITE-READONLY-LIVE-PLAN.md`。必要时补读现行主项目规则，不全文重审旧阶段材料。总控审查发现默认main固定安装disabled宿主，启用缺口是验收启动接线，不是领域规则缺失。

## 允许新建的文件

仅在原08工作树新增，不修改任何既有生产或测试文件：

1. `backend-java/src/main/java/com/knowledgegraph/assistant/host/AssistantReadAcceptanceApplication.java`
2. 同目录 `AssistantReadAcceptanceConfig.java`
3. 同目录 `AssistantReadAcceptanceEvidence.java`
4. 同目录 `AssistantReadAcceptanceDataSource.java`
5. `backend-java/src/test/java/com/knowledgegraph/assistant/host/AssistantReadAcceptanceTest.java`
6. 同测试目录 `AssistantReadAcceptanceDataSourceTest.java`
7. `docs/assistant-team/reports/08-readonly-acceptance-launcher.md`
8. `docs/assistant-team/website/08-readonly-acceptance-launcher/STARTUP-HANDOFF.md`
9. 同交接目录 `overlay.json`

允许在同交接目录 `evidence/<唯一runId>/` 及本工作树 `backend-java/target` 写本轮日志、脱敏测试配置、XML/索引与编译输出。不得覆盖旧XML、旧报告、凭据或用户文件。需要其它源码文件或修改现有文件时先说明影响并停报，不顺带改POM、wrapper、frontend或任何领域实现。

## 实现要求：组合已有能力，不重新判断业务

- 新的、显式本地验收入口复用现有 `AssistantReadHostApplication.install`、`AssistantReadHostService.production`、`AssistantReadWiring.VerifiedReader` 和现有边界/注册白名单。默认main和HTTP契约保持原样；没有完整配置与可信证据时fail closed，不构造可用宿主。
- 不能依赖@SpringBootApplication/组件包扫描或旧业务Application启动。禁止自动发现legacy服务/数据源、SQL初始化、Flyway/Liquibase；不提供settings/模型/写工具入口。维持10项只读工具、确定性执行和内存run store的原限制。不注册04/05或正式测评。
- 本地单负责人授权来自受控服务端启动配置，不来自请求owner/header/profile、前端输入或通用业务配置。维持SELECTED范围及1000-ID契约；不扩大已有授权。与可信reader grant绑定，验证过期后拒绝，不把“有一个64位hash字符串”当作已验证权限。
- 定义交给06的最小启动/证据文件格式：精确实例版本、UUID、loopback地址/端口、数据库、reader身份、SELECT表范围、宿主grant、核验结果、核验时间/有效期及审核固定的原始证据SHA-256。加载时核对真实文件摘要、字段对应和有效期；允许的信任来源是未来06已核验且总控已审核、受限保存的本批证据，不接受HTTP自报verified或任意自生成证明。这轮仅用合成测试文件，不伪造真实GRANTS已通过。
- 真实身份固定为历史批准I2环境：MySQL8.4.11、UUID `8c6cfcb3-ba49-11f1-bf64-005056c00001`、`127.0.0.1:33078`、`kg_assistant_i2_20260927_r01`。不支持其它实例/远程连接/生产root账号；本轮不实时核验。配置/凭据在未来06受限文件中交接，禁止把密码放命令行、URL、报告或日志；不读取现有真实凭据。
- 新DataSource是连接/预算适配，不是SQL修复。沿用原I2连接3000ms、socket4000ms、statement2s、session1500ms；既有宿主层会设置3s，适配应确保底层不被放宽。只允许既有预算的会话设置，不支持任意init SQL、多语句、自动重连或备用写账号；外层现有SELECT/身份守卫继续生效。构造/解析配置本身不打开连接。用fake/mock验证预算和失败关闭行为。
- 默认仅绑定 `127.0.0.1`，端口使用显式验收配置，禁止公网监听或接管已有服务；无授权配置不能启用。不删除/强停任何既有进程。保持现有Origin/Forwarded/单负责人门禁，不为Vite跨端口方便而放宽CORS或伪造请求身份。
- 为06交接一个同源网站方案。可在新增Application内提供受控、可关闭的同源静态构建资源接线：只使用未来06明确指定且验证过的主项目frontend构建目录，GET/HEAD静态读取；无目录时不得自动寻找源码或其它磁盘目录；路径穿越/目录外资源禁止。`/api` 不可落入SPA回退，未注册旧业务写API仍拒绝。不得改前端源码或旧过滤器。若不能在本轮允许文件内安全提供，明确列为后续接线缺口，先完成独立已批启动工作，不把普通覆盖缺口变成新的接令审批。

## 离线验证与交付

只在自己的工作树使用离线依赖和现有Maven wrapper。允许fake/mock JDBC、内存Spring/MockMvc；不启动真实宿主HTTP服务、MySQL或真实模型，不发外部请求。

精确选择旧宿主三类 `AssistantReadHostTest,AssistantReadIsolationTest,AssistantReadHostReviewRegressionTest` 与上述两个新类；保留全部旧断言。新测试至少覆盖：缺配置关闭、错误实例/远程或root拒绝、证据hash/过期/范围不符拒绝、无扫描/旧服务/自动初始化、确定性只读接线、凭据脱敏、超时不放宽及失败连接释放；如果做同源静态接线，覆盖路径边界与/api不回退。用唯一Surefire后缀，每轮原始XML单独保留。离线可用性不宣称真实MySQL、权限或网站上线。

控制重跑：首次发现问题保留红灯并集中修改，在本批新增文件内最多两次修订复验；仍有阻断时完整留证停报，不反复无上限试错。不重跑未变化的W1/I2大套回归。分别给出定义数、展开场景数、去重数、实际执行次数、失败/跳过和重跑，不累加覆盖。

交付三项：
- 报告：实际新增/原因、未改依赖、已证事实/推断/待验证、测试结果和历史红灯、停止状态；
- overlay：每份新增文件hash/大小、旧30项保护结果、唯一轮次与索引，旧expected hash不改；
- STARTUP-HANDOFF：编译/明确启动入口与参数、证据和受限配置schema、非敏感示例、预算、同源地址方案及06真实复验最小步骤。明确真实启动、取真实证据/密码、汇集仍待06单独批次授权。

报告标明源码/离线测试执行者原08，总控仅规划审核；真实环境执行者无。要求GPT-6.1 Sol / High，桌面确认/观察与后端独立回读分列，不能冒称实际模型已核实。

## 停止条件

分支/HEAD、直接前像或目标空路径不符，来源不明修改、身份/安全异常，必须改变领域/公共/HTTP契约、权限、数据、索引、迁移或预算时停止受影响操作并报告。既有已知dirty状态与单纯未覆盖不是整批新审批；独立安全项目继续留证。禁止领域代修、汇集、真实数据库连接、读凭据、部署、提交、I3/W2或启动06。完成本阶段即停止，向总控汇报并待命。

