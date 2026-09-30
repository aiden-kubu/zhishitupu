# 原 06｜I2-R4-D2 条件式 EXPLAIN：正式执行单

日期：2026-09-29。给原 06「集成测试」，由负责人手动转发。负责人已批准 [D2 方案](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-D2-DIAGNOSTIC-PROPOSAL.md)，原 03 已完成模板固化并停止；[总控增量审核](D:/知识图谱/docs/assistant-team/integration/I2/I2-R4-D2-TEMPLATE-REVIEW.md)未发现定义阻断。

**这是获批 D2 的准确实施清单和正式执行指令，不是新接令检查。** 请实施独立诊断驱动、自核后执行本批条件式 EXPLAIN，再停止。无需重复入职/全文审查或索取同范围授权；实际执行前的文件前像、环境身份、权限、数据和证据门禁必须执行。发现新冲突/未知修改/范围外需要即停报。

## 1. 串行角色与模型

- 仅原 06 执行本批，原 03 待命；不启动其他任务，不新建桌面任务、工作树、临时 Agent 或 App Server。
- 显式工作目录 D:/知识图谱-worktrees/assistant-06-integration-test；预期分支 codex/assistant-06-integration-test，HEAD 86d1e1fcff6b7b492faec3d499837a42246df320。会话默认目录为主项目的既有情况不改变此约束。
- 主项目 Maven 实際工作目录限定 D:/知识图谱/backend-java。06 工作树不是完整可编译主项目副本，不整体复制基线。
- 原 03 是诊断语义与模板作者；06 只负责确定性装载、绑定、执行限制和证据，不自行选择要删除的业务条件、重写 SQL 或提出生产补丁。
- 模型要求 GPT-6 Sol / High。负责人已于 2026-09-29 单独确认 06 并确认 01～06，无须重复索取。工具无法独立回读时另记“实际模型未核实；实际推理强度未核实”，不把人工确认改说为工具确认。

## 2. 首先复用什么，检查哪些增量

完整阅读新的 03 模板、其 D2 报告追加、本执行单和总控模板审核。旧规则、已审核 R4 运行器/Java、输出收集结论和入职证据复用未变结论；只阅读本批改动依赖的部分，不全仓扫描或重算全部历史材料。

### 2.1 固定输入

- [03 模板](D:/知识图谱-worktrees/assistant-03-research/docs/assistant-team/reports/I2-R4/D2-DIAGNOSTIC-TEMPLATES.json)：39285 字节，raw SHA-256 `531988319b3aed79a8bcd3f5c9088f4bc7f11273573553bb8a183af309c68d01`。
- [03 报告](D:/知识图谱-worktrees/assistant-03-research/docs/assistant-team/reports/03-research-I2-R4.md)：13338 字节，raw SHA-256 `38c7594ef9edae9a561e53524ba47c9675b3c847f74d0a9016f8c9581ec59b5c`；较上一批只是追加，旧 9411 字节 hash 未变。不能因此改写上一批记录里的旧 hash。
- 前批根目录：D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-EXPLAIN/evidence/R4Explain-20260929T164517990。
- 前批 INDEX raw hash `cb57d0fa2d7c2e0c39ac758119d31e958ac06e2920b99f3cc2362f65023cd5d2`；E02.json raw hash `72edc590f96d98642e530d8f6aadcd8614b29014f7497817fa7a2e4cdbfde609`，12074 字节。
- 前批窗口末态 I2-R4_WINDOW-AFTER.json raw hash `5e5015638e794dbb9b42eaf534fee1c6c22f9639447238c32bf4d608a47992a4`；ZERO-WRITE.json raw hash `ff50122a2e5aebbb3fab6e0db07f63b0ce44cf63845e7b4b52c3ed2c3b1404ac`。
- 前批记录的累计 general log raw hash `64faa0418dabab00cf1b84c7ba8035ac5446bfeb97bd93e797315a7b830557a3`。本批开始前先核对当前原件；不把 R3 旧截面当作当前原件，不覆盖旧日志/索引。

### 2.2 直接实现和复用代码

- 主项目/原 03 JdbcResearchReadRepository.java 规范化 hash 均为 `b922ec24fc397fd772524524c34c6837a3f044bff4bd18efcdd23c37ae67612f`；既有 JdbcResearchReadRepositoryTest.java 均为 `1b7c80d568cd0e75b982427c10c404b90f608ea8dee83daf4b92843a131277ce`。UTF-8 解码、CRLF 转 LF、保留结尾换行、不 trim。
- 主项目 assistant/i2/I2MySqlSupport.java 规范化 hash `eb518e6c7b884595bd868764f3686e5f002bea07266fcaa9851f4df31200f70c`，只读。
- 既有 I2ResearchEdgeExplainTest.java raw hash `5c9aebaea80b6459e273a6868dcf32d42359f1a3678001f3697d5bc8dc7429fd`；既有 Invoke-R4EdgeExplain.ps1 raw hash `497428ae0cb9c58a563c4eb4197cf219f1bda7f1fc8650aa56430bc86950743c`；既有 R4-EXPLAIN-OVERLAY.json raw hash `f50ee538a118fdb5e70de257aefddfe154b0c2e84f63e8de86b0bc7d4cfebeaa`。全部只读，不修改旧运行包。
- 若复用已有 Invoke-I2Environment.ps1 / Invoke-I2BaseVerification.ps1 中的原身份、凭据解封与快照函数，保持文件只读，执行前按上批 overlay 的原版本 hash 校验后仅提取必需函数，不执行其顶层。D2 新 overlay 须列出全部实际执行依赖；不能只核验新 runner 而忽略旧 helper。
- 旧 R4 入口具有四项固定循环、旧报告和 R3 日志前像，不能直接作为 D2 入口。新版本以明确新增量承接当前状态，不修改历史 expected hash、跳过版本校验或把不一致直接接受成新基线。

## 3. 准确允许文件与副作用

只允许新增以下实现/材料；若路径已存在且来源不明则停止，不覆盖：

1. 主项目集成诊断测试：D:/知识图谱/backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2ResearchEdgeDiagnosticExplainTest.java。
2. 06 新运行器：D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/integration/I2/R4-D2-EXPLAIN/Invoke-R4D2Explain.ps1。
3. 同目录新增 R4-D2-EXPLAIN-OVERLAY.json；封存输入、直接只读依赖、代码版本、预算、写入清单及前批状态关系。
4. 如需要独立检查条件门禁，可在同目录新增 Validate-D2ProbeGates.ps1；只做纯内存/假结果分支验证，不启动 Maven、MySQL、网络或真实 runner 顶层。
5. 同目录 evidence/<唯一 runId>/：本批静态自核/编译/真实执行的分离记录、输入模板精确副本、代码执行版本、SQL/绑定/错误/计划、分支判定、日志、索引及 hash。保存原始失败和各次尝试；不能覆盖旧结果或把重跑算新增覆盖。
6. 新报告：D:/知识图谱-worktrees/assistant-06-integration-test/docs/assistant-team/reports/06-integration-I2-R4-D2-explain.md。不改旧 R4 报告。
7. 本批原件受限保全：D:/知识图谱-i2-env/restricted-evidence/I2-R4-D2/<唯一 runId>/。仅保全可能被编译/启动/追加影响的旧 XML 和当前累计日志截面，限制新增目录权限，不改父目录或 mysql-data 权限。

此外允许：在既定主项目 backend-java 使用已有 Maven wrapper 和离线依赖，产生必要 target 编译输出及带唯一 Surefire 后缀的新 XML；按既有机制正常启停同一隔离实例和收集其运行日志。不得运行 clean、安装新依赖、改 pom/生产配置或全项目扫测。

以上测试类只负责诊断集成，不能修改领域仓储/测试、生产 SQL、SourceLocator、其他领域、公共核心或旧助手脚本。模板是只读原 03 交付；有错误应停报，不由 06 改成自己认为正确的 SQL。任何额外文件/权限/配置需求先报告。

## 4. 诊断驱动的必需门禁

1. 固定装载已审核 JSON 与封存 E02，先核对 raw hash 和各 SQL 字符串 hash。只执行原 03 的完整 namedExplainSql；不要在驱动中重新猜测/生成删除条件。
2. JSON 数字不得因默认反序列化类型变成任意 Integer：依声明构造 Long 的 nodeId/libraryIds/documentIds，maxRows 为 Integer；集合按模板数组次序，不能换无序 Set。P1/P2/P3 预期绑定数分别 22/16/10。
3. 在实际 JDBC 预处理/执行边界记录并**在 executeQuery 前比较**完整问号 SQL、每个参数位置/类型/值、statement 超时与同连接 session 预算。发生不符不发送目标 EXPLAIN，留证并停批次；不得仅在查询执行后才核对。
4. 目标语句只允许已核定探针的 EXPLAIN FORMAT=JSON；禁止目标业务 SELECT、EXPLAIN ANALYZE、额外 E02 基线重跑、附加语句、hint 或优化器设置。管理身份/权限/规模/快照所必需的既有只读检查另列，不冒充探针，也不借其调用其他领域工具。
5. 原 reader 是唯一探针账号，测试子进程不接收管理/loader 凭据。仅用原 DPAPI 流程，不在命令行、日志、XML 可见属性或公开报告中泄露凭据；原 XML 按受限材料管理。
6. 一个 probe 最多一个实际 executeQuery，唯一轮次必须防止中断后重用同一路径重跑。记录开始/已尝试/完成状态；不能确认是否已发 SQL 时按不确定执行停止，不重试补数。
7. 完整结构化结果应有原始 SQLState/vendorCode/异常链、executeCount、实际绑定、耗时、计划是否取得、输出完整性。区分无计划、解析失败与有效 JSON 计划；不是只凭进程退出码或错误文字含“3024”放行后续探针。
8. 复用上一批已审核的有界 stdout/stderr 收集、原始错误与收尾错误分离、每流缺失/取消/截断记录。进程结束后最多五秒收集，子进程运行上限不增加；输出或落盘不完整则停批次，不能报告完整成功。

允许在新清单内进行纯静态/纯内存和精确离线编译自核，保留尝试；这些不触发真实 SQL、不算数据库验证。至少核对：P1 成功停止、P1 同类超时放行、P1 其他错误停止、P2 同类超时继续 P3、P2 其他错误停止、缺失/畸形证据停止。不要为了检查门禁先把真实探针执行一遍。

若在首次真实取证前遇到仅属于新诊断驱动的实现错误，可在本清单内修正、自核并保留变更轮次；不得改模板/领域/旧依赖。已有目标 EXPLAIN 执行后不得改驱动自动重跑，先封存本批并汇报。

## 5. 唯一允许的真实分支

P1/P2/P3 均基于 E02 的 SELECTED 库 [7101]、资料 [7201,7202]、nodeIds 空、outgoing 节点 7701、业务 limit 10 / maxRows 11。

| 顺序 | 条件 | 动作 |
| --- | --- | --- |
| 1 | 全部安全门禁通过 | 执行 P1 一次：完整 WHERE 保留，只替换 has_source 投影为 0 |
| 2a | P1 取得有效 JSON 计划且证据完整 | P2/P3 标 conditionally_not_executed，均 executeCount=0，直接收尾 |
| 2b | P1 确认只发生目标 MySQL HY000/3024，且其他门禁/证据无异常 | 分别执行 P2、P3 各一次；两项独立基于 P1 |
| 3 | P2 成功或仅发生同类 HY000/3024，其他条件完整 | 继续已获准的独立 P3 |
| 停止 | P1/P2/P3 任何其他异常，身份/权限/前像/数据/输出等异常 | 不再发后续探针，标明 blocked/not_executed 原因并安全收尾 |

总计最多三项，各一次，串行，不自动重试。P1 得到计划不能为凑齐三项再执行 P2/P3；P1 仅“无计划”也不能泛化成后续许可。探针放宽条件不构成生产修法，不返回或消费业务结果。

## 6. 原实例、原预算和零写入

- MySQL 8.4.11；UUID 8c6cfcb3-ba49-11f1-bf64-005056c00001；127.0.0.1:33078；数据库 kg_assistant_i2_20260927_r01。
- 原 D:/知识图谱-i2-env、D:/kg-i2-env 联接、原 mysql-data；核对真实目标、进程及端口归属、datadir。只正常管理本批启动且身份匹配的实例，不接管未知占用，不移/删/复制数据目录，不重建实例。
- 原 7901：10000 节点、30000 边、2000 资料、20000 chunk；同时核对前批 R4 末态全部 34 表数据/DDL，不仅检查规模总数。未知漂移停报，不自行修数据、自增值或修改快照预期。
- connect=3000ms、socket=4000ms、JDBC statement=2s、同连接 session max_execution_time=1500ms。SQL 预算不因日志收集、进程预算变化而扩大。
- 原 reader 23 项 SELECT 表/列授权、无新增角色；loader 始终锁定。本批不装载、不注入变体、不重置、不改索引/迁移/统计信息/优化器/权限，不执行 ANALYZE TABLE 或写权限探针。
- 静态 reader 窗口前后：34 表数据/DDL、同次启动的 binlog 文件/位置、reader 授权和 loader 锁定、活跃 reader 查询。既有已获准的身份/权限/规模/快照只读核验可执行，与探针分开记账。
- 开启/关闭既有 general log、日志追加、必要连接 session 预算设置和实例正常启停属于原诊断基础流程；不得据此声称“完全无任何技术写入”或修改持久配置。

## 7. 证据保全、版本承接与收尾

在任何获批编译、启动或日志追加前：

1. 按前批 INDEX 保全四份 R4 新 XML（E01～E04），并保全当前累计 general log 截面；核对实际数量、每份大小/hash 和受限目录权限。正常最小保全数为 5，不硬编码成上一批 32 份。若还有本次操作会触及的既有证据，应列明并保全，不能遗漏或将未知修改接受为基线。
2. 已保全的 31 份 R3 XML 与其旧日志截面原样保留，可复用已完成核对，不删除、不覆盖、不再次披露原件。
3. D2 overlay 区分“旧版本参考”“已批准的报告追加/新文档”“本批新增执行文件”“当前环境截面”。旧封包/报告/hash 均保持；新 runner 实际依赖的所有文件须在执行前锁定。
4. 使用独立 runId、新脚本路径、新类名和每项独立 Surefire 后缀，不运行旧 R4/R3 顶层。overlay 和执行版本在真实查询开始前封存；运行后再次核对，不能在执行后重写 expected hash 掩盖差异。
5. 前批 zero-write 中的日志 hash 仅是已知前像，执行前实际原件如不同则停报。日志长度由通过 hash 校验的当前文件取得并记下；不猜长度或把旧 R3 日志边界硬套本批。

无论取得计划还是超时，都须保留完整结果、未执行原因、stdout/stderr/落盘状态、原始错误和独立收尾错误。前批原件受限，新公开证据脱敏。

结束前核对无残留 reader 查询，完成窗口后快照；只通过已有正常停库流程停止身份已核实且本批启动的实例。停库失败、取消异常或身份不能确认时，保留残留进程/监听和原因，不强杀数据库、不自行扩大权限或调整预算。

普通目标 HY000/3024 仅按上表继续独立对照。安全门禁、数据/DDL/binlog/权限异常立即停止目标取证并安全收尾。不在现场修复领域 SQL。

## 8. 交付与停点

交付新报告、overlay、唯一 INDEX，分别记录：

- 原 03 的诊断定义版本；06 的新驱动/脚本实际作者、静态/离线自核与真实执行者；总控仅审核；历史 E01～E04 作者/执行者不变。
- P1～P3 每项 planned / actual executeCount / plan_captured / HY000/3024 / blocked / conditionally_not_executed；无计划保留错误，不把未执行算通过。
- 完整 SQL/hash、实际绑定及类型、各预算、原 JSON 计划或完整错误。计划估算与 EXPLAIN 耗时不能替代业务 SELECT 结果、扫描量或 p95。
- Maven/测试方法定义数、实际执行次数、失败/错误/跳过，以及纯内存验证分别统计，不将诊断执行计作业务覆盖。
- 新旧证据保全、前后零业务写入、loader/权限、残留查询、正常停机及全部差异。报告已经执行与未执行项，不只交成功计划。
- 模型要求、用户确认来源/日期、工具独立实况；实际模型未核实、实际推理强度未核实时如实记录。

完成本批或触发停止条件即停，交负责人转总控审核。06 不恢复 03、不汇集领域补丁、不运行统一业务回归/既定业务性能采样，不继续其他 I2、I3、W2。

此单不承诺能够取得计划或确定根因；诊断全部执行完成也不等于研究查询性能已修复或 I2 验收通过。最终验收由负责人决定。

本单生成时，总控只新增审核记录和执行单，没有自动派发原 06、修改诊断代码、运行测试、读取凭据或连接数据库。
