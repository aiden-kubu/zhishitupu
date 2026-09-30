# 原 03｜I2-R4 research 边查询链路定向任务

准备日期：2026-09-29。原桌面任务 ID：01a0c6f9-fdb5-7d72-afa7-3eeda840413a。
工作树：D:/知识图谱-worktrees/assistant-03-research；分支 codex/assistant-03-research；准备时 HEAD bcd3b60ea4e6e18daad53f31787028a0f69bc6cf。
主项目 D:/知识图谱只读。模型要求 gpt-6-sol / high，正式开工前负责人在原桌面确认；工具不能回读时写实际模型/推理强度未核实。

## 0. 授权和串行门禁

负责人已决定由原 03 分析/修复此研究领域问题，由 06 后续集成验证。本文件现为已准备、未派发的任务，不表示原 03 已启动。本轮转发入口只授权增量接令检查；06 完成只读入职并停止后才发送原 03，接令回执后再发明确正式执行指令，不反复相同版本检查。

总控不修研究源码；06 不改研究源码/SQL/领域单元测试。03 不运行数据库、不接收数据库账号、不改主项目、不汇集、不改集成验收脚本。领域离线完成后停止，总控审核；06 只有收到后续明确批次指令才汇集/回归/真实复验，总控审核其证据，负责人验收。不自动进入其他 I2 工作、I3/W2。

## 1. 已知事实与证据缺口

R3 真实批次 Real-20260929T104824880：质量 Q01～Q10 和既定性能采样通过；跨领域 Base 35 场景中 34 通过、1 error，总体真实 43/44。

失败位置：主项目 I2JdbcBaselineTest.researchEdgesKeepDirectionAndBothEndpointScope，第 95 行：
research.outgoingEdges(AB,7702,10)，预期空列表。
AB=libraryIds {7101}、documentIds {7201,7202}、nodeIds 空；内部 maxRows=11。
实际 MySQL 3024 / HY000：maximum statement execution time exceeded。
该方法总耗时 1.566s，包含已执行部分，不当作单条 SQL 精确耗时。

同方法第一条 incidentEdges(LIB,7701,10) 返回两条的断言已执行；后续 outgoingEdges(AB,7701,10) 对 7801 方向及 incidentEdges(AB,7701,10) 对范围的断言尚未到达，不能写为通过。

目前只有这一次业务失败记录，未重跑。没有该研究 SQL 的真实 EXPLAIN/ANALYZE，不能把旧质量 LIST 的执行计划套用为研究查询根因，也不能先断言是某个索引或相关子查询导致。R3 没改研究源码，不能凭此认定质量补丁造成研究超时。

精确失败 SQL、原始路径/hash、两端前像和只读参考见 I2-R4-RESEARCH-EDGE-EVIDENCE.json。原错误与质量绿灯均保留，不改 R3 报告为全绿。

## 2. 增量必读及前像

先读本任务单、新增 evidence JSON、原始 Base 失败用例/SQL/堆栈、I2MySqlSupport 中 scope/预算定义、R3 当前报告涉及新失败/环境/停点的部分。主项目 AGENTS.md 和协调/总控规则只读新增 06 调整及影响；若首次未读则完整阅读。原 W1 已读任务/报告和冻结依赖可复用，发生变化才扩大。

准备时主项目与原 03 的 10 份研究源码/测试均规范化 hash 一致。目标前像：

- JdbcResearchReadRepository.java：b922ec24fc397fd772524524c34c6837a3f044bff4bd18efcdd23c37ae67612f。
- JdbcResearchReadRepositoryTest.java：1b7c80d568cd0e75b982427c10c404b90f608ea8dee83daf4b92843a131277ce。

规范化：UTF-8、CRLF 转 LF、保留结尾换行、不 trim。新测试、新报告、新证据目录准备时未占用；接令时再次核对，不覆盖。原 Git 已有修改保留，不因未跟踪而猜作者。

18 项 W1 只读依赖继续冻结。接令复用已有核对结论，必要核对变动/直接影响项；正式交付给出冻结依赖当前一致性证据，不改清单 expected hash。质量 R1～R3 改动不是 03 新依赖，不要求把整棵其他工作树同步到 03。

## 3. 正式执行后的修改白名单

仅在原 03 工作树：

1. backend-java/src/main/java/com/knowledgegraph/assistant/research/JdbcResearchReadRepository.java
   - 分析/修复 outgoingEdges、incidentEdges 共用 edges 查询链路及其必要的边查询专用 private 片段。
   - nodeScope/documentScope 等共享辅助逻辑若涉及，必须保留它们对 searchNodes/node/原文检索等其他调用生成 SQL、绑定和语义不变；需要改变其他方法行为或公共边界时停报。
   - 不对无问题查询做风格统一，不预设 CTE、JOIN、子查询或索引提示等具体修法。
2. backend-java/src/test/java/com/knowledgegraph/assistant/research/JdbcResearchReadRepositoryTest.java
   - 原 edgeQueryChecksBothEndpointsAndRealEvidenceBeforeLimit 的 SQL 形状断言可随批准的等价实现精确调整，但以同等或更强的范围/方向/来源/分页保护替代，并逐项解释映射。
   - 其他四个用例保持原样，不删断言、放松结果或改 mock 来迎合错误实现。
3. 新增 backend-java/src/test/java/com/knowledgegraph/assistant/research/JdbcResearchEdgeQueryPerformanceRegressionTest.java。
4. 新增 docs/assistant-team/reports/03-research-I2-R4.md。
5. 新增 docs/assistant-team/reports/I2-R4/ 下独立轮次证据 JSON/脱敏日志索引；不修改历史 W1 报告。

不修改 ResearchReadRepository 契约、ResearchAgentToolProvider、ResearchComparison、source/核心/其他领域、现有业务服务、集成 i2 测试、pom、索引、迁移或权限。若需要更多生产/测试文件，先提出准确清单和理由，不能因为“在 research 目录”就扩大。

## 4. 必须保留的业务语义

- 授权 scope 的 libraryIds/documentIds/nodeIds 各维度按原交集，单维度内多个 ID 按原集合语义；PROJECT/SELECTED 的合法边界不变，不用空集合误扩大范围。
- 先按真实 source/target 和两个端点可见性硬过滤，再 ORDER BY e.id、limit+1。不能先截断全图再过滤，不能额外泄露范围外边或节点。
- outgoing 只出边；incident 双向但保留真实 source→target。不复制自环，不把反向边或同端点不同类型边合并，不因多条 node/edge evidence 或多库归属造成边重复。
- document 范围存在时，两个端点的 node evidence 以及 edge evidence 均满足既有正式来源要求；各自可来自允许集合中的不同文档，不新增“必须同一文档/chunk”限制。
- 原文正式条件保持 COMPLETED、READY/OCR_OK、metadata 缺省/null 或 active、主库或 topic assignment 属于授权库等既有规则。
- has_source 仍按真实范围内证据计算，不恒真，不用引用存在替代正式来源；当文档过滤为空时，保持当前边可见性与来源标志的区别。
- 不从 quality 的 active-edge 统计语义抄入 research。本仓储当前返回状态/元数据的语义不变，不擅自过滤 inactive 节点或边。
- 保持真实 Long ID、字段位置/时间/属性白名单、稳定 ID 升序、limit+1、hasMore、空结果、异常传播；不吞 3024 返回空列表/partial 冒充成功。
- 不新增缓存陈旧窗口、不通过提前短路对非空授权范围返回固定空值，不修改超时或返回契约。

## 5. 分析与离线测试计划

先形成 outgoing/incident 共用查询及 scope 分支矩阵；区分证据已支持的事实、静态推断与需要真实 EXPLAIN 验证的假设。不要求为每条 SQL 分别申请新轮，覆盖本边查询链路及影响即可，其他研究功能不顺手改。

新增离线回归至少覆盖：
项目/库/文档/节点及组合范围；两个端点与边来源；跨库归属；多证据去重；自环/反向/多类型边；方向；无匹配；ID 排序和 limit/hasMore；Long 高 ID；状态/元数据/来源标志；参数绑定和异常传播；原非边查询保持不变。
mock 能证明生成 SQL/绑定/映射/异常路径，不能证明 MySQL 真正执行或性能通过。若可安全构造旧实现红灯的新增回归，保留红绿轮次；不能为了红灯写无效输入或人为跑慢。

原隔离回归仍为核心 43＋研究域 24，共 67 个已知展开场景；明确选择原六类：
AssistantRuntimeTest、DecisionParserTest、ResearchAgentToolProviderTest、JdbcResearchReadRepositoryTest、ResearchComparisonTest、ResearchDraftValidatorTest。
新增类单列方法数/展开数。使用 Maven 离线、精确选择类和唯一 surefire reportNameSuffix，不全项目扫测、不引入真实 I2 测试、不改 pom/依赖、不安装组件。若数量变化逐项解释，不直接复制历史绿灯。

没有真实 EXPLAIN 会限制根因确信度；若不足以安全确定修复，先交一份诊断请求（查询/参数/需回答的问题/范围/预算），由总控安排 06 获批补证。原 03 不为绕过此限制自行连接数据库，也不虚构性能结论。

## 6. 正式交付、后续验证和停止

报告列：①实际修改及原因；②检查未修改的查询/分支及理由；③待另批事项；另列证据/推断、契约保持、测试定义/展开/轮次/失败/重跑/hash、文件前后 hash、冻结依赖、实际作者/测试者/模型确认来源。原始失败不覆盖。

后续 06 的预期验证（仅计划，不是现在授权）：相同 UUID/版本/规模/权限/预算下，对原失败调用及同方法未达断言完整执行；验证 outgoing/incident 各授权分支、字段和分页/重复；保留真实 EXPLAIN及正确性结果；原主项目统一回归、新领域回归、既定跨域/质量性能采样、零写入和停库。具体批次/样本安排由总控审核后明确派发，不减少原标准，不强加未经确认的新性能阈值。

交付后停止。总控审核领域差异，不代修；06 获批汇集/真实复验，不改研究代码；最终验收由负责人确认。
