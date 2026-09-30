# I2-R4 原 06 运行前修订：增量复核

日期：2026-09-29。结论：主要修订已核对，原 C 组仍余一项日志完整性缺口；暂不恢复真实取证。不是发现新的研究 SQL 问题，也不是重新打开已完成的 A/B 审核。

## 1. 本次版本和审核方法

只比较原 06 保留的三份草稿与当前修订，以及对应的直接依赖/证据链；不重复全文审核未变化的研究实现或历史阶段。读取新增修订回报，不执行 runner，不调用其函数。

- 当前 Java raw SHA-256：`5c9aebaea80b6459e273a6868dcf32d42359f1a3678001f3697d5bc8dc7429fd`。
- 当前 runner raw SHA-256：`2a7a68ba427fe2206a6f165c49c933ccaf2c3e184dd53db11dedf4b84b1e955a`。
- 当前 overlay raw SHA-256：`42985fe20063c9d920bdeb2e5ac3a13e0a9b96dc08cea5897bf46703832974ac`。
- 原 06 修订回报 raw SHA-256：`360d60f7c4d62014de245147b2ef3c618ba3ca54ad6b33a86b9784bb0f20c16d`。

主项目 Java：`D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2ResearchEdgeExplainTest.java`。

runner/overlay 位于 `D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-EXPLAIN/`，草稿副本位于其 `evidence/preexecution-review/`；报告为 `D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/reports/06-integration-I2-R4-explain.md`。

总控本轮实际核对：11 项 rawReferences 和 2 项 newFiles 全部匹配；3 份草稿副本均与前次审核前像匹配；5 项规范化 preimages 全部匹配；PowerShell AST 解析错误为 0。R3 封存索引对 package/protection/summary 三项的匹配各 1 条，protection 对两个导入 helper 和权限环境清单的匹配各 1 条。AST 只解析语法，没有求值或运行脚本。

## 2. 已闭合部分，后续不重复审核

- A：实际导入 helper、权限清单、R3 package/protection/summary 已追溯并固定到历史封存版本，门禁位于函数导入、凭据读取及启动之前。E01 期望 SQL 改从受控 POSTRUN-SUMMARY 读取，当前摘要包含原失败 SQL；不存在把未知当前文件直接盖章为旧基线的情况。
- B：数据库 Stop-Process 分支已移除；身份未核实不发 SHUTDOWN，正常停库错误及进程/监听残留分别留证。Maven 子进程取消与数据库处置分开。
- C 的保全数量：由实际复制并复核的集合计算，保全阶段和已记录/已核验数分别报告，不再无条件写 32。
- C 的 XML 与收尾错误：新 XML 路径、存在性、大小/hash、suite 计数与 EXPLAIN 执行数进入索引；未达、跳过、失败分别记录；原错误和收尾错误分开保存。
- 预算对应：Java 已在用于 EXPLAIN 的实际连接上读取并核对 session 1500ms，statement 2s 约束保留。没有调整原 SQL、实例、数据或性能预算。

上述为静态修订核对，不是编译/测试或真实环境验证；不能据此把 I2-R4 或原超时标为通过。

## 3. 唯一继续阻断项：C 组输出流尚未收集完时会遗失日志

位置：[runner 第 101 行](D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-EXPLAIN/Invoke-R4EdgeExplain.ps1:101)及其后状态处理。

当前逻辑仅即时检查 stdout/stderr 的 ReadToEndAsync 任务是否 IsCompletedSuccessfully。若尚未完成或已 fault，则直接写 `[stdout unavailable or incomplete]` / `[stderr unavailable or incomplete]`，之后没有有界等待、补收集或对应的证据缺失错误记录。

进程退出观测不等于这两个独立异步读取任务已经成功完成。该分支一旦发生，即使已有诊断 JSON 且子进程 exit=0，也可将 case 标为 plan_captured；日志中的占位文字不会加入 cleanupErrors，最终仍可能被记录为 four_plans_captured。原 C 组要求的失败过程留证因此尚未完全落实。

这是静态可达分支和证据风险，并非声称本轮已经丢日志。06 尚未运行，四项 EXPLAIN 均未执行。

## 4. 给原 06 的定向续作要求

无需重做接令、模型确认或已闭合 A/B 检查。只在现有批准的 runner、overlay、报告/证据路径内补齐本项；不改 Java/研究域或其他文件来扩大任务。

1. 正常退出和有界取消后，对两个输出读取任务进行有界收集；等待与取消路径也保留已取得的实际输出，不把尚未完成简单等同于没有内容。
2. 若流读取 fault、取消或在收集上限内不能完成，分别记录该流状态、可用输出、原因及证据不完整状态。不能把占位文件当完整日志；此类证据异常应停止后续诊断项并进入安全收尾，不能返回完整成功。
3. 确保日志收集异常不遮蔽原始错误，也不跳过 EXECUTION/收尾记录。不得为修复日志而提高 SQL/连接预算、扩大五分钟子进程运行预算、自动重跑 EXPLAIN 或强杀数据库。
4. 在不触发真实 runner 顶层、凭据、Maven/数据库的条件下验证即时完成、延迟完成、读取异常/取消这几类分支；使用内存替身或现有获批证据位置记录结果。若验证方式需要新的源码/测试路径，先列清单，不自行扩围。
5. 保留当前修订前像和新增量记录，更新本轮文件 hash；报告哪些分支仅静态检查、哪些有不触达环境的验证，不能把验证方法数或重跑计为真实 EXPLAIN 执行数。

完成这项后停止并回报。总控下一次仅复核该输出收集差异及影响；闭合后再给明确的真实取证续作指令。当前不得直接运行现有 runner，不执行四项 EXPLAIN，不编译或连接数据库。该意见不要求删除已有成果或重建任务，也不新增领域修复轮次。

## 5. 归属、授权与阶段状态

原 06 是本次 Java/runner/overlay/回报的实际修订者；总控只增量审核和新增本记录，没有代修文件。总控执行的 AST 解析与 hash 比对均为静态审核，不是编译或运行测试。本轮没有汇集、读取凭据、启动或连接数据库。

负责人于 2026-09-29 已确认原 01～06 为 GPT-6 Sol / High；工具独立回读实际模型及推理强度未核实，人工确认无需重复。原数据库补证授权保留，但当前技术门禁尚未解除。03 继续待命，不自动推进其他 I2、I3 或 W2；最终验收由负责人决定。
