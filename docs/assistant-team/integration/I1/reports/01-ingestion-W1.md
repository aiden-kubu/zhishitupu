# 01 资料建设｜W1 阶段交付

日期：2026-09-24。范围：本工作树的 `assistant/ingestion`、`assistant/source`、对应隔离单元测试；未修改总控同步的公共核心或既有业务代码。桌面原任务接令，未使用旧任务单所述外部控制器。

## 修改文件与所有权

| 新增文件（均属 01 授权范围） | 用途 |
|---|---|
| `backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/IngestionReadRepository.java` | 无写方法的领域读取边界与字段记录（第 10–38 行） |
| `backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/JdbcIngestionReadRepository.java` | 参数化 SQL、范围前置、状态事实、可信存储路径存在性检查（第 18–205 行） |
| `backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/IngestionAgentToolProvider.java` | 十个角色限定工具、字段级参数 schema、参数白名单、返回脱敏/截断/实际范围（第 34–337 行） |
| `backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/IngestionRecoveryAdvisor.java` | 确定性只读恢复评估、快照 token 与规范影响说明（第 10–118 行） |
| `backend-java/src/main/java/com/knowledgegraph/assistant/ingestion/IngestionProposalValidator.java` | 未执行草稿的动作、参数、归属、状态校验（第 11–108 行） |
| `backend-java/src/main/java/com/knowledgegraph/assistant/source/SourceLocator.java`、`JdbcSourceLocator.java` | 可供后续领域复用的原文定位接口及 SQL 实现（分别第 9–17、10–76 行） |
| `backend-java/src/test/java/com/knowledgegraph/assistant/ingestion/IngestionAgentToolProviderTest.java`、`JdbcIngestionReadRepositoryTest.java` | fake 仓储/来源、mock JDBC 的隔离测试 |
| 本报告 | 阶段交付记录 |

## 已实现的实际能力与契约

- `IngestionAgentToolProvider.tools()` 返回固定十项 `ingestion.` 工具：`list_libraries`、`get_library`、`list_documents`、`get_document`、`get_document_unit`、`list_jobs`、`get_job`、`get_organization`、`assess_recovery`、`prepare_import`。全部仅允许 `INGESTION`、声明 `READ_ONLY`，无处理/重试/取消/入库写调用，也不调用模型；目前**未注册 Spring Bean、未接入网站/API**（`IngestionAgentToolProvider.java:32-153`）。
- 十项工具的 `argumentsSchemaJson` 逐一声明真实字段、类型、取值范围、必填 ID、状态枚举和禁止额外字段；精确原文定位的 chunk ID/hash 互相依赖。服务端仍独立执行白名单校验；正整数 ID、页码 1–1000、每页 1–20、单元序号 1–1,000,000，不接受 SQL、路径、URL（`IngestionAgentToolProvider.java:150-250`）。`SELECTED` 库和文档维度在列表 SQL 的 `LIMIT` 前同时过滤，节点维度因缺安全归属解析而明确拒绝；单对象工具检查真实文档/库关联，多库归属由主库与 topic assignment 合并判断（`JdbcIngestionReadRepository.java:36-59,78-87,128-137,166-185`）。不存在、越界、陈旧等通过 `ToolResult.errorCode` 表达，不新增核心状态。
- 列表返回 `items/total/page/pageSize/hasMore/completeCoverage/truncated`；`hasMore=false` 仅表示无下一页，只有第一页覆盖 `total` 才表示 `completeCoverage=true`，其余为 `PARTIAL`，包括第 2 页正好读到最后一条（`IngestionAgentToolProvider.java:314-321`）。原文单元每次最多 12 段、每段最多 1200 字，指定段最多 2400 字，证据摘录最多 2048 字，均标明截断。`get_job` 只返回 `errorMessageAvailable`、`sourceWarningAvailable`，不暴露原始报错；文档只返回 `sourceFilePresent`，不暴露 `storagePath`。在所选库之外的文档主库 ID 不输出，改标注 `primaryLibraryOutsideSelectedScope=true`；`libraryIds` 只列可见归属（`IngestionAgentToolProvider.java:287-302`）。库统计在文档限定范围下省略，以免把全库数量误报为所选文档数量；组织结果的 `assignedCandidateKeys` 不冒充正式入库节点数。
- 恢复评估覆盖 `UPLOADED`、活动状态、`AWAITING_REVIEW`、`FAILED`、`CANCELLED`、`COMPLETED`，区分候选可复审、仅关系候选无法复审、已有 chunk 且无 OCR 缺口可续抽取、缺文件需重解析、缺视觉档案等。输出 `classification/suggestedAction/resumeScope`、后续可能模型调用/建库/标签/自动复审/入图、前置条件、限制、`executionAllowed=false`；不实际恢复。`stateToken` 哈希当前任务、文档、视觉配置、chunk 与 OCR 缺口观测，只用于发现陈旧，不是事务锁或执行许可。活动状态不推断后台存活，取消被明确视为合作式且不能回滚已提交写入（`IngestionRecoveryAdvisor.java:20-104`）。
- `SourceLocator` 按 `documentId + unitIndex` 或真实 `chunkId` 查询；返回真实 `unitId/chunkId/chunkIndex/contentHash/offset/content`。SQL 每次都附带文档归属和宿主 scope 条件；精确重读必须提交 `expectedContentHash`，不一致返回 `STALE_SOURCE`，不会把新文本当旧证据（`JdbcSourceLocator.java:14-70`；`IngestionAgentToolProvider.java:60-92`）。
- `IngestionProposalValidator` 只接受白名单导航、处理恢复或元数据草稿，校验角色、单目标、参数、对象归属、当前快照及刷新前置条件。恢复评估输出服务端生成的 `requiredImpact`；处理草稿的 `impact` 必须与该规范说明**完全相等**，不再用关键词包含判断，以拒绝“不会自动复审，也不会入库”等反向表述。元数据草稿必须刷新文档，若附版本则比较当前文档/元数据时间。所有 `ActionProposal.executionAllowed` 恒为 false，无执行回执（`IngestionRecoveryAdvisor.java:94-106`；`IngestionProposalValidator.java:58-80`）。

## 总控复核后的四项补修

| 问题 | 修正及针对性回归测试 |
|---|---|
| 模型可见工具参数 schema 缺字段 | 十项 schema 与实际字段一致；`everyToolPublishesItsRealArgumentSchema` 核对每项字段、必填、边界和 chunk/hash 依赖（`IngestionAgentToolProviderTest.java:47`）。 |
| 最后一页被误认为完整覆盖 | 将 `hasMore` 与 `completeCoverage` 分开，后一页仍为 `PARTIAL`；`lastPageIsNotMisreportedAsFullCoverage` 重现 2 条记录只读第 2 页（`IngestionAgentToolProviderTest.java:123`）。 |
| 多库文档泄露范围外主库 ID | 范围外 `libraryId` 不输出；`multiLibraryDocumentDoesNotRevealPrimaryLibraryOutsideSelectedScope` 同时检查详情和列表（`IngestionAgentToolProviderTest.java:94`）。 |
| 草稿反向影响说明绕过 | 要求与服务端 `requiredImpact` 精确相等；`proposalImpactRejectsNegationEvenWhenItContainsTheRequiredKeywords` 直接验证反例被拒（`IngestionAgentToolProviderTest.java:261`）。 |

## 隔离验证

在 `D:/知识图谱-worktrees/assistant-01-ingestion/backend-java` 实际执行：

```powershell
.\mvnw.cmd '-Dtest=AssistantRuntimeTest,DecisionParserTest,IngestionAgentToolProviderTest,JdbcIngestionReadRepositoryTest' test
```

补修后结果：**59 通过、0 失败、0 错误、0 跳过，BUILD SUCCESS**；分别为公共核心 `AssistantRuntimeTest` 28、`DecisionParserTest` 15，本阶段 `IngestionAgentToolProviderTest` 12、`JdbcIngestionReadRepositoryTest` 4。补修的四项各增加一个针对性测试；其余覆盖空项目、范围和参数、分页、真实 chunk ID/hash 陈旧与截断、恢复状态/副作用、报错脱敏、草稿归属校验、接入 Runner 的 fake 隔离运行、SQL 参数绑定/范围前置/来源映射。未运行全量测试或任何 IntegrationTest；未连接真实业务数据库、调用项目真实模型。

## 未验证事项、跨域依赖与阻塞

- JDBC 测试只验证 SQL 形态、参数绑定及 mock 映射，**未证明真实 MySQL 兼容性、索引性能、并发快照一致性或文件系统权限行为**。查询存在多语句读之间的归属/版本变化窗口；生产接入前需在总控负责的集成层选择一致性与查询超时策略。文档版本是 `documents` 与 `document_metadata` 更新时间的较新者，时间精度及标签表变化仍不足以构成强版本；Job 无持久化 `updated_at`，`stateToken` 无法检测“候选内容变更但数量不变”等同值变化。执行端必须再次核验并原子化处理，不能拿建议或 token 直接授权写入。
- `visionConfigured` 只表示数据库存在已启用、标记视觉能力且密钥非空的档案，不证明实际模型可用。恢复复用的最终判断和幂等副作用仍由既有 `ProcessingService`/复审/抽取服务在执行时决定；本阶段没有提供执行器。也未验证原文 hash 与内容重新计算的一致性，依赖既有分段写入规则。
- 总控需决定何时注册这些只读适配器与 `ProposalValidator`、如何从服务端鉴权生成固定宿主 scope（模型不得扩大）、统一模型/工具预算与查询超时、如何向网站展示 `PARTIAL`/限制/未执行草稿，以及何时安排隔离 MySQL 的只读兼容和并发验收。新 schema 的具体生产模型工具协议兼容性也尚未验证。现阶段生产注册保持关闭，不应将十项工具宣称为已上线端点。
- 02 质量治理可复用 `SourceLocator` 的真实 chunk ID/hash 与范围约束；03 图谱研究可消费资料/任务证据和多库归属，但必须保留自身的节点归属校验；04/05 不应把 `assignedCandidateKeys` 当入库节点或把恢复建议当完成回执。公共核心 `contract/runtime/tool` 归总控所有，本阶段未触碰。
- **无阻止本阶段隔离交付的扩权事项。** 生产接入和真实数据库验收尚未授权，留待总控另行下发；未进入 W2。
