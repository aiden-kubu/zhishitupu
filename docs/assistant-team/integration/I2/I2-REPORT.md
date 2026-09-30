# I2 中间报告：真实 MySQL 验证与性能阻断

日期：2026-09-27。状态：**I2进行中，部分验证已完成，因大规模质量洞察查询超时停止推进；不是I2阶段交付完成，也不是负责人验收通过。** 未进入I3。

## 1. 项目负责人摘要

已通过负责人单项批准的英文目录联接和具名本地共享内存管理连接，启动并核验原MySQL实例。验收库、账号分离、固定样本、首批真实JDBC验证已实际执行，不再只是mock。

| 已执行范围 | 实际结果 | 应如何理解 |
|---|---|---|
| I1原19类回归 | 154/154通过，0失败/错误/跳过 | 冻结领域行为仍稳定，仍属于原隔离测试 |
| I2基础真实JDBC与权限 | 35/35通过（查询22、权限13） | 使用真实MySQL、真实JDBC和reader账号 |
| 独立数据库变体 | 7个场景通过 | 同一个JUnit方法分别运行7次，不冒充7个不同方法 |
| 超时与小样本性能 | 3/3通过 | 服务端中断、JDBC中断、小样本单线程及4并发读取 |
| 大规模查询 | 最终复测2项：1通过、1错误 | 更新样本统计后仍触发3024；不能宣称大规模性能通过 |

I2按场景取最终结果为47个执行项，46通过、1错误；另保留大样本首次2项结果（1通过、1错误），含这次复测的实际执行累计49次，47通过、2错误，两个错误是同一性能问题。按JUnit classname+name去重为41项，因为7个变体复用了一个方法且大样本复测不增加独立方法。原154项回归单列，不把失败项或重复执行合并成“全部通过”的总数。

目前仍没有新增网站入口可点击。生产宿主、真实模型、API、前端、部署与W2均未实施。

## 2. 原实例身份、环境和账号

- 原目录：`D:/知识图谱-i2-env`。英文联接`D:/kg-i2-env`已解析核实指向它。
- 数据目录仍为原`mysql-data`，未移动、复制、删除或重新初始化。运行时SQL确认UUID：`8c6cfcb3-ba49-11f1-bf64-005056c00001`。
- MySQL Community **8.4.11**，仅监听`127.0.0.1:33078`，mysqlx关闭。未连接本机原有MySQL8.0或其他库。
- 经批准启用本地MEMORY管理通道`KG_I2_33078_8c6cfcb3`；管理员为`root@localhost`，没有新增网络端口，没有启用skip-grant-tables。
- 独立数据库：`kg_assistant_i2_20260927_r01`。原V1～V11文本未修改，全部执行成功，形成33张现有结构表；另有1张I2专用权限canary表。
- `kg_i2_loader@127.0.0.1`仅用于该验收库的迁移、装载、故障注入/恢复。密码每次装载阶段随机轮换、不持久保存；reader验证前锁定，当前仍锁定。
- `kg_i2_reader@127.0.0.1`仅通过TCP连接。21张业务表SELECT、2张配置表的必要列SELECT，加全局USAGE；无默认角色、无库级通配SELECT、无GRANT OPTION、无业务写/DDL权限。SHOW GRANTS共24条。
- 管理员和reader口令仅以Windows DPAPI加密保存在项目外，文件ACL为当前用户专有；没有放进源码、命令行参数或公开报告。测试子进程只接收reader口令，不接收管理员/loader口令。

21表：knowledge_libraries、library_aliases、knowledge_nodes、node_aliases、library_nodes、knowledge_edges、node_evidence、edge_evidence、documents、document_metadata、document_tags、document_topic_assignments、document_units、document_chunks、ingestion_jobs、entity_candidates、relation_candidates、extraction_batch_results、learning_paths、learning_path_steps、learning_assessment_attempts。

配置列：llm_profiles(enabled,vision,encrypted_api_key)，system_settings(setting_key,encrypted_value,is_secret)。数据均为合成或空配置，没有真实模型密钥。

证据：[SQL身份](D:/知识图谱/docs/assistant-team/integration/I2/I2-MANAGEMENT-IDENTITY.json)、[实际表列/外键/授权](D:/知识图谱/docs/assistant-team/integration/I2/I2-ENVIRONMENT.json)。

引导脚本曾在完成建库后的重复凭据ACL设置中报错；已有ACL核查为当前用户专有，移除重复收尾设置后，独立Inspect成功连接并正常停止。另一处快照脚本标识符校验最初未允许sha256中的数字，已仅修正本轮测试脚本。两者不是业务查询失败，不计作产品测试通过数，也没有因此修改冻结文件。

## 3. 固定样本映射与已执行真实查询

I1 JSON和A/B正文保持原样。按真实必填列、外键顺序装入6份资料、4个单元、5个chunk、3个处理任务、3个候选、5个节点、3条有向边、路径1000、阶段8101/8102、旧记录9102/9101。原V3自动生成的验收库内演示库1/节点1、2/边1已按精确ID移除，可由保留的V3脚本在新验收库重建；未删除用户现有数据库中的任何数据。全程未关闭外键检查。

映射不是悄悄改I1：7501有实体候选，真实SQL计算reviewable=true；7502已完成且有chunk、无候选，extractable=true。候选APPROVED保留为原始非标准状态，ACCEPTED另作独立变体后恢复。actualStepPathId由JOIN计算，不虚构列。原文全文文件hash与chunk正文hash分别记录；缺文件用明确的合成历史上传元数据。归档/范围外衍生文件写在I2环境内，未覆盖I1原文。

证据：[映射](D:/知识图谱/docs/assistant-team/integration/I2/I2-FIXTURE-MAPPING.json)、[实际装载SQL](D:/知识图谱/docs/assistant-team/integration/I2/I2-FIXTURE.sql)、[装载结果](D:/知识图谱/docs/assistant-team/integration/I2/I2-FIXTURE-LOAD.json)。

六个真实JDBC适配器39个公开查询方法均至少执行正常路径：

| 领域 | 已执行方法 |
|---|---|
| 01（12） | libraries、library、documents、document、documentLibraries、jobs、job、hasChunks、hasOcrGaps、destinations、maxUploadSizeMb、visionConfigured |
| source（2） | unit、chunk |
| 02（10） | job、candidates、candidate、entityByTempKey、chunkDocumentId、chunkHash、unitMeta、nodeMatch、edgeMatch、insight |
| 03（8） | searchNodes、node、incidentEdges、outgoingEdges、matchingDocuments、candidates、sourceMeta、libraryExistsInScope |
| 04（6） | currentPath、graph、node、edge、libraryVisible、nodeSourceLinked |
| 05（1） | legacyAttempts |

验证包含库/资料/节点范围交集、范围先于分页、同名越界节点、方向边、归档来源、分页覆盖、UTF-8正文SHA-256、错误归属、真实Long大ID、多库主库隐藏、空结果、旧成绩不升级正式结果。部分领域工具也用真实仓储执行，但**没有验证所有33项工具的生产宿主全链路**。可信宿主在不需要宿主的读取场景中显式返回不可用；没有伪称生产适配完成。

权限负向测试的合法零行INSERT/UPDATE/DELETE必须收到MySQL权限错误，成功但影响0行也算失败；CREATE/ALTER/DROP/TRUNCATE/TEMPORARY TABLE只针对canary/专用探针名。实际均被1142或1044拒绝，越权表/列SELECT也被拒绝，不以语法错误冒充鉴权成功。

## 4. 变体、预算及当前失败

7个已执行场景：[记录](D:/知识图谱/docs/assistant-team/integration/I2/I2-VARIANTS.json)。HASH_CHANGED、ARCHIVE_A、LONG_A、MULTI_CURRENT、INACTIVE_CURRENT、NO_PATH、ACCEPTED_STATE均通过。每次单独注入，锁定loader，运行reader，最后恢复基线；恢复后的34表数据和DDL hash与原基础样本一致。

LONG_A目前仅验证数据库正文、hash、单元和偏移扩长后A/B配额及partial；**没有重建长版原始A文件并切换上传元数据**，因此不是I1 V_LONG_A全物理文件链路验收。NO_PATH验证服务端无路径，不等于浏览器旧缓存行为已经验收。

测试专用JDBC设置：connectTimeout=3000ms、socketTimeout=4000ms、JdbcTemplate queryTimeout=2s、连接session max_execution_time=1500ms。不是生产配置接线。

- 小样本20次组合读取：单线程p95=184ms，4并发p95=125ms。
- JDBC SLEEP超时约1014ms，捕获超时异常，同一连接随后SELECT 1成功。
- 服务端超时探针加200ms hint，实际约1227ms返回3024，同一连接可继续使用。hint不是严格200ms墙钟保证，不能把它报告为200ms完成。
- 验证结束无仍在执行的reader查询。

大规模衍生库7901：1万节点、3万关系、2千资料、2万chunk，仅用于数据库性能；没有生成真实原始上传文件，不代表解析/文件完整性验收。检索窗口/范围项通过；洞察查询第一轮即在`JdbcQualityReadRepository.insight`的节点度数排名SQL处超时，MySQL错误3024（SQLState HY000），首次测试项耗时约1.782s。随后排除统计陈旧影响，按原预算复测仍失败，测试项约1.723s。20次大样本p95采样和4并发采样尚未完成，不能报告任何大样本p95。

定位：[冻结查询源码](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/assistant/quality/JdbcQualityReadRepository.java:232)。该SQL逐节点做带source/target OR条件的相关COUNT，再排序取前N。只读EXPLAIN最初仍估算节点20行，因此使用loader在验收库内对14张相关表执行ANALYZE TABLE更新统计，再锁定loader复测，未改超时、样本规模、索引或SQL。更新后计划估算节点8463行（实际10005）、边27523行（实际30003），仍出现`DEPENDENT SUBQUERY`下边表`ALL`扫描，以及外层`Using temporary; Using filesort`。复测同样3024，说明问题不只是新装载后的旧统计；没有执行EXPLAIN ANALYZE或任何候选优化SQL。

证据：[原计划](D:/知识图谱/docs/assistant-team/integration/I2/I2-SCALE-EXPLAIN.json)、[统计维护](D:/知识图谱/docs/assistant-team/integration/I2/I2-SCALE-STATISTICS.json)、[更新后的计划](D:/知识图谱/docs/assistant-team/integration/I2/I2-SCALE-EXPLAIN-ANALYZED.json)、[复测结果](D:/知识图谱/docs/assistant-team/integration/I2/I2-SCALE_ANALYZED-I2ScaleQueryTest.json)。统计维护属于验收样本准备动作，在reader窗口外执行，不能宣称全阶段无技术写入。

失败证据：[测试明细](D:/知识图谱/docs/assistant-team/integration/I2/I2-SCALE-I2ScaleQueryTest.json)、[构建日志](D:/知识图谱/docs/assistant-team/integration/I2/I2-SCALE-MAVEN.log)。没有提高超时、改索引、迁移、冻结SQL或将失败改为跳过。

## 5. 零业务写入证据与冻结保护

每个reader静态窗口前后，管理员对验收库全部34表获取精确COUNT、所有行所有列NULL/HEX规范化表示的SHA-256、SHOW CREATE TABLE的SHA-256，以及binlog文件/位置；reader运行时loader锁定，同时记录MySQL general log。

基础1个、变体7个、预算1个、大样本初测/复测2个，共11个窗口全部数据/DDL hash相等、binlog位置不变。大样本查询失败也未写业务数据。[大样本首次零写入](D:/知识图谱/docs/assistant-team/integration/I2/I2-SCALE-ZERO-WRITE.json)、[复测零写入](D:/知识图谱/docs/assistant-team/integration/I2/I2-SCALE_ANALYZED-ZERO-WRITE.json)。这些证据针对静态测试窗口，不把迁移、样本装载、故障注入、恢复、统计维护以及server内部日志写入叫作“全阶段零写入”。

跨大样本两次窗口（含重启和统计维护），34表行数与数据hash均相同；node_evidence的SHOW CREATE TABLE中自增展示值由16389变为10006，替换该数字即可精确重现前一DDL hash，列/索引文本没有变化。此差异单列在[维护审计](D:/知识图谱/docs/assistant-team/integration/I2/I2-SCALE-MAINTENANCE-REVIEW.json)，不隐去差异，也不归因于某一个未经单独验证的操作。

总控离线复核：499个操作前既有文件内容一致；I1的62文件、98份重复依赖、105条冻结依赖均一致；主分支/HEAD未变；新增文件仅在I2文档/脚本/证据和assistant/i2测试目录。原154项已真实重跑并逐case去重，不是沿用旧结果。[复核清单](D:/知识图谱/docs/assistant-team/integration/I2/I2-CHECKPOINT-VERIFICATION.json)。

当前隔离MySQL已正常停止，33078无监听。目录联接、原数据目录、固定/性能样本及日志全部保留；没有连接或停止已有MySQL服务。凭据未泄露到公开I2证据。

## 6. 尚未完成及需要负责人决定的边界

以下不打勾、不宣称完成：

1. 大规模质量洞察性能与后续4并发；当前是已发现的阻断项。
2. 真实loader与reader交错的并发数据变化、读取中途资料/路径变更。4并发只读不等于并发写入一致性；当前快照指纹不等于事务原子快照。
3. 全物理文件版V_LONG_A、实际步骤归属异常的真实数据库补充场景、装载失败恢复演练、重置/销毁演练。原W1同类fake/mock用例通过不能替代这些项目。
4. 原文正文独立hash校验目前在测试中核对了基线；冻结SourceLocator直接返回存储hash，不能宣称运行时每次重算了正文SHA-256。正文改了但存储hash未同步的损坏场景尚未验证。
5. 伪宿主、B失败、partial已有154回归覆盖仍属隔离验证；可信生产宿主待I3。浏览器缓存/页面拒写待网站阶段，当前没有浏览器验收。
6. 全量索引/执行计划、连接失败/整工具10秒预算的真实宿主接线没有完成。只对本次触发的语句/JDBC超时做了实测。

下一步建议：基于已有EXPLAIN先评审最小补修方案，优先研究“在范围内对边的端点一次聚合度数，再与节点关联”，避免对每个节点重新扫描边；自环须保持只计一次，范围、有效边、有向图及统计口径不变。候选改动范围只考虑02查询实现和针对性测试；是否需要索引须另证，不先改迁移。任何冻结实现、契约或迁移修改均需另行批准，不以I2现有授权自动解冻I1。也不建议为通过验收直接提高超时或降低样本规模。

解决并重新验证性能项后，仍须补齐上述I2剩余项，由总控复核并由负责人最终验收，才可能讨论I3。

## 7. 负责人现在可以怎样核对

打开第1节结果表、失败日志和第6节未完成清单，确认本轮实际进度；打开SQL身份和权限记录核对确为指定隔离实例；打开零写入和冻结复核证据查看布尔结果。当前不需要也不能到网站验收新功能。

离线证据复核可运行`Verify-I2Checkpoint.ps1`：不连接数据库、不编译、不改源码，只生成本轮复核JSON。其他Invoke脚本会启动指定隔离实例，**当前停止推进后不要未经新指令自行运行或把Bootstrap作为重置命令**；Bootstrap遇已有凭据会拒绝。LoadFixture仅允许初始V3演示状态，Scale遇已存在7901拒绝，不自动覆盖样本。销毁/重建实例不在本轮已执行动作中。
