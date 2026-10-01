# 06 正式任务：研究视图同步到 07

本任务仅在总控正式消息派发后执行。网站批准范围内的必要依赖交接，不是 I2 续验；原 09 已完成并停止，07/08/09 待命，06 唯一执行。

使用原 06 桌面任务，模型 GPT-6.1 Sol / High。每条命令 workdir 为 `D:/知识图谱-worktrees/assistant-06-integration-test`，分支及 HEAD 沿用上批 `codex/assistant-06-integration-test` / `86d1e1fcff6b7b492faec3d499837a42246df320`。不复读未变规则或旧 I2，不重新编译/运行源检查。

源 09、目标 07 和七个准确文件的前像/后像见主项目本目录 `09-GRAPH-SYNC-MANIFEST.json`；审核记录 `09-STAGE1-REVIEW.md`。五个目标已有文件前像已由总控定向核对，两个新路径缺席。共同三个客户端依赖是已知成果，不复制/覆盖，核对其 hash 以保证导入一致。

门禁一致直接通过 apply_patch 精确同步这七个文件，目标内容不手改。只搬运批准源码，不复制验证脚本/夹具、dist、缓存、图片、文档或整棵树。保留 07 已有工作台/Sidebar/API 等修改。任一前像、源后像、分支/HEAD、新路径缺席或依赖不符立即停报，不自行解决冲突。

本 06 树仅新增 `docs/assistant-team/website/06-graph-sync-manifest.json` 与 `docs/assistant-team/reports/06-website-graph-sync.md`，先确认缺席。记录七个前像、源/目标 raw 与规范化 hash、共同依赖、作者09/汇集者06、实际核对者及模型实况。规范化保持 UTF-8、CRLF→LF、结尾换行、不 trim；raw 变化只可为已说明行末差异，不能隐匿内容差异。

不访问主项目源码、不动 07 其他文件、08/其他领域、不连接任何后端/数据库/模型、不运行浏览器/回归、不改 node_modules 或包、不删除文件、不提交/切分支/部署。同步完成停止回报，由总控审阅后向 07 派发往返联动；06 不自行开下一任务。
