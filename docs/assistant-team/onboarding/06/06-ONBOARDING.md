# 06 只读入职任务单

阶段：06-ONBOARDING，2026-09-29。负责人批准创建 06 并只读入职；没有批准本轮测试、开发、数据库操作或样本装载。当前总控已创建磁盘工作树，桌面会话需负责人创建；本文件不是 06 已完成接手的报告。

## 1. 指定上下文

工作树 D:/知识图谱-worktrees/assistant-06-integration-test，分支 codex/assistant-06-integration-test，Git 基底 HEAD 86d1e1fcff6b7b492faec3d499837a42246df320。主项目 D:/知识图谱只读参考。

本树暂不是主项目未提交成果的可执行副本，不运行 Maven/npm/setup。主项目现行基线由 06-BASELINE-MANIFEST.json 的指针/hash 定义；不要把旧 Git HEAD 和新 R3 文件混成同一版本。

## 2. 首次必读（不要执行其中历史命令）

1. 主项目 AGENTS.md、docs/assistant-team/COORDINATION.md、CONTROLLER-RULES.md（首次完整阅读，当前新增规则优先于历史职责）。
2. docs/assistant-team/06-INTEGRATION-TEST-CHARTER.md。
3. 本文件、同目录 06-I2-ENVIRONMENT-HANDOFF.md、06-REPORT-TEMPLATE.md、06-BASELINE-MANIFEST.json、GOVERNANCE-OVERLAY.json。
4. docs/assistant-team/integration/I2/R3/R3-REPORT.md、evidence/POSTRUN-SUMMARY.json、FINAL-INDEX.json、XML-TRANSCRIPTION-CHECK.json。
5. 主项目 9 份 assistant/i2 集成测试及 R3 三个运行/保护脚本：首次阅读以了解执行路径、凭据边界和危险入口，不运行。
6. I1-BASELINE.json、I1-CASES.json、I2-FIXTURE-MAPPING.json、I2-VARIANTS.json、I2-SCALE-FIXTURE.json 的样本/标准信息；R1/R2/R3 overlay 与包清单作为沿革，旧历史报告按 R3 当前报告的引用查阅，不默认全部再审。

路径均以主项目 D:/知识图谱 为根；清单给出精确绝对路径。旧环境/样本脚本先看入职环境卡的分类，不按名称顺序执行。不得调用旧 .local/assistant-team 控制脚本。

## 3. 只读核对

- 实际 cwd、分支、HEAD、Git 状态，区分原 Git 内容与总控新增入口；不清理。
- 模型要求/用户确认来源/工具实况；旧 01～05 确认不能作 06 本轮实况。
- 清单明确的当前集成源码/运行器、领域基线和关键证据存在且 hash 匹配；规范化 UTF-8、CRLF→LF、保留结尾，不 trim。raw hash 用原字节。
- 当前 62 个 I1 文件和 R1～R3 显式增量的关系，不能改旧 expected hash。新治理文档与旧 R3 guard 的预期冲突是有记录的治理增量，不运行 guard 强行改绿。
- 现有脚本真正运行目录和写入位置；哪些会装载/改变样本，哪些会启动服务，哪些会调用 Maven。只说明，不执行。
- 环境身份来自已封存证据和环境卡；不启动、不连接、不探测监听、不解密凭据，不接触 mysql-data 内容。
- R3 已执行但未全绿：离线 198/198，真实 43/44，质量 Q01～Q10 和组合采样达到本轮标准；03 outgoingEdges 3024、后续同方法断言未到达。06 不接管研究修复。
- 原 XML 在 target、general log 累计追加、中文转录补充证据、下轮运行前证据保全需求；本轮不复制/清理原始敏感材料。

检查出现缺失/前像不一致/未知修改时报告其路径、预期、实况和影响，不自行同步或修复。

## 4. 回执与停止

只在聊天返回：实际目录/分支/HEAD、核对清单结果、理解的职责与危险入口、已知停点、缺口、模型实况。列代码修改者=无，测试运行者=无，真实环境验证者=无。不要创建报告文件。

结尾：“06 只读入职接手检查完成，尚未开发、汇集、运行测试或连接数据库，等待总控审核和下一步指令。”若存在阻塞，明确“检查未完成/存在阻塞”，不得套用无阻塞结论。然后停止，不启动原 03 或其他任务。
