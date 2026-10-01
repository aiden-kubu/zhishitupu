# 07 同阶段正式续作：图谱客户端依赖

负责人已批准网站改造及固定团队执行。本指令为原 07 第一阶段同范围正式续作，不设第二次接令审批；必要门禁一致即实施。

## 执行者、范围与基线

原可视会话「智能助手 07｜网站前端」；自己的工作树 `D:/知识图谱-worktrees/assistant-07-web-ui`，分支 `codex/assistant-07-web-ui`，HEAD `8544b19f89bbcf477b9b458be7f48117775ff621`。每条命令明确 workdir。模型要求 GPT-6.1 Sol / High；总控本次已观察到桌面按钮 GPT-6.1 Sol 高，服务端实际参数未独立核实。

复用未变化的长期规则、08 契约及原 07 任务单。只阅读本指令和 `07-STAGE1-REVIEW.md` 的本次增量，不重读未变化全文。

仅允许修改以下两个已有源码文件及自己的 `docs/assistant-team/reports/07-web-ui-stage1.md`（追加，不覆盖历史）；必要的定向检查辅助文件只能放既有 `frontend/src/features/assistant/verification/**`。所有文件编辑使用 apply_patch。

| 文件 | raw SHA-256 前像 |
| --- | --- |
| frontend/src/services/assistantApi.ts | 35ad6415fe6bc84cafc22f2a252f2a2e61a10fa146d73b3b44ade88c44990306 |
| frontend/src/features/assistant/responseValidation.ts | 37a8bf1098f556c3e7b22d014e4f1d77171094c5fa088641fc02c6b7500f1c65 |
| docs/assistant-team/reports/07-web-ui-stage1.md | 3aaee81c9fe659d2c9a46c3562f8aabf2cebdcb910b1f5c28ffa7b179f5eed2b |

既有修改均保留；前像或 HEAD 不符且不能解释为本会话已知变化时停报，不覆盖。主项目只读。

## 具体交付与验收标准

1. 在 assistantApi 中增加 graph(runId, artifactId, signal?)，返回既有 Graph 类型；只调用获审 `GET /api/assistant/runs/{runId}/artifacts/{artifactId}/graph`，继续使用现有 segment 编码、api 包络、request 校验及 AbortSignal，不另建客户端或降级旧图谱接口。
2. 将已有 graph 响应校验纳入公开检查键；保留当前 Long 字符串、方向、范围、覆盖和数组上限检查，不放宽契约、不增加业务判断。
3. 定向类型检查，并检查新增请求的 URL/GET/信号与合法、非法 Graph 包络处理。只用现有本地依赖；验证夹具只在检查脚本，不进入页面结果。报告检查定义数及实际执行数；不重跑未变化的旧浏览器 7 项，也不追加领域/I2测试。
4. 沿用已成功的应用项目显式 tsBuildInfoFile 本树路径检查方式；不要再运行会写共享 `.tmp` 的默认 build/type-check，不复用失败的 monkeypatch。新缓存只写自己工作树；记录两份既知共享缓存前后 hash。不得删除/恢复任何旧缓存或安装依赖。
5. 报告追加实际变更及新 hash、命令、结果与未验证项。此批不启用图谱按钮，不改 router、Graph 页面、业务规则或任何 backend。完成后停止，由总控审核后交 06 同步，再给 09；07 不自行派发。

## 禁止及停点

不连接真实 API/数据库/模型、不启动已有后端、不读凭据、不部署、不提交、不汇集。未知修改、缓存再次越界、依赖需安装或实现需改变 08/领域契约时停报。普通覆盖缺口记录，不重新索取同批执行授权。
