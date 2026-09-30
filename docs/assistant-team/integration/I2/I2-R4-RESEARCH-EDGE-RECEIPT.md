给原 03「图谱研究」：I2-R4 增量接令检查（只读）。

发送前条件：06 已完成只读入职回报并停止。当前尚未满足时，不发送本指令，不同时启动 03/06。

请在原目录 D:/知识图谱-worktrees/assistant-03-research、原分支 codex/assistant-03-research 执行，不新建/切换工作树或会话，不运行 App Server，不修改主项目。
模型要求 gpt-6-sol / high，负责人在桌面确认；工具不能回读时写实际模型/推理强度未核实，用户确认单独记录。

完整阅读：
D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-RESEARCH-EDGE-QUERY-TASK.md
D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-RESEARCH-EDGE-EVIDENCE.json
按任务单增量阅读新增规则和直接失败证据；未变 W1 结论复用，不从头重复全量审计。

本轮只检查 cwd/分支/HEAD/Git 状态、两端目标前像、新路径占用、只读依赖/证据变化、查询链路和允许边界。请说明：原失败调用和缺少研究 EXPLAIN 的事实；outgoing/incident 共用链路的方向/双端范围/证据/去重/分页语义；计划离线测试；是否需 06 获批提供额外诊断；是否有缺失/未知修改/契约阻塞。

不修改文件、不创建报告、不编译或运行测试、不启动/连接/探测数据库、不接收凭据，不开始领域修复。本任务单已派发的仅是接令检查，不是 formal execution。
完成后明确回复：“I2-R4 接令检查完成，尚未开发，等待正式执行指令。”如有阻塞如实列出，然后停止。
