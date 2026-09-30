# I2-R4-D2：原 03 诊断模板增量技术审核

日期：2026-09-29。总控只审核新增模板、报告追加及直接前像。负责人已批准 D2 有界诊断方案；本结论允许按该方案准备原 06 的明确执行单，不代表探针已经执行、生产查询已修复或 I2 已验收。

## 1. 结论与核对范围

未发现阻断原 06 使用模板的定义问题。未重复全文审核未变的 R4 历史证据/领域契约，未扫描全仓，未运行 Java、单元/集成测试、runner、EXPLAIN 或连接数据库。

输入：

- [模板 JSON](D:/知识图谱-worktrees/assistant-03-research/docs/assistant-team/reports/I2-R4/D2-DIAGNOSTIC-TEMPLATES.json)：39285 字节，raw SHA-256 `531988319b3aed79a8bcd3f5c9088f4bc7f11273573553bb8a183af309c68d01`。
- [03 报告](D:/知识图谱-worktrees/assistant-03-research/docs/assistant-team/reports/03-research-I2-R4.md)：13338 字节，raw SHA-256 `38c7594ef9edae9a561e53524ba47c9675b3c847f74d0a9016f8c9581ec59b5c`。前 9411 字节 hash 仍为 `26c84155fd83319148495d5311015cdc94c54a76c4654d9c33702c62a29d0e76`，确认原报告按字节完整保留，本次只是追加。
- E02 原件 raw hash `72edc590f96d98642e530d8f6aadcd8614b29014f7497817fa7a2e4cdbfde609`、R4 INDEX raw hash `cb57d0fa2d7c2e0c39ac758119d31e958ac06e2920b99f3cc2362f65023cd5d2` 均匹配。
- 主项目和原 03 工作树研究仓储规范化 hash 均为 `b922ec24fc397fd772524524c34c6837a3f044bff4bd18efcdd23c37ae67612f`，既有仓储测试均为 `1b7c80d568cd0e75b982427c10c404b90f608ea8dee83daf4b92843a131277ce`。没有据这四项抽核宣称本轮重新核验全部冻结依赖。
- 原 03 当前分支 codex/assistant-03-research、HEAD bcd3b60ea4e6e18daad53f31787028a0f69bc6cf 与已有基线相符。

机器化静态核对记录：[I2-R4-D2-TEMPLATE-REVIEW.json](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-D2-TEMPLATE-REVIEW.json)。它是总控文本/JSON/文件核对记录，不是新业务测试通过数。

## 2. SQL 差异与语义边界

| 探针 | 审核结果 | 预期绑定数 | source/target/edge 文档证据条件保留数 |
| --- | --- | ---: | --- |
| P1 | E02 第八列的 has_source EXISTS 精确替换为 0；完整 WHERE、JOIN、方向、排序和分页不变 | 22 | 1 / 1 / 1 |
| P2 | 独立从 P1 仅删除 WHERE 中的 edgeDocumentScope | 16 | 1 / 1 / 0 |
| P3 | 独立从 P1 仅删除两个端点的 node_evidence 文档条件 | 10 | 0 / 0 / 1 |

总控在内存重建每次替换/删除，检查唯一片段匹配数与前后定位上下文，并逐字符比对结果。P2/P3 没有叠加，双端 library_nodes、真实出边方向、ORDER BY e.id、maxRows=11 均保留。

常量与过滤删减均只属于诊断定义。即使得到计划，也不能据此允许恒定 has_source、删除生产授权条件或断言变体与原业务语义等价。

## 3. 参数、hash 与条件分支

- 独立按参数出现顺序和数组元素顺序展开：E02 重建的 26 位 SQL/值/类型与原真实边界记录一致。
- P1/P2/P3 的完整 EXPLAIN SQL、预期问号 SQL、位置/参数名/元素下标/值/Java 类型一致；48 个预期绑定位置逐位核对。最后 maxRows=11 为 Integer，其余为 Long。
- 三种 SQL 字符串各自 hash，共九个，与模板一致；按解码字符串原样 UTF-8 计算，不 trim、不折叠空白。
- 人工审核 JSON 执行门禁：P1 一次，取得有效计划即跳过 P2/P3；只有 HY000/3024 才允许两个独立对照各一次；其他失败停止。此处未实际运行任何分支驱动。
- 模板明确 notJdbcVerified。新探针实际预处理 SQL、绑定和 session/statement 预算，仍须 06 在真实执行边界验证，不能用本次静态结果替代。

核对问题数为 0；不因此生成任何新的 MySQL 成功数、完整业务结果或 p95。

## 4. 后续交接

原 03 停止并待命。按此前已批准方案，由负责人把 [原 06 正式执行单](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-D2-06-EXECUTION.md) 转发原 06；不自动派发、不新建任务，不再增加一次相同范围的接令检查。

原 06 只开发独立诊断驱动并按条件取证，不能修改研究实现、模板或旧运行包。新 overlay 以本次模板、当前报告和上批 R4 封存状态为基线；不改历史 expected hash。新批次执行前文件/环境/安全核验不可省略。若出现新冲突或范围外需要，停报而不是在现场放宽。

06 完成或触发停点后整批交总控审核，再交原 03。没有自动恢复 03 领域开发、统一回归、业务 SELECT、其他 I2、I3 或 W2 的授权。

## 5. 实际归属

- 原 03：本次诊断 SQL 定义、静态自核和报告追加的实际作者；本轮没有领域补丁或测试运行。
- 总控：本次差异、参数/hash/报告前缀与直接代码前像审核，新增本审核记录及 06 执行单；未代写诊断 SQL/驱动，未编译测试、读取凭据或连接数据库。
- 原 06：尚未执行 D2；历史四项 EXPLAIN 的实际执行者仍为原 06，归属不变。
- 原 01～06 模型要求 GPT-6 Sol / High；负责人于 2026-09-29 在本会话确认。工具独立回读：实际模型未核实；实际推理强度未核实。总控模型不在该六任务确认范围内。
- 最终验收由负责人决定，本审核不替代负责人验收。
