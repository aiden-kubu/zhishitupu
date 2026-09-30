# W1 公共运行核心：阶段交付与接入契约

日期：2026-09-23。前置规划：[W0 总控审阅](W0-REVIEW.md)。

## 当前交付结论

已实现可独立测试的多 Agent 运行核心：同一角色可以有多个独立任务实例，按显式依赖传递成果，而不是一个聊天 Agent 依次扮演所有角色。当前演示链为独立资料 A/B 取证 → 比较成果 → 学习素材交接。

这是一阶段基础实现，不是完整智能助手已上线。尚未注册 Spring Bean、开放运行 API、接入生产模型、真实业务工具或前端工作台。现有资料处理、图谱写入、学习路径保存和测评流程没有改变。

## 已实现的共享协议

实现位于 `backend-java/src/main/java/com/knowledgegraph/assistant/`，新增 9 个 Java 文件，无新增 Maven 依赖。

| 类型 / 入口 | 冻结语义 |
|---|---|
| `AgentTask` | taskId、角色、目标、固定 scope、直接依赖 IDs、工具允许列表、输入 JSON、调用预算、完成策略 |
| `Scope` | 显式 PROJECT 或 SELECTED；库/文档/节点维度为交集约束；授权不可静默扩大 |
| `CompletionPolicy` | 最少有效工具结果、是否必须引用本任务新证据、是否接受 partial 依赖、是否要求实际观察范围精确匹配 |
| `ToolResult` | status、effectiveScope、dataJson、evidence、limitations、errorCode；失败结果不能携带证据 |
| `EvidenceRef` | sourceId、来源类别、对象 kind/id/version、实际范围、定位与摘录；不伪造不存在的版本 |
| `Artifact` | 宿主生成 ID、生产任务、authorizedScope、observedScopes、outcome、摘要、数据、引用、草稿与限制 |
| `ActionProposal` | actionType、目标对象、参数、影响和前置条件；executionAllowed 恒为 false |
| `RunHandle` | snapshot()、completion()、cancel()；仅内存持有，recoverableAfterRestart 恒为 false |

`authorizedScope` 是授权上限，不能表示全部范围已研究。`observedScopes` 只来自有效工具结果及显式依赖成果；没有工具/依赖观察时为空。`scopeMatched` 只表示存在精确匹配的观察范围，**不表示穷尽查询或论断正确**。

`requireMatchingScope=true` 时，范围不匹配会把 completed 降为 partial，并携带 REQUESTED_SCOPE_NOT_MATCHED。多资料任务可以分别建立 A/B 必需依赖；不把多个范围简单合并成未经证明的完整覆盖。领域工具仍须返回分页/截断/缺失等覆盖信息。

Scope 的集合包含校验不是对象真实归属校验。文档属于哪个库、节点与所选资料的关联、版本是否已变，必须由领域适配器查证；当前核心没有连接数据库，因此不宣称已完成这些业务核验。

## 调度与决策规则

- `AssistantRuntime.start(RunPlan)` 在调度前校验角色适配器、工具权限、任务 ID、范围、依赖存在性和无环性。依赖任务授权范围不得大于接收任务范围。
- 同一运行的独立任务使用专属有界线程池，不复用现有 ingestionExecutor；依赖未完成时不占用工作线程等待。
- 每个任务只拿到自己的输入、工具观察及直接依赖 Artifact，不共享其他任务的聊天过程。继承引用仍经过 ID 冲突校验。
- 失败或 needs_input 依赖阻断后续任务；partial 依赖必须显式允许，接受后仍传播 partial，不升级为完整成功。独立分支的有效成果保留。
- JSON 决策仅允许 call_tool、complete、needs_input；拒绝重复字段、尾随 JSON、未知字段、Markdown 包装和过深结构。最多一次协议修复，修复调用同样计入预算。
- 模型只能引用本任务或明确依赖中已知的 sourceId；不存在的引用不能完成任务；相同 ID 不得替换成另一版本或另一对象。
- 工具必须同时在角色权限和本任务允许列表内。WRITE 工具不能注册。只读声明不是安全沙箱：适配器实现仍须人工审阅，不能把隐藏写操作标成 READ_ONLY。
- 操作草稿默认全部拒收。只有为该角色注册可信 `ProposalValidator`，并验证动作、参数、目标归属及范围后，才允许进入成果；验证通过也不授予执行权。
- 调用异常输出稳定错误码，不把异常消息、模型原始失败文本或连接信息返回为成果。摘要/dataJson 仍属于模型产物，不是可执行指令或数据库回执。

## 预算、取消和生命周期

- 同时最多 8 个运行；每次最多 32 项任务；线程数可设 1–8，等待队列 64；过载明确拒绝或返回 QUEUE_FULL，不无限排队。
- 模型/工具尝试次数按全运行和单任务双重原子计数；失败和被拒工具请求也消耗预算。每项最多 256 次，时限最多 1 小时；实际领域任务应采用更小预算。
- 单任务时限从开始执行计，全运行时限从 start 计；JSON、证据、成果、上下文大小另有限制。这里不是 token/金额计费器，也未与 OCR/原有模型并发配额统一。
- 取消与接收任务最终结果共用同步边界；已完成成果不会被事后改成取消。运行中显示 cancelling，尚未完成的任务停止继续决策或调用工具。
- 已获准进入的阻塞调用不是可强制撤回的事务。生产 DecisionProvider/AssistantTool 必须落实连接/读取/数据库查询超时并观察取消信号；核心不承诺中断永久阻塞的适配器。
- close 拒绝新任务、发出合作式取消并让队列中任务结算，不丢弃其 completion。运行结果仅通过调用方持有的 Handle 留在内存；无聊天记录落库、重启恢复或持久化任务查询。

## 子任务审查及修正

本轮只启动原有 03 图谱研究任务，以 `gpt-6-sol / high` 完成独立只读审查；其余四个待命。未创建第六个任务，未同时运行多个子任务。审查报告位于 `D:/知识图谱-worktrees/assistant-03-research/docs/assistant-team/reports/03-research-W1-CORE-REVIEW.md`。

总控采纳并修正三项发现：

1. 授权范围冒充实际覆盖：拆分 authorizedScope / observedScopes，新增显式范围完成策略和回归测试。
2. 最后成果提交与取消的竞态：终态接收与取消使用同一同步边界，取消判定读取已发布终态，不仅查看 future；增加 150 次边界竞争断言。
3. 越界操作草稿：默认拒收，新增角色级 ProposalValidator；测试合法草稿仍不能执行、越界草稿无法进入 Artifact。

另外补上参数校验期间时限到期时不得继续调用工具的检查。本报告中的修正由总控实现并测试；子任务未进行修后复审，不把原静态审查冒称修后验收。

## 验证结果

2026-09-23 本机 Java 17 / Maven Wrapper：编译通过。新增助手单元测试 43 项，连同既有问答、检索、子图组装与响应结构测试共 **76 项通过，0 失败、0 错误、0 跳过**。

```powershell
# 工作目录：D:/知识图谱/backend-java
.\mvnw.cmd '-Dtest=AssistantRuntimeTest,DecisionParserTest,ChatServiceAskTest,ChatServiceContextTest,RetrievalServiceTest,SubgraphAssemblerTest,ApiResponseJsonTest' test
```

覆盖独立 A/B 任务并行取证、比较与学习交接、上下文隔离、证据伪造/冲突、scope 越界、草稿校验、依赖失败/partial/needs_input、取消竞态、已完成成果保留、全局预算、工具预算、超时、队列饱和与关闭。

测试使用内存假工具和决策适配器。未连接 MySQL、未调用项目真实付费模型、未运行 IntegrationTest、未测试真实模型决策成功率、未构建前端或部署。工作树核验未发现子任务越权修改业务代码；主工作树原有未提交修改保留，未提交或合并 Git 分支。

## 下一阶段任务与接入要求

总控先将本阶段契约以受控补丁同步到即将运行的工作树，再逐一下发。不可让子任务基于旧快照重新发明公共接口，也不批量启动五个任务。

1. 01 资料建设：真实只读对象/任务查询、恢复评估、稳定原文定位；严禁调用 process/retry 写流程。先补齐 01 的模型配置占用问题。
2. 02 质量治理：复审四层状态、来源归属/版本核验、准确范围与截断；语义复核放入可计费的决策步骤，不隐藏在工具中调用模型。
3. 03 图谱研究：范围前置过滤、稳定引用、方向正确的关系/路径；A/B 独立任务和比较、交给质量核查。
4. 04 学习教练：消费研究/质量成果，只产出路径/讲解/练习草稿，不覆盖现有路径及历史。
5. 05 测评分析：成绩可信度和准备条件；正式判分尚不存在时返回 unavailable，不用客户端旧分数冒充有效凭证。
6. 总控：生产 DecisionProvider、业务任务规划、统一模型配额、运行 API/鉴权、前端任务状态与成果界面、真实测试配置下的端到端验收。

接入时必备：参数白名单、真实对象归属和版本检查、查询范围前置、截断说明、超时/取消、默认拒写、草稿校验及独立单元测试。具有副作用的执行器、幂等、正式路径版本和服务端判分仍属后续 W2，不包含在本阶段交付中。
