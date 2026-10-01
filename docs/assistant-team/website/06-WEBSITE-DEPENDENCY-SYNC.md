# 06 网站依赖受控同步任务

本任务属于已批准网站实施的依赖交接，不是 I2 续验。仅在总控确认 07 续作交付已审核、正式消息附上最终源文件 manifest 后执行；现在准备任务不代表已派发。07、08、09 执行期停止待命，06 唯一执行。

## 执行者与目录

原 06 桌面会话「智能助手 06｜集成测试｜I2 只读入职」，GPT-6.1 Sol / High；用户 2026-09-30 桌面确认沿用，发送前总控再次观察设置，独立服务端参数未核实。

每条命令显式 workdir `D:/知识图谱-worktrees/assistant-06-integration-test`，预期分支 `codex/assistant-06-integration-test`，HEAD `86d1e1fcff6b7b492faec3d499837a42246df320`。保留全部已有成果，不复读不重跑旧 I2 证据。

若 06 尚未完整读取 frontend/AGENTS.md，本次先完整读一次；之后复用未变结论。旧协作规则的手工派发惯例已由负责人明确要求的可视桌面团队自动协调及 WEBSITE-TEAM 增量覆盖；仍不使用 App Server 或临时 Agent。这里只完成前端依赖搬运，不接手生产源码所有权。

源树只读：`D:/知识图谱-worktrees/assistant-07-web-ui`。目标树：`D:/知识图谱-worktrees/assistant-09-graph-ui`，分支 `codex/assistant-09-graph-ui`，HEAD `8544b19f89bbcf477b9b458be7f48117775ff621`。总控准备时目标 Git 状态为空；下列三个目标文件均缺席。

## 唯一接收文件

从 07 到 09，只新增相同相对路径的三份文件：

1. `frontend/src/services/assistantApi.ts`
2. `frontend/src/services/assistantTypes.ts`
3. `frontend/src/features/assistant/responseValidation.ts`

前像为缺席；批准后像为正式消息指定的源文件 manifest 中 raw/规范化 SHA-256。任何源 hash 改变、目标已有未知内容、分支/HEAD 改变或依赖冲突均停报，不覆盖。不得整体复制树、提交、切分支、接收 AssistantView/Sidebar/验证脚本或其他源码。

在本 06 树新增 `docs/assistant-team/website/06-dependency-sync-manifest.json` 和 `docs/assistant-team/reports/06-website-dependency-sync.md`。两个新路径先确认未占用，不覆盖。源码及文档编辑均用 apply_patch；目标内容只搬运不手改，规范化 hash 必须与审核源一致。若 apply_patch 导致 CRLF/LF 字节差异，分别记录 raw hash 与行末差异，不虚报 raw 一致。规范化方式为 UTF-8 解码、CRLF→LF、保留结尾换行、不 trim。

## 核对、报告与停点

必要门禁无异常直接同步，不返回无阻塞接令审批。核对源/目标清单、三个相对导入依赖是否存在；目标既有 http.ts 是只读依赖，不修改。无需运行编译/浏览器/领域或 I2 回归，09 后续在完整自己树检查。报告同步前像/后像、三份源/目标 raw 与规范化 hash、实际汇集者 06、源码作者 07、总控审核和真实环境未执行。只能据目标定向状态说明三文件接收，不扩大到全仓未变结论。

不访问主项目源码、不读凭据、不启动数据库/模型/后端、不改变依赖缓存或 node_modules、不写其他工作树文件。06 不解决源码冲突、不替 09 开发、不改业务规则。完成最终回报并停止；总控核对报告后才向 09 发正式指令。
