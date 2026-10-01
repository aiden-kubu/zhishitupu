# 原 06：助手体验四文件受控汇集与轻量离线验证

日期：2026-10-01。状态：总控已准备，尚未派发/执行；负责人批准本批后，此文作为正式执行指令，不再设置普通无阻塞接令审批。

## 执行者与必要门禁

你是已有原 06「集成测试」，不是新建或替代任务。显式 workdir 使用 `D:/知识图谱-worktrees/assistant-06-integration-test`；预期分支 `codex/assistant-06-integration-test`，HEAD `86d1e1fcff6b7b492faec3d499837a42246df320`。要求 GPT-6.1 Sol / High；桌面配置与工具无法独立回读的实况分别记录。既有规则/入职未变结论直接复用，首次未读的文件仍完整读取。

完整读取本任务、新 `07-UI-POLISH-REVIEW.md` 与 `06-UI-POLISH-MERGE-MANIFEST.json`。原 07 报告只读新增交付及失败/覆盖说明，旧报告不重读全文。核对获审四项主项目前像、来源后像及原始报告/overlay/runner/fixture hash；核对本批目标只读依赖。正常门禁直接实施；未知修改、前像不符、身份/范围/权限异常时停止相关操作，保留现场，不覆盖、不倒改 expected hash。

07 已停止后才能实施；其它任务待命，严格串行。门禁不是新的授权轮次；普通测试覆盖缺口留证后继续独立已批项目。

## 唯一允许的修改范围

1. 主项目 `D:/知识图谱/` 中清单的四项 frontend 文件：仅以获审原 07 字节后像受控接收，不现场修源码。用 apply_patch；先将四份原始前像以 base64/raw hash 保全在自己的本批证据目录。汇入后 raw SHA-256 必须逐项等于获审后像。
2. 本 06 树新增 `docs/assistant-team/website/06-ui-polish-merge/**`，用于 runner、增量 overlay、前像保全、构建日志、检查结果、截图、收尾与索引；新报告 `docs/assistant-team/reports/06-website-ui-polish-merge.md`。
3. 主项目 frontend 新独立 `dist/06-ui-polish-<唯一runId>/**` 用于 tsBuildInfoFile、site 及独立 Chrome profile；不改旧 dist、共享缓存、依赖及源代码测试入口。

不汇入原 07 整树或其测试/报告。main router 与原 07 router 的已有 raw 差异按清单分别保留，不顺手统一。18 项只读前端/图谱依赖保持原 hash。

禁止 backend、领域实现、公共核心、宿主/API、schema、旧测试、package/lock、公共 Modal、全局主题/Sidebar 的任何修改；不新增网站业务/鉴权判断，不改前批索引/历史红灯。总控/06 不代做 01～05、07～09 实现，发现问题整批交总控。

## 验证：只覆盖本批风险

- 主项目 frontend：应用 vue-tsc 非 build 模式 `--project tsconfig.app.json --noEmit --incremental --tsBuildInfoFile <本批独立cache>` 一次；Vite `build --configLoader runner --outDir <本批独立site>` 一次。明确 workdir，不使用默认 npm build、旧缓存修复脚本、安装或删除依赖。先记录原两份共享 tsbuildinfo 和 `.vite-temp` 必要范围，结束再核对；不声称全 node_modules 零写入。
- 借鉴/复用获审 07 `verify.cjs` 的四组、11 个场景，在自己新 runner 中仅适配主项目 site、06 输出目录/独立 profile/证据路径；不弱化检查断言。07 runner 只读，勿直接运行其硬编码输出目录。原 06 17份 MockMvc fixture 只读复用，变体仍明确 synthetic；所有 API 请求只回放或返回明确错误，不代理真实后端。
- 中文 partial/metadata/CURRENT、正常选择；disabled/404/未知原因等诚实失败；375px 内外双层边界、长 locator/hash/原错误/全文及键盘滚动；精确重读→STALE_SOURCE 清正文和原成果→09→原成果。范围与真实指针不得改，未知码不得冒充成功。
- 窄屏测试保留布局稳定时点，同时检查 left/right 与每个可见内层 scrollWidth/clientWidth，不仅外层页面；不靠 overflow-x:hidden 或截断实现“通过”。不把原大整数受限显示称为 2D/3D GPU 已测。
- 不重跑未变后端78/77、W1/I2、旧07/09全套。11个场景只算11个覆盖，重跑与失败另列。既有已验证内容直接引用，不累加历史测试数字。

允许仅在 loopback 启动本批静态 fixture server 与独立 headless Chrome，结束关闭自己启动的进程/监听；不启动真实 backend/Vite长期服务、MySQL、模型、不读取凭据、不触碰既有服务。进程异常不能对未知用户进程做强制清理。

## 证据、停点与交付

保留每次命令、cwd、退出码、完整输出、失败及受影响条目的定向重跑；源码前后 hash、18项只读依赖、fixture、源报告/overlay/runner、现有必要缓存均记入新增量，不改历史清单。报告区分事实/解释/尚未验证，附最终源码核验、场景定义/执行/重跑数、截图、自己的进程收尾。

安全异常立即停止并安全收尾。普通业务失败完整记录，继续独立安全检查，依赖项标阻断；不现场改源码、接口或标准。待总控一次增量审核后，负责人最终验收。本批不连接真实数据库/模型、不开放真实宿主、不部署、不自动进入后续环境验收、I3或W2。

明确列作者：前端07、原图谱09、原样本06；本批汇集/类型构建/页面验证06；总控增量审核；真实环境执行者本批无。完成后停止，向总控回报报告与证据索引路径。
