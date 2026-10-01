# 07 网站前端第一阶段任务

执行者新增 07，模型 `gpt-6.1-sol / high`。工作树 `D:/知识图谱-worktrees/assistant-07-web-ui`，分支 `codex/assistant-07-web-ui`，基底 `8544b19f89bbcf477b9b458be7f48117775ff621`。该任务只有在总控交付审核后的 08 契约并发正式提示词后开始，不因目录存在自行开工。

## 目标和允许文件

改造 `/assistant` 为能操作的工作台：服务能力提示、选择库/资料范围、目标与任务模板、执行步骤、成果、证据抽屉和取消；新增流程的真实服务暂不可用时显示明确状态。助手入口移动到工作空间，沿用现有布局、组件、深色主题和移动端能力。聊天不增加永久保存。

只修改本工作树 `frontend/src/views/Assistant/AssistantView.vue`、`frontend/src/components/layout/AppSidebar.vue`；允许新增 `frontend/src/features/assistant/**`、`frontend/src/services/assistantApi.ts`、`frontend/src/services/assistantTypes.ts`。允许新增自身阶段报告。图谱页面/路由及图谱渲染由 09 负责，其他领域页面及 backend 全部只读。

首次完整读主项目现行协作规则、frontend/AGENTS.md、本目录 WEBSITE-TEAM.md 和获审 08 契约；后续只读变化。任务开始时记录实际前像及新路径缺席，不覆盖未知修改。

## 本次正式派发基线

08 交付已由总控审核，审核记录为主项目本目录 `08-STAGE1-REVIEW.md`。完整读取该记录和 `D:/知识图谱-worktrees/assistant-08-host-api/docs/assistant-team/website/08-stage1/HTTP-CONTRACT.md`；机器字段以同目录 `http-contract.json` 为准。这些路径只读，不要求汇集到主项目才开发前端。契约文件 raw hash 为审核记录所列三个值，未变材料不重复全量审查。

2026-09-30 总控门禁：07 分支和 HEAD 与上述基底一致，Git 状态为空。两个允许修改的既有文件 raw SHA-256：

- AssistantView.vue：`ce59898dd20c89ac7f8c859ae33c4c5f90900ad071cdc5314bacad6410e77f68`
- AppSidebar.vue：`369f8120ffa241b1ee3857a85caa243ebc96abd6f766b6e7f1a3dd8d138ae270`

正式执行前仅核对这些前像、新路径未占用、指定分支及依赖；一致就开发，不再返回接令检查等授权。每条命令显式使用自己的工作树 workdir。所有文件新增/修改使用 apply_patch；不要用 Python、shell 输出重定向或内联生成器写源码和报告。

主项目已有 frontend/node_modules，07 尚无；在 package.json 和现有锁文件与主项目相同且目标不存在时，允许为 07 的 frontend/node_modules 建立到既有主项目依赖目录的目录联接，作为本批构建环境复用。不得移动/删除/安装/更新共享依赖、修改包配置或主项目源码。记录联接目标；构建输出仅落自己的工作树。若现有依赖无法用于检查，报告检查未完成，不擅自安装新包。

保持首版小闭环，不新增学习/评分逻辑、持久化聊天、认证系统或压力测试。图谱跳转使用已有路由 `GraphWorkspace`（当前 path 为 `/`），携带 runId/artifactId 上下文，由 09 实际接入；不要使用不存在的 `/graph` 或新增路由。当前尚无联动时明确标待接入，不误导打开旧全库图。原文的 formalAvailable 只指来源状态，不代表正式测评可用。

## 页面边界

桌面采用范围与任务区、主要成果区、可展开证据区；窄屏改页签或抽屉。以中文用户目标与操作为主，不把内部 hash/工具参数铺满页面，必要元数据放详情。状态不只依靠颜色。

页面分别显示服务未接入、无资料、正在运行、部分完成、失败、已取消和未保存草稿。HTTP 错误不退化为空成功；刷新后内存运行找不到则提示重新发起。每个引用可精确重读，失败保留原因，不自动换全库检索或旧聊天接口。

按获审契约调用后台，不能写死样本或用 mock 冒充结果。未启用的后台能力不会伪造任务进度。不得重新判断质量、评分、路径推进或图谱关系；只呈现服务端事实。图谱跳转协议由契约提供，并预留 09 联动位置。

## 验证和停止

使用已有依赖，无新增包、无 lint --fix 全仓或 format 全仓。允许针对自己的工作树运行 `npm run type-check` 与 `npm run build`；若依赖目录不存在，报告实际状态并采用已有安装资源，不擅自装包。不要运行 dev:local、启动后端、读取生产配置、数据库或模型。

根据实际变更做必要页面状态和键盘/窄屏验证，记录未运行的真实 API 验收。报告代码作者、改动文件、构建结果和未接线能力。完成最终回报总控后停止，不提交/推送、不自行启动 09/06。
