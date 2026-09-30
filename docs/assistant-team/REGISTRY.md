# 子任务登记（原 01～05 与新增 06）

## 2026-09-29 新增 06（当前登记，非旧 App Server 状态）

| 项目 | 当前事实 |
| --- | --- |
| 名称 | 智能助手 06｜集成测试 |
| 工作树 | D:/知识图谱-worktrees/assistant-06-integration-test，已创建 |
| 分支/基底 HEAD | codex/assistant-06-integration-test / 86d1e1fcff6b7b492faec3d499837a42246df320 |
| 创建执行者 | 总控，通过 Git 创建独立磁盘工作树；未切换/提交主项目 |
| 桌面会话 | 负责人已转回 06 实际只读入职回执；thread ID 未取得，未通过工具独立回读 |
| 只读入职 | 已完成并停止；总控资料/职责接手增量审核未发现阻断，正式执行条件尚未全部满足 |
| 目录记录 | 会话默认 D:/知识图谱；本次只读命令显式 workdir 为 06 工作树，不称桌面绑定已迁移 |
| 模型 | 要求 gpt-6-sol / high；06 单独桌面确认待完成；实际模型/强度未核实 |
| 下一串行任务 | 原 03 I2-R4 只读接令检查：前序 06 已停止，可由负责人手动转发；未自动派发 |

总控规划/审核，06 获批汇集/回归/真实复验，负责人验收。原 01～05 保留，不创建替代任务。入职入口：[06-ONBOARDING.md](D:/知识图谱/docs/assistant-team/onboarding/06/06-ONBOARDING.md)。最新状态见 [06 回执技术审核](D:/知识图谱/docs/assistant-team/onboarding/06/06-RECEIPT-REVIEW.md)；原准备清单/创建报告保留当时状态，不倒改历史。

以下为原 01～05 历史登记；旧当前状态、App Server 方式、模型回读和总控执行分工不覆盖上述现行记录，不授权调用旧控制脚本。

# 五子任务登记

## 当前协作规则（2026-09-28 起）

保留原 01～05 任务和工作树；当前固定为负责人手动转发、原任务串行执行、总控复核与受控集成。不得启动新的 App Server、创建替代任务或由总控进入领域工作树代做。

现行依据：[COORDINATION.md](D:/知识图谱/docs/assistant-team/COORDINATION.md) 与 [CONTROLLER-RULES.md](D:/知识图谱/docs/assistant-team/CONTROLLER-RULES.md)。领域任务要求 `gpt-6-sol / high`，无法可靠核实时必须写“实际模型未核实”。

**以下直到文末均为历史登记。** 其中创建/续接方式、控制器命令、manifest 状态和过去模型回读不代表当前执行方式、授权、实时状态或实际模型；不得按旧操作说明继续执行。原文字保留用于追溯，I2-R1 实际执行者的更正在总控规则文档中单独记录。

## 历史登记（保留原记录）

创建时间：2026-09-22。总控任务：`01a0c4d3-fb82-7661-a6a9-f056b2c52c32`。

五个任务均已由本机 Codex App Server 实际创建，并核验返回配置为 `gpt-5.6-sol`、`high`。先全部创建，再由总控逐一执行；每个任务完成指定阶段后结束本轮并待命。

## 2026-09-23 模型更新

用户要求改为 `gpt-6-sol / high`。总控默认配置与六份协作约定已同步；历史执行记录与报告保留原样。实际会话经独立进程重新读取：

| 子任务 | 实际模型 / 强度 | 配置结果 |
|---|---|---|
| 01 资料建设 | `gpt-5.6-sol / high` | 待更新：桌面进程持有 active writer，未强行释放或删除锁 |
| 02 质量治理 | `gpt-6-sol / high` | 已更新并回读确认 |
| 03 图谱研究 | `gpt-6-sol / high` | 已更新并回读确认；原额度中断已在续接轮次完成 |
| 04 学习教练 | `gpt-6-sol / high` | 已更新并回读确认 |
| 05 测评分析 | `gpt-6-sol / high` | 已更新并回读确认 |

模型配置操作本身未调用 `turn/start`。随后收到用户“继续任务”指令，总控已按 03 → 04 → 05 串行收齐余下报告，五个阶段均已汇报并待命。01 可在桌面对话模型菜单中改为目标模型，或在占用释放后由总控执行 `node .local/assistant-team/control.mjs set-model` 补齐；禁止把控制脚本默认值等同于所有会话均已更新。接口依据来自 [官方 App Server 文档](https://learn.chatgpt.com/docs/app-server) 和本机生成的 `ThreadSettingsUpdateParams` / `ThreadReadResponse` schema。

## 原始任务登记

| 顺序 | 名称 | 任务 ID | 工作树/分支 |
|---|---|---|---|
| 1 | 资料建设 | `01a0c6f9-fb32-70f3-9440-b8c0d7df4ca3` | `D:/知识图谱-worktrees/assistant-01-ingestion` / `codex/assistant-01-ingestion` |
| 2 | 质量治理 | `01a0c6f9-fc89-7ed1-b9c0-29d35cbac0b5` | `D:/知识图谱-worktrees/assistant-02-quality` / `codex/assistant-02-quality` |
| 3 | 图谱研究 | `01a0c6f9-fdb5-7d72-afa7-3eeda840413a` | `D:/知识图谱-worktrees/assistant-03-research` / `codex/assistant-03-research` |
| 4 | 学习教练 | `01a0c6f9-feeb-7623-923b-eb6d78f6c6bd` | `D:/知识图谱-worktrees/assistant-04-learning` / `codex/assistant-04-learning` |
| 5 | 测评分析 | `01a0c6fa-0046-7f33-8185-e35365ab5187` | `D:/知识图谱-worktrees/assistant-05-assessment` / `codex/assistant-05-assessment` |

共同基线提交：`bcd3b60ea4e6e18daad53f31787028a0f69bc6cf`。

基线分支：`codex/assistant-team-baseline-20260922`。它是当前代码和规划的隔离快照；主分支仍为 `main`，HEAD `86d1e1fcff6b7b492faec3d499837a42246df320`，创建过程未改变主工作树的暂存区。

## 阶段汇报与控制

首阶段 W0 的交付是各领域的接口/工具/结果契约、文件所有权、依赖和 W1 最小实现计划。报告放在各自工作树的 `docs/assistant-team/reports/<编号>-W0.md`；真实模型回复与阶段结束事件由控制器收取，总控审阅后决定下一步。

本机实时阶段与错误状态以 `D:/知识图谱/.local/assistant-team/manifest.json` 为准，本文不复制容易过时的运行状态。

控制器：`D:/知识图谱/.local/assistant-team/control.mjs`。可用命令：

```powershell
node .local/assistant-team/control.mjs status
node .local/assistant-team/control.mjs run-w0 01-ingestion
node .local/assistant-team/control.mjs resume-w0 01-ingestion
node .local/assistant-team/verify-reports.mjs
```

`run-w0` 只下发指定的一个任务，不会启动其他任务或进入 W1。完成 W0 后继续开发须由总控通过对应任务的续接接口下发新阶段，不自行从头创建替代任务。若遇模型不可用或额度不足，保持指定模型配置、记录阻塞并停止推进。

`resume-w0` 仅用于已记录为受阻的原任务，保留既有报告与历史，不重新创建任务。收到用户继续指令后，本轮按 [官方 App Server 续接协议](https://learn.chatgpt.com/docs/app-server) 恢复了 01 的中断阶段；不是换模型规避限制。控制器检查已有运行任务及前序任务是否完成，防止误启动并行轮次。

总控审阅与跨域决策见 [W0-REVIEW.md](W0-REVIEW.md)。本轮 W0 指五份契约设计报告，不等于主规划中的运行原型和产品功能已交付。

## 2026-09-23 公共核心实现阶段

总控已实现并测试公共协议、有限决策循环和依赖调度，阶段交付见 [W1-CORE.md](W1-CORE.md)。这里只交付隔离运行核心，真实领域工具、生产模型/API 和助手前端尚未接入。

本轮仅续接原有 03 任务进行 `W1-CORE-REVIEW` 只读审查，模型/强度回执为 `gpt-6-sol / high`；正常结束后自动汇报并待命。01/02/04/05 未启动新轮次。03 新增自己的审查报告，总控修复其中三项问题并执行回归。

控制脚本新增 `review-core` 单阶段命令；调用有状态与串行门禁，不会自动进入领域开发。阶段历史在 manifest 的 previousStages 保留。最后回读 02—05 为新模型，01 仍为旧模型待补改，未在旧模型上执行本轮工作。

## 2026-09-23 资料建设阶段准备

用户在了解 W1 核心交付与验收边界后，明确允许按既定规划继续；此记录不替代用户的最终验收结论。

总控准备了 [01 资料建设 W1 任务单](assignments/01-ingestion-W1.md)，并向 01 工作树同步 9 个公共源码文件、2 个核心测试文件及 W1-CORE 说明，逐文件规范化换行后核验一致。任务单也已同步；没有向其他四个工作树复制代码或启动任务。01 工作树内隔离复跑公共核心 43 项测试，0 失败、0 错误、0 跳过，完成于本机 16:43:38。

本轮依照 [OpenAI Docs 的官方 App Server 续接流程](https://learn.chatgpt.com/docs/app-server)重新尝试配置 01，仍返回 `already has an active writer`。尚未调用本阶段 turn/start，未下发资料建设开发，未使用旧模型或跳过顺序。manifest.nextStage 记录为 prepared_blocked_writer；需要桌面占用释放后回读确认 gpt-6-sol / high，再启动唯一的 01 阶段。
