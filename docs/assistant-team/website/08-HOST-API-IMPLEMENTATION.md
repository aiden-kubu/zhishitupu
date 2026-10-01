# 08 正式阶段：只读宿主与 API 实现

2026-09-30；属于负责人已批准的网站及必要只读接线开发，不是 I3、真实环境启用或部署。本任务准备不等于已派发；总控完成 01 的 H03 说明审核并发出正式消息后，08 为唯一执行者，必要门禁一致直接实施，不另设接令审批。其余任务待命。

## 执行者与基线

既有桌面任务「智能助手 08｜只读宿主与 API」，工作树 `D:/知识图谱-worktrees/assistant-08-host-api`，分支 `codex/assistant-08-host-api`，HEAD `8544b19f89bbcf477b9b458be7f48117775ff621`。所有命令明确自己 workdir，主项目及其它树只读。GPT-6.1 Sol / High；派发记录区分桌面按钮观察与实际参数未独立核实。

复用未变规则和自己已获审的 08-stage1 契约/接线设计。只阅读新任务、`08-IMPLEMENTATION-BASELINE.json`、01 的新说明及总控审核，07 往返审核只读接口影响段落。基线含自己树与主项目规范化一致的 66 个公共/领域 Java 文件，以及 8 项已有 POM/配置/包络依赖；没有额外源码同步或依赖升级要求。原领域/公共契约继续只读，历史冻结 expected hash 不变。

三份设计文件 raw SHA-256：HTTP-CONTRACT.md `0f5efd0f40a0dee738fcd435de5e0ca9a971b7c259b7793f7666d528cce3c3bf`；http-contract.json `5b4fa0049a1a886734f9448cca6d163a6d7a00308f7ab66bdf10c84e3c635e32`；HOST-WIRING.md `fc270a9a9494275b738af357dc07ada755d512f6d62a0ef4de6bed5b692e8b61`。

H03 说明已完成并经总控审核：主项目本目录 `01-READ-WIRING-REVIEW.md`；原 01 报告 `D:/知识图谱-worktrees/assistant-01-ingestion/docs/assistant-team/reports/01-website-read-wiring.md`，raw hash `a0e66cbd9191aa8bd4f6a84e8395b52938ab8c98415bf61923f326f0e7f2c385`。依赖基线 JSON raw hash `607cf84d56f4f013c261eb05669910f6212f5478571434ba569b4941af4d7d82`。三项资料读取包含可信路径的文件存在性检查，保持现有语义，不声称纯数据库读取；真实挂载/文件权限仍待未来获批验证。

## 允许新增文件

两处 host 目录检查时均不存在，只允许在以下目录新增归属本任务的实现与测试；若已有未知文件，停报，不覆盖。

- `backend-java/src/main/java/com/knowledgegraph/assistant/host/**`：按已有设计的十四项宿主职责实现，可合并小类；不得将其它领域代码挪入此目录重写。
- `backend-java/src/test/java/com/knowledgegraph/assistant/host/**`：定向离线集成/契约/安全测试、仅测试用 fake 仓储/来源/访问上下文。
- `docs/assistant-team/reports/08-host-api-implementation.md` 和 `docs/assistant-team/website/08-implementation/**`：新证据/准确文件清单、检查结果及必要实施说明；不得改 stage1 历史设计与报告。

源码和文档使用 apply_patch。不改 POM、application 配置、入口主应用、公共包络/核心、01～05 源码/测试、前端、治理、旧冻结清单。目标/接口超出范围时留缺口，不现场解决；主项目不汇集。

## 实施内容与边界

1. 按已审 HTTP 契约实现十个端点：能力、唯一模板、库/资料范围、创建/读取/取消运行、读取成果、精确原文重读、研究子图。字段/错误/Long 字符串、严格输入和上下文关联与机器 schema 一致，不新增用户上传的权限/DAG/可信成果参数。
2. 固定模板 `source_graph_citation_v1`：每资料 metadata → source → directed_graph → citation_check，1～2 份资料分别保留结果与缺口。使用现有 Runtime、Runner、Registry、领域 provider 和真正直接依赖；不调用模型、旧业务服务或写工具。DecisionProvider 只决定工具与完成，不藏 JDBC 调用。模型调用为 0、内部 modelCalls 映射 decisionCalls；不改核心指标。
3. 生产原 Artifact 和 Context 原观察是权威；页面 DTO 只白名单转格式。质量 reader 只读取该 consumer 的真实直接输入；图谱只投影原观察及原方向，原文定位从真实 evidence 解析。不得在适配层重判正式来源/学习/评分/质量业务、重排结果、编造摘要或伪造 sourceId/hash。真实空结果、partial、error、blocked、未检项和覆盖限制原样传播。
4. 采用 01 说明及审核所确认的现有真实构造依赖边界。只注册三项资料工具与模板必要研究/引用工具；不用 null/mock/fake 作为正式接线、不绕过领域构造器。测试的 fake 仅隔离测试。SettingsService 等真实内部依赖不成为通用公开 bean/工具/API，旧设置/模型/写方法不可达。
5. 有界内存 RunHandle/原成果/可信观察管理及 owner/grant 校验，查询/取消/终态发布/到期线性化；新开/关闭/过期不写数据库。停止或切页面不能假称取消运行，已完成成果保留。保留 404/410 和取消真实状态；并发容量、TTL、tombstone 有界，内存超限不驱逐 active、不默删证据冒充完整。
6. 默认 host/database/model 均关闭；模型即便配置 true 也拒绝，不读取 LLM 密钥/设置。独立显式装配，不扫描旧业务应用、不注册旧写路由、不修改主应用默认数据源/迁移。仅有类或配置 flag 不得将 databaseIdentityVerified 设 true。缺可信访问/固定 grant/reader 身份及只读权限依据时 fail closed。使用独立明确命名 reader，禁止 writer/root/default JdbcClient；setReadOnly 不能替代权限证明。本轮不提供真实凭据、不实际验证数据库。
7. 安全入口必须与旧主应用扫描隔离；新增启动/配置类不得被旧 Spring 扫描顺带开启或改变旧路由。离线验证默认关闭不创建连接/读取秘密、不触发数据库初始化或迁移、旧写服务不注册。编写独立启动代码不等于本轮允许启动监听端口；生产身份/权限/代理/公网及多用户认证继续留后续批次。
8. 运行/成果/原文/Graph 属敏感易变数据，HTTP 返回明确 `Cache-Control: no-store`；契约 07 首轮夹具失败不能当作生产缓存验证。错误脱敏，禁止回显 SQL、凭据、storage_path、原始 private Context/未经白名单 dataJson。无来源指针不能补造原文；引用 CURRENT 不代表语义正确、评分或人工验证。

已有设计中运行 180 秒、decision32/tool24、任务分阶段限额，SQL3秒、独立重读10秒/并发2、active8/retained32/TTL1800秒/内存界限可作为本批代码与隔离测试的默认约束；不调大核心原有上限或 I2 的历史预算，不宣称已性能验证，不授权真实环境启用。若原设计有内在契约矛盾，先说明，不偷偷改机器 schema/公共接口。

## 离线验证与交付

只使用既有离线 Maven 依赖，显式自己 backend-java workdir；允许编译和受影响定向测试，使用独立 Surefire 后缀保留每轮 XML。不得安装依赖、启动真实宿主/现有后端、读取凭据或连接数据库/模型/真实 API。MockMvc/内存 Runtime/已有仓储 fake 可以使用，避免测试加载默认真实 DataSource。

覆盖重点：十端点字段/包络和拒绝非法输入；大 Long 及 Scope1000边界；服务器 grant/归属变化/跨 run producer/直接输入伪造；真实 Runtime 的单资料与 A/B 部分/失败传播、指标与取消保留；终态到期/容量/迟到竞态；Graph 方向/真空与不可用区分；原文 hash/归属不符无正文；默认关闭零连接与旧写路由不可达；no-store/错误脱敏。风险相关即可，不重复 I2 压测或未变领域整套。不能完成的覆盖如实列出，不为普通覆盖缺口再次停批索取授权。

报告列实际新增文件/后像、修改者与检查执行者、定义/展开执行/失败/重跑、机器结果路径/hash、真实环境未执行、未接入能力和下一批前置条件。API JSON 静态一致检查不冒充真实联调；无模型固定工具模板不宣传成模型协作。保存红灯，不弱化旧测试/契约。未知修改、前像变化、身份/安全异常或必须扩大权限/数据/索引/迁移/预算/领域契约时停止受影响操作并汇报。

完成后最终回报并停止，总控增量审核后另向原 06 明确集成批次；08 不自动派发、汇集、部署或进入 I3/W2。负责人最终确认网站验收。
