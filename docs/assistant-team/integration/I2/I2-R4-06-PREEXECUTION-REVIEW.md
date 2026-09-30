# I2-R4 原 06 中断成果：运行前增量审核

日期：2026-09-29。当前状态：诊断实现中断，未编译/运行验证，四项 EXPLAIN 均未执行。本记录不是正式交付验收，也不自动恢复任何任务或启动数据库。

## 1. 审核范围与前像

本轮仅审核原 06 新增的三个文件及其直接复用边界，不重复旧领域实现和已完成的历史审核。

| 文件 | 当前 raw SHA-256 |
| --- | --- |
| 主项目 backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2ResearchEdgeExplainTest.java | 15669890eee0c1b071c8b502d4d0d3170b1a3b0e340b8849e392db29d92d91c9 |
| 原 06 docs/assistant-team/integration/I2/R4-EXPLAIN/Invoke-R4EdgeExplain.ps1 | d3ca0cc561dc71a3469ee402500e642153e2e968a19e749a3a2e96c303054de4 |
| 同目录 R4-EXPLAIN-OVERLAY.json | 101da52c43d95c15461e87b642c75288c5e929e656ccd91cd408171c820ef519 |

原 06 工作树为 `D:/知识图谱-worktrees/assistant-06-integration-test`，主项目为 `D:/知识图谱`。行号均对应上述前像。

总控实际进行了文件阅读、直接复用函数/清单的定向检查，以及 PowerShell AST 静态解析；解析未报告语法错误。AST 解析未求值脚本，不是执行、编译、测试或环境验证。没有读取/解密凭据、探测/启动/连接数据库，也没有保全旧环境证据。

据原 06 中断回执，测试、脚本和 overlay 均未运行，阶段报告和结果索引未生成。此处不把中断草稿当作 06 宣称完成的交付，更不将下述潜在分支风险写成已经发生的环境事故。

## 2. 技术结论

SQL 捕获的基本方向符合批准方案：调用未修改仓储的 mock JDBC 边界，保留完整模板及命名参数，再生成四个独立 EXPLAIN；原业务 SELECT 不在测试中的执行路径。E01 与原 R3 失败 SQL 对照、唯一输出文件和逐位置绑定记录也已写入草稿。

但是当前版本还不能直接运行真实环境。以下三组问题应由原 06 在原批准文件范围内集中补齐，总控不代改。

### A. [P1] 被执行的复用函数及部分关键参考尚未进入前像门禁

[脚本第 158 行](D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-EXPLAIN/Invoke-R4EdgeExplain.ps1:158)起，从 `Invoke-I2Environment.ps1` 动态导入 Invoke-I2Sql/Unprotect，从 `Invoke-I2BaseVerification.ps1` 导入 Snapshot-I2 并执行。现 overlay 未固定这两个实际代码来源，Import-FrozenFunction 只检查可解析和函数唯一，不校验其历史版本。

脚本第 152 行加载 R3-PACKAGE.json 后只检查 environmentFiles 的四项配置/二进制；没有先把该包自身与已信任的 FINAL-INDEX 对齐。I2-ENVIRONMENT.json 决定期望授权，R4 研究证据 JSON 决定 E01 期望 SQL，也未在当前 overlay 中得到版本检查。06 入职时的历史核对不替代即将执行这些文件时的变更门禁。

续作要求：沿已封存索引/清单追溯实际执行及关键预期的版本，只补这些直接依赖的校验；在函数导入、凭据读取和实例启动之前生效。不能把未知的当前内容直接重新盖章为可信前像，不能改旧 expected hash，不能执行旧整批 guard 或关闭校验。新诊断文件补修前后 hash 用显式新增量记录，保留本次前像。

### B. [P1] 停库失败分支超出了本批“正常停止”的既定边界

[脚本第 239 行](D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-EXPLAIN/Invoke-R4EdgeExplain.ps1:239)起，只要正常 SHUTDOWN 未确认，就会 Stop-Process 终止服务/启动器；包括实例身份未完成核实的路径。本批批准的是身份核实后的正常停库，没有批准自动强制终止数据库进程。

续作要求：保留受控的正常停止和残留检查，移除未经批准的数据库强杀回退。无法确认身份或正常停库失败时停止后续操作，完整记录已知进程身份、错误和可能残留，交总控/负责人决定下一步，不将失败写成正常收尾。不得以 PID 历史值或端口单独认定可处置对象。此问题不等于禁止对本批自有 Maven 子进程实施原有有界取消；二者须区分。

### C. [P2] 失败路径和运行索引的证据还不完整

- [脚本第 76 行](D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-EXPLAIN/Invoke-R4EdgeExplain.ps1:76)的子进程超时分支先终止并抛错，跳过后续 stdout/stderr 落盘及该项 EXECUTION 记录；诊断错误发生时可能缺少最关键的原始过程证据。
- [第 215 行](D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-EXPLAIN/Invoke-R4EdgeExplain.ps1:215)、第 237 行等收尾 catch 仅在没有既有 failure 时设置简短标志，无法保证同时保留原业务/构建错误与所有收尾错误。
- [第 252 行](D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-EXPLAIN/Invoke-R4EdgeExplain.ps1:252)将 restrictedPreimageCount 写死为 32；若前置失败、只复制一部分甚至尚未开始保全，也会输出 32。索引应明确实际完成/未完成数量和保全阶段，不能仅用历史预期数冒充本轮实测。
- 当前 INDEX 只遍历本轮 evidence 目录，没有纳入主项目 target 下本轮 Surefire XML 的路径、大小、raw hash 和执行数量。应记录本轮实际 XML（或明确缺失），区分一项测试方法的四次诊断执行、构建失败、未到达和跳过；不把历史 XML 当新结果，也不向公开证据复制 XML 中的敏感属性。

续作要求：失败也能形成可追溯的本轮证据；原始错误与收尾错误分别追加保留，完成数来自本次实际活动。修复不能重跑已经执行过的 EXPLAIN 来凑绿灯；目前四项未执行的事实继续保留。

## 3. 随续作一并核实，不预设新的性能问题

Java 第 100 行读取 session 预算所用的 jdbc 连接，与第 105 行后执行 EXPLAIN 的连接是分别申请的。底层连接工厂目前都会设置 1500ms，但计划证据应清楚对应实际 EXPLAIN 连接，或者明确这条记录的证明边界，避免将另一个连接的观测冒称为本次连接实测。不得改变 1500ms/2s 等原预算。

本轮没有编译，不判定 Java 一定编译成功；本轮没有运行脚本，不判定以上修正足以保证真实取证完成。原 06 还需完成批准范围内的检查和实现验证。

## 4. 接下来如何继续

不需要重建会话、删除三个文件或重新做完整接令。原 06 模型人工确认已经满足，沿用 [负责人统一确认](D:/知识图谱/docs/assistant-team/MODEL-CONFIRMATION-20260929.md)，不重复索取。

下一步建议负责人转发本审核给原 06：先在原批准诊断测试、脚本、overlay 和报告/证据位置内集中补齐上述问题，保存本次草稿前像及修订记录；涉及其他文件或新增权限先申请。编译前仍须遵守旧证据保全要求，不运行 clean，不修改研究源码或既有测试。对失败/收尾分支的验证优先使用不触达真实环境的方式；若需要新增获批路径之外的测试文件，先提出清单。

本次审核不自动恢复原 06 或执行数据库。运行前问题补齐后，由总控增量复核修订及验证证据，明确续作停点；不得直接运行本审核的三个旧 hash 对应版本。原 03 保持待命，四项计划及后续领域修复均未完成。

## 5. 实际归属

- 原 06：据回执新增诊断测试、脚本和 overlay；未编译测试、未读取凭据或运行真实环境。
- 总控：只审核新增内容及直接依赖，静态解析脚本，新增本审核文档；没有修改实现、SQL、测试或运行器。
- 本轮汇集者、编译/测试运行者、真实环境验证者：无。
- 原 01～06 要求 GPT-6 Sol / High，负责人于 2026-09-29 已统一确认；工具独立回读实际模型/推理强度未核实。总控自身不在该六任务确认范围内。
- 最终验收由负责人决定。本次结论仅为“中断草稿存在运行前待补齐事项”。
