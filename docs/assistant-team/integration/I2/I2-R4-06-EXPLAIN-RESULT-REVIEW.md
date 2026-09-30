# I2-R4 原 06 四项真实 EXPLAIN：总控证据审核

日期：2026-09-29。runId：`R4Explain-20260929T164517990`。

## 结论

本批执行及证据留存可交接给原 03，未发现需要退回 06 重做本批的证据不一致。结论是 **三份 EXPLAIN 超时错误和一份库范围计划**，不是取得全部计划、修复完成或 I2 验收通过。原 06 停止待命，不再次运行同一批。

| 项 | 原调用 | 本批结果 | 记录耗时 |
| --- | --- | --- | ---: |
| E01 | outgoingEdges(AB,7702,10) | HY000/3024，无计划 | 1659ms |
| E02 | outgoingEdges(AB,7701,10) | HY000/3024，无计划 | 1648ms |
| E03 | incidentEdges(AB,7701,10) | HY000/3024，无计划 | 1668ms |
| E04 | incidentEdges(LIB,7701,10) | JSON 计划取得 | 131ms |

这些是 EXPLAIN 调用耗时，不是目标 SELECT 的耗时或 p95。E01～E03 的根因仍未确定，不能因为也返回 3024 就断言是某个索引、某一层 EXISTS 或优化器阶段问题。

## 实际审核与证据对应

只审本次新结果、报告追加段及相关运行证据；不重审已闭合运行器实现，不运行脚本、编译、测试或数据库。

- INDEX raw SHA-256 为 `cb57d0fa2d7c2e0c39ac758119d31e958ac06e2920b99f3cc2362f65023cd5d2`。总控重算其中 23 份公开证据、4 份本轮 XML 的大小/hash，均匹配。
- 读取四份 XML 的测试结果而不输出系统属性：同一测试方法定义执行四次，E01～E03 各 1 failure，E04 通过；0 error、0 skipped。不是四个新的测试方法定义，也不是原领域回归通过。
- 四份 JSON 的 executeQueryCount 均为 1；四份 EXECUTION 各有唯一后缀、退出码、完整输出流、无截断/落盘错误。E01 的 R3 原 SQL 对照已通过，E01/E02/E03/E04 有序绑定分别为 26/26/27/7 个，末位 maxRows=11 为 Integer，其余为 Long；实际 EXPLAIN 连接 session=1500ms，statement=2s。
- 对当前累计 general log 仅检查原封存截面之后的新内容：出现 4 条目标 EXPLAIN，未见目标 `SELECT e.id,e.source_node_id,e.target_node_id,...` 业务语句直接执行。未将日志、密钥或原 XML 敏感属性复制入本审核/领域交接材料。
- 32 份受限副本大小/hash 均与保全清单匹配，且每一项均能对应 R3 历史索引中的 31 份 XML 或累计日志截面。保全目录 ACL 为禁止继承的受限状态。总控仅核对现有副本，没有重新保全或复制。
- 从实际前后快照重新比较：34 表数据和 DDL 一致，binlog 位置一致，loader 前后 Y；entry 数据/DDL 与 R3 最后封存快照一致。ZERO-WRITE 记录 reader 授权一致、活跃 reader 查询 0；当前 general log hash 与其记录一致。
- 身份证据为原 8.4.11、UUID `8c6cfcb3-ba49-11f1-bf64-005056c00001`、33078、原 datadir；7901 仍为 10000/30000/2000/20000。SHUTDOWN 记录正常停库、启动器退出、无残留进程或监听、没有强杀。以上为对 06 已保存记录的审核，不是总控实时连接或端口探测。
- RUN 状态为 completed_with_explain_errors，三项普通 EXPLAIN 错误，primaryFailure 为空、cleanupErrors 为空，与结果和收尾记录一致。
- 主项目与原 03 的研究仓储规范化 hash 当前仍同为 `b922ec24fc397fd772524524c34c6837a3f044bff4bd18efcdd23c37ae67612f`。06 本批未交付领域补丁。

## 可供领域分析的信息与不能推出的结论

E01/E02 同为 AB 出边，区别为 nodeId；E03 为 AB 双向关联，三者的 EXPLAIN 均在原预算下失败。E04 仅库范围可取得计划。这为“比较文档范围分支与库范围分支”提供了真实观察，但不是单独某个条件造成超时的证明，不允许据此删去授权或正式来源过滤。

E04 计划从边表 index_merge 开始，使用 sort_union(uk_edges_source_target_relation,idx_edges_target)，后接两端 library_nodes/节点表 eq_ref；排序含 filesort，select-list 证据子查询依赖边 ID。query_cost=13.45、边表 rows_examined_per_scan=4 都只是该计划估算。不能把它当成 E01～E03 的计划，也不能用 filesort 的存在直接断言瓶颈。

本批没有执行原业务 SELECT，因而原失败方法的后续业务断言仍未通过本批验证。三份缺失计划、真实查询正确性和修复后性能仍是明确缺口。

## 交接及归属

新增 [诊断证据 overlay](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-03-EXPLAIN-EVIDENCE-OVERLAY.json)只引用本次证据，不改原 R3/I2-R4 索引或 expected hash。交接入口为 [原 03 续作指令](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-03-RESEARCH-RESUME.md)。03 只消费脱敏公开结果，不接收数据库账号或受限原件。

原 06 是诊断代码/脚本、内存验证、四次 Maven/诊断测试及真实环境执行者；本批没有领域补丁汇集者。总控只规划、审核和准备交接，本轮未编译测试或连接数据库。原 03 后续领域修改必须由原 03 完成并单独报告，不把 06 的取证归为 03 已修复。

负责人于 2026-09-29 已确认原 01～06 的 GPT-6 Sol / High；工具独立回读实际模型/推理强度未核实。最终验收由负责人确认，不自动推进剩余 I2、I3、W2。
