# 原 08 正式续作：宿主审核问题集中补修

2026-09-30；原网站/必要只读接线授权范围内的同阶段补修。总控已完成首次增量审核并明确范围，正式消息发出后必要门禁一致直接实施，不再设置无阻塞接令审批。08 唯一执行，其余任务待命，不授权真实环境。

## 执行范围与前像

既有「智能助手 08｜只读宿主与 API」，GPT-6.1 Sol / High，自己 worktree/分支/HEAD 仍为原实施任务所列。每条命令显式自己树或 backend-java workdir；其它树只读。复用未变规则/契约/基线，只读新 `08-IMPLEMENTATION-REVIEW.md`、本任务和 `08-REVIEW-FIXES-BASELINE.json`。

只允许修改增量 baseline 中十份现有文件（七份 host 源码与三份 host 测试/夹具）；不需要变的文件保持原样。允许新增自己树 `backend-java/src/test/java/com/knowledgegraph/assistant/host/AssistantReadHostReviewRegressionTest.java`，以及 `docs/assistant-team/reports/08-host-api-review-fixes.md`、`docs/assistant-team/website/08-review-fixes/**`。新路径如被未知文件占用停报。不改 ApprovedSchema、原 08 契约、74 项领域/公共/POM依赖及其它宿主源码。

修改前受限保全拟改文件前像到新 evidence/preimages 中并留 hash；这只是历史保全，不回滚源文件。保留原实施报告、FINAL-RECEIPT/GATE/EXECUTION/XML/log，旧 expected hash 不改。当前活文件将产生新后像，必须用新 overlay 明确关系；不运行旧历史 verifier 来要求当前源文件重新匹配旧像，不把它改绿。源码/报告编辑用 apply_patch。

## 一次性处理六项

1. 去掉 host 额外的 sourceFilePresent 选择/创建否决规则；保留领域原存在性检查及出站事实，不跳过 Files helper。通过现有领域正式来源工具确认原文，而非重新实现来源规则。补“原文件不存在但正式 chunk 仍可用”的正常完整链回归，同时保留归属变更/未完成/非 active/真正不可用来源的失败或缺口检查。原测试中“文件缺失必拒创建”的断言与契约相冲突，应精确替换并在报告记录原因，不删除其它归属/权限检查。
2. 按冻结契约映射真实 research.read_source 错误：缺失/越界→404 NOT_FOUND；原本不可用→409 SOURCE_UNAVAILABLE；读取中缺失/失效→503 SOURCE_CHANGED_OR_UNAVAILABLE；陈旧 hash→409 STALE_SOURCE。读中 hash 变化按现有领域 STALE_SOURCE 保留，不重判断业务。各项均无正文、无回退，拒绝请求和执行中撤权仍保留原权限状态。
3. 区分外部 HTTP 字节限制与内部既有字符串字符限制。以符合原领域/核心边界且包含中文的真实 ToolResult/Artifact 或模板输出复现单位问题，修宿主解析/投影调用；不调大核心字符上限、HTTP64KiB/4KiB、Scope1000、模板/图谱窗口或内存上限。不把合法服务器结果的转换失败伪称非法客户端请求；真的超界也继续拒绝。若确需改领域/公共契约，停报不改。
4. 对齐四项未接入能力的 unavailable/原因：学习和测评预览 NOT_IN_STAGE1_TEMPLATE；正式测评 FORMAL_SCORING_UNAVAILABLE；真实模型 REAL_MODEL_NOT_CONNECTED。读取开关关闭仍 disabled，真实环境/接线缺失仍 unavailable，formalAvailable=false，模型调用0；不因此启用任何能力。
5. 选择顺序在 host 自己的可信内部计划中保存，不从无序 Scope Set 推断；A/B 对调请求应对调任务链的编号/排列，Scope 核心保持不变。首版 Runtime.parallelism 回到已审1；独立 IO 槽位2及其它批准预算保持不变。离线验证固定计划顺序/两条链保留/失败传播，不修改核心。
6. 按原契约拒绝 GET 非空 body，并保持正常 GET、no-store、同源/loopback及错误脱敏；不把安全边界搬入领域或新增路由。

## 验证与停点

现有离线 Maven，明确自己 backend-java workdir；只定向运行原 Host/Isolation 两类与新增补修类，独立 Surefire suffix、新 evidence 目录，不覆盖旧 XML/红灯。不重跑未变 I2/领域整套，不启动监听宿主/数据库/模型/真实 API，不读取凭据、不安装、不提交/部署或主项目汇集。

先用定向检查说明缺陷（能复现的保留旧像红灯）；修后同组及两类受影响回归。报告分别列定义/展开/重跑及当前实际结果，不把重复轮次算新覆盖；每项说明实际改动、未变范围、仍未验证。普通覆盖缺口记录后继续其余独立已批项；前像/安全/未知修改或必须扩大范围才停止受影响项。

新 overlay/报告列十项前后像（未改项明确相同）、新测试及红绿/XML hash、原报告/receipt保留状态、代码作者/测试者为08、总控只审、模型按钮观察与实际参数未核实。完成最终回报后停止，等待总控一次增量复核，不派发06/07/09或自动推进。
