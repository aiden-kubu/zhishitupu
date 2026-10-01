# 07 正式续作任务：图谱往返与原成果恢复

本文件准备不等于已派发；只有总控正式消息确认 06 七文件同步及其审核记录后执行。网站原批准范围内续作，不新增接令审批。06/08/09 待命，07 唯一执行。

06 同步现已完成且总控审核通过：主项目本目录 `06-WEBSITE-GRAPH-SYNC-REVIEW.md`。实际同步清单为原06树 `docs/assistant-team/website/06-graph-sync-manifest.json`，raw SHA-256 `17373f136702c8b358f5b0686241ca2a63ccc2b641e04517372fc8041e2036a4`；图谱依赖的准确后像复用 `09-GRAPH-SYNC-MANIFEST.json`。正式消息发出后必要门禁一致即可实施。

## 执行者、范围及前像

原 07「网站前端」，GPT-6.1 Sol / High；工作树 `D:/知识图谱-worktrees/assistant-07-web-ui`，分支 `codex/assistant-07-web-ui`，HEAD `8544b19f89bbcf477b9b458be7f48117775ff621`。每条命令 workdir 明确自己树。主项目及其他树只读。

复用已读未变协作规则、08 契约、07 客户端及 09 审核结论；只读新增同步/任务增量。必要门禁一致直接实施。

只允许修改这些已有文件；如 presentation helper 不需变，不为风格改动。Graph 七文件及 API/types/校验器为只读依赖，不能越界修改。

| 相对路径 | 当前 raw SHA-256 前像 |
| --- | --- |
| frontend/src/views/Assistant/AssistantView.vue | bef676de57861c8ec1c62bd6453b9657329a1dbbf2b3fdfecf8b0c0123145201 |
| frontend/src/features/assistant/useAssistantWorkbench.ts | 5e4f43aa4a93fe909b3c7cc5f17706d5d3ed1d95b35c4f81eaa6ae586e2e2f6a |
| frontend/src/features/assistant/ArtifactPanel.vue | ab3685e350c338e7500ce3178d17ec890a2b247f263d83046a56d8e9f7fa6ac8 |
| frontend/src/features/assistant/presentation.ts | 97082ab1925ac92833da9f90e0673035a7ae599c3ae2d2bcbedb80332fad9d08 |

允许新增本树 `frontend/src/features/assistant/verification/roundtrip*` 检查文件及 `docs/assistant-team/reports/07-web-ui-roundtrip.md`；不得覆盖已存在未知新路径，源和报告使用 apply_patch。允许检查生成输出放自己 frontend/dist 下新的 roundtrip 唯一运行目录，不删除旧截图、缓存或构建。不得修改旧 stage1 报告或失败证据。

## 交付

1. 已具备 09 安全视图后，为可显示 Graph 的原研究成果启用“查看研究图谱”，使用现有 `/`、runId/artifactId。其它成果不伪造图谱，不退回旧全库。页面明确只读。
2. 消费现有 `/assistant?runId=…&artifactId=…` 返回定位，重新 GET 原运行及其对应成果。URL 不是权限或新任务指令，不通过返回参数创建新运行、不写本地持久化、不恢复虚构组件内存。
3. 检查定位为单个合法 ID、Run/Host/Artifact/ProducerTask 身份与关联。任一参数存在但不完整、数组或非法，或者原成果不存在/过期/拒绝/不属于运行时，显示实际失败与可用手动返回入口，不静默打开另一成果，不自动发起或重试创建。
4. 活动运行恢复保持既有轮询/取消和代际保护；终态不轮询。切换上下文或卸载不自动取消宿主运行、不把迟到旧响应覆盖新成果。无定位参数的正常助手入口保持现有空草稿流程。
5. 验证实际往返、活动/终态与失败定位、迟到响应及研究模式请求隔离。只采用隔离响应夹具、自己的 loopback 静态前端服务和独立 headless profile；夹具不进入生产源码。不宣称真实宿主/数据库已通过。

## 检查和停点

应用定向类型检查显式 tsBuildInfoFile 自己树；Vite 现有 runner 构建到自己新的输出目录，不运行会写共享缓存的默认 npm build/type-check。无安装、无全仓格式化；记录两份已观察共享缓存前后 hash，不宣称整个 node_modules 零写入。

不重跑未变 07 七项、27 个客户端检查、09 整套或领域/I2；只运行实际受影响的精简往返/状态检查。记录新定义、执行、失败/重跑和文件后像。发现前像/依赖漂移、未知修改、安全问题或需改领域/08 契约时停止受影响操作并报告；普通覆盖缺口留证后完成独立已批项目。

不连真实 API/DB/模型，不启动现有后端，不读凭据、不汇集主项目、不提交/部署或启动其他任务。完成阶段报告、最终回报并停止，交总控审核。是否最终网站通过仍由负责人确认。
