# I2-R4 原 06：输出收集增量复核与真实取证续作指令

日期：2026-09-29。本文件由负责人手动转发原 06。结论为解除运行前日志技术停点，恢复此前已批准的本批四项 EXPLAIN 补证；不是 I2-R4 或 I2 验收通过，也不表示指令已自动发送或脚本已经运行。

## 1. 本次增量复核结论

总控仅比较上一版 runner 与本次输出收集差异，并审核新验证脚本、最终证据、报告追加段和 overlay 增量。已闭合 A/B、XML、保全数量等部分复用前次结论，不重复全文审核或全仓扫描。

原 C 组最后一项日志缺口已在本次差异中闭合：

- stdout/stderr 分别分块异步读取，保留已取得的内容；子进程结束或取消后有界收集，最多额外五秒。
- 读取异常、取消、收集到期或截断均保留部分内容与原因。日志持久化失败单独记录，不以占位文件冒充完整输出。
- 每流状态、截断与完整性进入 EXECUTION；不完整会抛出本批错误并进入既有安全收尾，不能报为完整成功，原始错误与收尾错误分开。
- 每流 16777216 字符保留上限只影响诊断证据收集；超限继续排空但标记截断并停止后续项目，不用于放宽 SQL 或测试标准。

总控阅读了原 06 的纯内存验证代码和结果，六组为即时完成、延迟完成、读取 fault、取消、收集到期、完整性门禁（含截断标志）。验证只提取四个输出收集函数，不求值 runner 顶层；最终证据记录六组通过。本轮总控没有重跑这些验证。

真实进程管道、Maven 编译、真实日志落盘、MySQL EXPLAIN、零业务写入和停库均仍待实际执行验证。内存验证不替代这些能力，不计算为六项数据库查询或统一回归通过。

## 2. 核对结果与当前版本

本轮总控重算了七个直接相关文件/前像的 raw hash，全部匹配：当前 runner、overlay、未变 Java、上一版 runner/overlay/报告副本和最终内存验证 JSON。overlay 的 rawReferences 与 preimages 声明相对上一版完全未变；未再全量重算历史文件。runner 及新验证脚本的 AST 静态解析错误均为 0，未执行其代码。

| 当前文件 | raw SHA-256 |
| --- | --- |
| Invoke-R4EdgeExplain.ps1 | 497428ae0cb9c58a563c4eb4197cf219f1bda7f1fc8650aa56430bc86950743c |
| R4-EXPLAIN-OVERLAY.json | f50ee538a118fdb5e70de257aefddfe154b0c2e84f63e8de86b0bc7d4cfebeaa |
| I2ResearchEdgeExplainTest.java（未改） | 5c9aebaea80b6459e273a6868dcf32d42359f1a3678001f3697d5bc8dc7429fd |
| OUTPUT-CAPTURE-VALIDATION-FINAL.json | d488478d1655bc825df3e4bd660da233fb94bd9c268075281dd84b320548efe1 |
| Validate-OutputCapture.ps1 | 0bf48f61e644586663afef248e5fc4aed4327acaa34f60e1b014ff5e6b110a89 |
| 06-integration-I2-R4-explain.md（当前静态修订回报） | 78948bea758d96454eec1dfebc69e97a642f3b856b80d3679a0fc749609e3f9a |

runner/overlay 位于 `D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-EXPLAIN/`；内存验证脚本及 JSON 位于其 `evidence/revision-review/`。

Java 位于 `D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2ResearchEdgeExplainTest.java`。报告位于 `D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/reports/06-integration-I2-R4-explain.md`。此处固定的是运行前版本，后续报告应追加实际运行记录并保留此前状态，不倒改历史 hash 或重写旧错误。

## 3. 正式续作：只恢复本批已获批准的取证

原 06：负责人此前已明确批准你的诊断实现、写入落点和四项真实 EXPLAIN，且已单独确认模型。本次运行前技术复核未发现仍需阻断本批恢复的问题。现在解除两份运行前审核中的“先补修并停止、暂不运行真实 runner”停点，恢复 [原正式执行指令](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-06-EXPLAIN-EXECUTION.md)下的同一批次，不需重新接令或索取重复的同范围授权。

以原 06 工作树 `D:/知识图谱-worktrees/assistant-06-integration-test` 为每条控制命令的显式工作目录，运行上述已审核版本的脚本。脚本内获批 Maven 工作目录仍为主项目 backend-java。保留既有修改，不修改其他任务文件，不创建新会话或工作树。

严格按以下顺序执行：

1. 完成正常执行前的文件版本、旧证据、工作树及环境身份门禁；不因复用审核而跳过实际执行所需的安全核对。版本漂移或未知修改停报，不能临时把 expected hash 改绿。
2. 编译或启动前，按批准位置受限保全原 XML 和累计 general log 截面，核对复制结果。所有新输出使用唯一 runId/后缀；不 clean、不覆盖历史 XML、索引或旧验证 JSON。
3. 使用原 MySQL 8.4.11、UUID `8c6cfcb3-ba49-11f1-bf64-005056c00001`、127.0.0.1:33078、库 `kg_assistant_i2_20260927_r01`；原数据/DDL/权限和 7901 规模不变，不装载或重置样本。loader 保持锁定，诊断子进程仅持原 reader 凭据。
4. 保持 connect=3000ms、socket=4000ms、JDBC statement=2s、session=1500ms。五秒输出收集不增加这些数据库预算，也不扩大五分钟子进程运行上限。
5. 只执行 E01 outgoingEdges(AB,7702,10)、E02 outgoingEdges(AB,7701,10)、E03 incidentEdges(AB,7701,10)、E04 incidentEdges(LIB,7701,10) 对应的完整 EXPLAIN FORMAT=JSON。AB/LIB、方向、双端/来源过滤、has_source、ORDER BY 和 maxRows=11 均不改。四项各一次，不执行原业务 SELECT、EXPLAIN ANALYZE 或其他性能批次，不自动重试。原身份/权限、规模及静态窗口快照的必要只读核验按批准流程单独记录。
6. 实际验证输出管道和日志完整性，保留成功、失败、未达及收尾证据。普通 EXPLAIN 错误留证后仅继续批准的独立项；身份/权限/前像/数据/零写入异常、输出不完整或其他安全阻塞立即停止后续项目并安全收尾。不得现场改研究代码、SQL、索引、数据、迁移、权限、预算或测试标准。
7. 完成同次启动的 reader 窗口前后数据/DDL/binlog、权限、loader 锁定及残留查询核对；只对身份已核实且本批启动的原实例正常停库。停库失败或身份不明时报告残留，不自行强杀数据库进程。

遇编译或诊断驱动问题，保留本轮尝试和原始错误，不通过重跑已执行 EXPLAIN 凑绿灯，不借修驱动扩到领域文件。需要改变已审核实现或操作范围时，先汇总问题交总控决定。

## 4. 交付与停止点

06 完成执行后，在已有报告追加真实运行记录，给出本轮 runId、INDEX、每项计划/错误及有序参数、XML/日志证据、环境/预算、零业务写入与收尾。内存验证六组、实际 Maven 尝试、四项 EXPLAIN 的执行数与完成数分别报告，不混算。

完成或遇停止条件后即停，交负责人转总控审核。此指令不授权恢复 03 开发；总控审核完整脱敏证据后另给原 03 明确指令。不得自动进入补丁汇集、统一回归、其他 I2 遗留、I3 或 W2。四份计划即使全部取得，也不等于查询性能修复、业务语义通过或 I2 验收。

## 5. 真实归属与模型

- 原 06：本次输出收集修订、overlay/报告、内存验证脚本及六组验证的实际作者/执行者；尚未提交真实环境运行结果。
- 总控：差异审核、直接文件 hash 核对、AST 语法解析和本续作指令；没有代写运行器、重跑内存验证、编译、执行 runner、读取凭据或连接数据库。
- 原 03：待命，研究领域实现未因本轮日志修订改变。
- 模型要求 GPT-6 Sol / High；负责人于 2026-09-29 已单独确认 06 并统一确认原 01～06。工具独立回读实际模型、推理强度未核实，不重复索取本次人工确认。总控不在该六任务确认范围内。
- 真实环境执行者在本指令形成时仍为“尚未执行”；未来必须按 06 的真实运行证据填写，不能把总控准备指令记为取证完成。最终验收由负责人决定。
