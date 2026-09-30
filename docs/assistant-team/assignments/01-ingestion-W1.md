# 01 资料建设｜W1 阶段任务单

日期：2026-09-23。任务 ID：`01a0c6f9-fb32-70f3-9440-b8c0d7df4ca3`。

## 准备状态与启动门槛

用户已明确允许在 W1 公共运行核心基础上继续，要求仍为严格串行。本任务单由总控准备，不等于已经调用 turn/start。

当前阻塞：01 通过官方 App Server 续接时返回 `already has an active writer`，实际模型仍为 `gpt-5.6-sol / high`。解除占用后，总控必须回读确认 `gpt-6-sol / high`，并确认没有其他运行中的子任务，才能下发执行。禁止删除锁、终止桌面进程、使用旧模型或私自新建替代任务。

总控已将公共核心 9 个 Java 源文件、2 个单元测试文件和 `docs/assistant-team/W1-CORE.md` 同步至本工作树。它们是只读依赖，不能被子任务修改。当前助手核心的 ToolResult.status 只有 ok / partial / error，旧 W0 报告中的 not_found / conflict 等映射为 errorCode 或领域数据，不另建运行状态枚举。

## 本阶段目标

接入真实业务结构的只读适配器，实现资料/任务查询、确定性恢复评估和稳定原文定位，为后续质量治理与图谱研究提供共同数据来源。只在隔离单元测试中调用这些实现；不连接本机或生产业务数据库，不调用项目真实模型，不开放新公开 API。

### 1. 领域工具

- 以现有 `AssistantTool` 实现工具；用 `IngestionAgentToolProvider.tools()` 返回 List<AssistantTool>，不新建公共 ToolProvider 接口。
- 工具名固定使用 ingestion 前缀：list_libraries、get_library、list_documents、get_document、get_document_unit、list_jobs、get_job、get_organization、assess_recovery、prepare_import。
- 只声明 READ_ONLY 或 DRAFT_ONLY，角色允许列表为 INGESTION。共同原文读取服务可供后续角色复用，暂不扩大工具角色权限。
- 每个工具有严格参数白名单、类型/数量上限、分页/截断说明，返回固定宿主 scope 内的数据；禁止任意 SQL、文件路径或 URL 参数。
- SELECTED 的库/文档/节点维度均必须执行，不能忽略不支持的维度后退回全局查询。节点限定暂不能安全解析归属时明确拒绝，不用“同库优先”替代过滤。
- 真实对象归属校验必须在查询/返回前完成。应对多库归属、文档/任务关联缺失和对象不存在；空范围、无结果、字段不可用不伪造成成功数据。
- 结果不能暴露 storagePath、密钥、堆栈或未脱敏的底层错误。只有从服务端可信元数据得到的存储路径才允许检查文件是否存在，参数不得直接决定读取路径。
- 不在这些工具中隐藏模型调用或业务写入。读取方法也要检查是否有隐式创建/修复副作用。

### 2. 恢复评估

- 只根据 Job / Document / 断点 / 候选 / 文件存在性等事实计算，不执行 process、retry、extract、review、commit、cancel 或元数据写操作。
- 覆盖 UPLOADED、活动任务、AWAITING_REVIEW、FAILED、CANCELLED、COMPLETED，以及缺少视觉配置/文件、已有候选、已有抽取断点的分支。
- 恢复建议完整披露后续可能调用模型、建库、标注、自动复审和入图；无法可靠决定复用断点/全量处理时写 server_decides_at_execution。
- 不把活动状态直接认定为后台仍存活，也不猜测旧任务可强制恢复。没有心跳/租约支持时保留限制。
- stateToken 可用类型化快照 hash 表示，但只用于发现陈旧，不能当事务锁或执行授权。
- Organization 的分配数量使用 assignedCandidateKeys 等准确语义，不冒充正式入库节点数。

### 3. 稳定原文定位

- 授权新增 `assistant/source` 子包，只读查询现有 document_units / document_chunks 等真实结构。
- 返回实际 documentId、unitId、unitIndex/type、chunkId、chunkIndex、contentHash、定位与有长度限制的原文；不能用 chunkIndex 冒充 chunkId。
- 定位结果须验证文档所属范围；重读时可校验期望 hash，不一致明确返回 stale/conflict，不替换成新的内容冒充原引用。
- 不改变已有 DocumentService DTO、Controller、数据库迁移或原文页面。优先通过新增只读仓储/适配器解决，供 02/03 后续复用。

### 4. 未执行草稿

- 恢复方案、导入准备与元数据建议均为未执行数据，不能生成执行回执。
- 若产出 ActionProposal，必须提供对应的 INGESTION ProposalValidator：校验动作白名单、参数、目标归属、范围和前置条件，且 executionAllowed 永远为 false。
- 文档/Job 版本不足时明确限制，不伪造版本；不调用人工核验写接口。

## 修改所有权

允许新增/修改：

- `backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/**`
- `backend-java/src/main/java/com/knowledgegraph/assistant/source/**`
- 上述两个子包对应的 `backend-java/src/test/java/**` 单元测试。
- `docs/assistant-team/reports/01-ingestion-W1.md`

禁止修改：公共 contract/runtime/tool 包及核心测试、既有领域服务/Controller、前端、配置与密钥、pom、数据库迁移、其他子工作树或主工作树。确有必要扩权时先在报告提出，不自行越界。不提交 Git、不合并、不部署、不再创建 Agent。

## 验证与阶段报告

- 单元测试使用 mock/fake 仓储；若编写 JDBC 适配器，也要测试 SQL 参数化、查询前范围限制、分页边界和返回映射，不声称 mock 已证明 MySQL 兼容性。
- 必测：空项目；各恢复状态；范围越界与不支持维度；稳定 chunkId / hash 陈旧；截断；错误脱敏；禁止写调用；草稿目标校验；工具接入现有 Runner 的隔离测试。
- 只用指定测试名执行 Maven，不运行全量 `test` 或 `*IntegrationTest`。当前公共核心测试命令为 `mvnw.cmd -Dtest=AssistantRuntimeTest,DecisionParserTest test`；新增单元测试必须逐个加入明确列表。
- 报告列出修改文件、实际工具能力、实际测试命令/数量、未测试项、仍未接入的网站能力、跨域契约和待总控决定问题。
- 完成指定阶段后自动汇报并结束本轮，等待总控下一条指令，不进入 02 或后续集成阶段。
