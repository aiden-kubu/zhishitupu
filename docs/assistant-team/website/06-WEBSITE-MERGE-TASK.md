# 给原 06：网站成果受控汇集与离线组合验证，正式执行

2026-09-30。授权来源：负责人既有网站开发/必要只读接线授权，以及本轮总控汇报后“继续吧”。仅本批清单汇集和离线组合验证，不是原 I2 剩余项、真实环境、模型、I3/W2 或部署授权。07/08/09 阶段已完成并待命，06 唯一执行，不创建替代任务。

## 1. 执行者、基线和门禁

原桌面「智能助手 06｜集成测试｜I2 只读入职」；每条命令显式 workdir `D:/知识图谱-worktrees/assistant-06-integration-test`，主项目编译/检查时明确相应主项目子目录。06 分支 `codex/assistant-06-integration-test`，HEAD `86d1e1fcff6b7b492faec3d499837a42246df320`；主项目分支 `codex/snapshot-20260930-current`，HEAD `8544b19f89bbcf477b9b458be7f48117775ff621`。

GPT-6.1 Sol / High；负责人2026-09-30已确认原06设置，派发时总控核对可见模型按钮，后端实际参数未独立回读。复用未变长期规则/入职/历史证据，只读本任务、`06-WEBSITE-MERGE-MANIFEST.json` 及新 `08-REVIEW-FIXES-REVIEW.md`。07往返、09视图和08首次实施审核复用，不全文重审。

门禁核对源后像、七个目标旧前像、32个新增路径缺席、只读依赖和允许新集成测试/自己证据路径未占用；一致就直接执行，不回报接令后再等第二次授权。主项目前像已按先前审核记录核对；router只存在CRLF/LF差异，规范化内容一致且本批不修改，保留主项目raw。

## 2. 精确汇集与允许落点

权威文件清单为同目录manifest：**39个文件，7个替换、32个新增**。16个前端生产文件来自07组合工作树（9个作者07、7个作者09）；17个宿主源码、5个宿主测试/夹具和1个冻结机器schema来自08。图谱09成果已由06同步到07并经总控审核，不用09旧助手文件覆盖07最新往返改动。

全部门禁通过后保全七个主项目旧文件原字节及必要旧XML截面，逐项hash。仅通过apply_patch搬运39项正文，不手改后像，不复制整个树、node_modules、旧验证脚本、dist、报告或缓存。ApprovedSchema读取的主项目 `docs/assistant-team/website/08-stage1/http-contract.json` 本批明确新增，不能改测试读取逻辑/机器schema来掩盖缺依赖。逐项raw/norm记录；源与目标raw如因行末不同须明确说明、norm必须一致。

另允许新增一个由06拥有的集成测试：主项目 `backend-java/src/test/java/com/knowledgegraph/assistant/host/AssistantWebsiteIntegrationTest.java`。仅复用既有HostTestFixture/真实provider及内存Runtime、MockMvc，检验/导出测试专用HTTP样本，不改08夹具/宿主或01～05业务。不因package私有访问把业务复制到测试或修改可见性。该路径被占用、确需改生产实现即停报。

06自身仅允许新增 `docs/assistant-team/website/06-main-integration/**`（运行器、前像、受控JSON fixture、日志、截图、XML副本、overlay与索引）及 `docs/assistant-team/reports/06-website-main-integration.md`。主项目只增加/替换上面精确文件及正常编译产物：`backend-java/target` 的本批新suffix日志/XML/类产物、`frontend/dist/06-website-<唯一runId>/**` 的独立缓存/构建。不清理旧产物、不覆盖历史XML/任何冻结expected hash；测试报表必须用独立suffix。

## 3. 验证范围：集中本批，不重复I2整套

1. 汇集前后核对39项、74个后端只读依赖和8个前端只读依赖，以及已知两份共享tsbuildinfo。记录基线继承及未修改范围，不全仓扫描或宣称全仓/全部node_modules零变化。
2. 主项目 backend-java 使用已有 Maven **离线 `-o`**，只选择 `AssistantReadHostTest,AssistantReadIsolationTest,AssistantReadHostReviewRegressionTest,AssistantWebsiteIntegrationTest`，独立suffix。原宿主三类预期77展开场景；新增集成测试数量另列。模块源码/测试编译不等于运行全部领域测试。不重跑未变I2的203项、压力/故障数据库验证或升级依赖。
3. 前端应用定向类型检查：现有 `vue-tsc`，`--project tsconfig.app.json --noEmit --incremental --tsBuildInfoFile <本批独立目录>`。构建：现有Vite CLI `build --configLoader runner --outDir <本批独立site目录>`。不得用默认npm build/type-check、build-mode或失效cache隔离脚本，不写共享`.tmp`/`.vite-temp`；不改POM、Vite、tsconfig、依赖/锁文件为过关。
4. 新增集成测试生成的样本仅来自**本次主项目实际MockMvc输出**，包含能力、模板、库/资料、真实内存Run/Artifact/Graph/原文及错误包络，按需要记录HTTP请求/response关联。测试fake只在夹具；不得放入生产代码、生成演示成果给用户或把本批称为真实数据库/API联调。
5. 在本批构建上用06自己的离线浏览器驱动集中验证下面8项；可复用既有浏览器机制和07/09检查思路，但只新增自己的受控脚本，不改原检查预期，不再复制运行全部旧27/20/43项。静态服务只绑定loopback临时端口，全部`/api`由本批离线样本/明确错误处理，无代理/转发；阻断外部资源。Chrome使用独立profile，正常结束收尾，不复用用户浏览器。

### 八项关键网站组合场景

- API未接入/关闭：明确不可用、禁止创建，不回退旧聊天/全库。
- 选择已授权库和资料、发起唯一固定模板：显示相应Run、四阶段及服务器真实成果；Long保持字符串。
- partial及覆盖缺口可见，引用CURRENT不呈现为语义正确/人工核验或正式掌握度。
- 阅读来源成果并重读：绑定原run/artifact/source/document/chunk/hash；陈旧失败不残留/替换正文。
- 进入原研究成果的图谱：真实source→target、范围/缺口保留，不偷偷载入旧全库。
- 新图谱模式无旧写入口，不触发学习自动保存、阶段推进或图谱扩展请求。
- 图谱返回原成果：runId/artifactId正确恢复、不创建新Run、不定位首成果或过期兜底。
- 375px深色、键盘/关闭证据抽屉：无横向溢出且可返回正常操作；保存一张桌面、一张窄屏结果截图。

8项只计本批实际执行，缺口明确列出；需要更多数据场景、真实环境、改领域逻辑才可覆盖的项目不补样、不扩大批准。普通安全覆盖缺口不阻断其余独立项。不以修改测试标准或隐藏失败转绿。

## 4. 安全、失败及停止条件

不启动主应用/Standalone宿主监听、不连接任何数据库或真实API/模型，不读取凭据/生产配置，不修改07/08/09生产后像、01～05/公共核心/索引/迁移/权限/数据/预算。不安装、清理、提交、部署或自动进入其它批次。离线浏览器loopback静态夹具是本节唯一允许的监听，不是网站生产服务。

未知修改、前像/后像不符、身份/权限异常、共享缓存越界等安全问题立即停止受影响操作并保全；不覆盖/回滚用户文件，不现场修复。普通编译/业务失败留证并继续安全独立检查，依赖项标阻断；整批交总控判断退回07/08/09哪个任务，不自己开修复任务。

成功标准：39项受控后像一致，冻结依赖未改，77项宿主回归及新增测试按实际报告；前端类型/构建通过；8关键场景逐项记录通过或缺口，失败/红灯保留；无未获批外部连接、共享缓存越界或残留本批进程。不能覆盖的项目不自动标通过。

## 5. 报告和停点

报告列实际汇集/新增集成测试作者为06，生产作者07/08/09，总控审核；实际命令/workdir、unique runId、hash、各组定义数/展开场景/重跑、失败与警告、截图及进程收尾、真实环境未执行、普通缺口和后续真实接线前置。旧作者/红灯/expected hash不倒改。

当前任务完成写最终回执后停止等待总控审核；不自行启动08/07/09，不开启真实数据库/宿主/API联调或后续阶段。总控审核后由负责人最终验收。
