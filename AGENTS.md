# AGENTS.md — 知识图谱项目（Java + TailAdmin 重构版）

> 2026-09-13 本机环境更新：当前仓库位于 `E:\知识图谱`。已安装 Temurin JDK 17；根目录 `start-dev.cmd` / `stop-dev.cmd` 管理本地服务。独立 MySQL 位于 `127.0.0.1:13306`，本地配置跳过无旧表环境的 V2；数据保存在 `C:\Users\Administrator\.knowledge-graph-dev`。完整操作见 `docs/本地开发环境.md`。下方 D 盘路径与 3306 为原开发机记录。V3 空库种子判断已修正，TCP/UDP 及关系完整初始化。后端 109 项测试通过。

> 本文件供 AI 助手在后续会话中快速了解项目状态。**每次接手请先读完本文件与开发文档。**

## 一、项目现状（2026-09-12，阶段 A/B 完成时点）

- **2026-09-13 最新用户决策：取消人工审核，改为 AI 独立对照原文复审后自动入库。此决策覆盖下文和旧开发文档中所有“必须人工审核/确认入库”的旧要求。** 新流程抽取/分类 → AI_REVIEWING（95–98%）→ IMPORTING（99%）→ COMPLETED（100%）。默认模型逐项复审候选；证据不足拒绝、拒绝端点的关系连带拒绝；全部结果完整后同事务入库，失败保留候选可重试。前端 /review 改为 AI 复审记录；人工修改/批量审核/commit HTTP 入口返回 409，新入口 POST /api/processing/jobs/{id}/ai-review 或候选任务 retry，GET /api/review/jobs/{id}/ai-decisions 查看理由。详见 docs/AI自动复审入库.md。

- 2026-09-13 删除知识库语义已按用户预期调整：删除该库独占节点（关联边、证据、会话按外键级联），其他库共享节点和共享资料保留；首页不保留已删除节点的旧上下文。V3 通过 `bootstrap_flags/demo_seed_v3` 只初始化一次，用户清空知识后重启不恢复演示节点。历史孤立 TCP/UDP 已备份至 `.local/deleted-library-backup.sql` 后清理。

- 2026-09-13 用户新决策：新上传资料默认无需选库，由 AI 自动识别、命名、同主题归入已有库、多主题分入多个库；不重组已有库。显式指定库仍兼容，知识候选仍需审核。V6 增加 `auto_document_imports` 和 `document_topic_assignments`；`LibraryOrganizationService` 在抽取全部成功后规划，分类及建库和候选同事务保存。审核按每个候选的目标库写关联，合并已有节点也写关联，原资料由多个库共享。详见 `docs/AI自动整理.md`。

- 旧 PHP 项目（knowledge-graph/backend、旧前端）已由用户于 2026-09-12 明确要求全量删除，仅保留 TailAdmin 压缩包。
- 依据 `docs/第一版开发文档-Java-TailAdmin.md`（V1.0，1206 行）执行重构，**该文档是唯一需求/架构/接口/UI 依据**。
- 目录结构：
  - `docs/` — 开发文档与交付文档
  - `ui-template/vue-tailwind-admin-dashboard-main/` — TailAdmin Vue 免费版只读参考
  - `vue-tailwind-admin-dashboard-main.zip` — 原始模板压缩包（只读保留）
  - `knowledge-graph/frontend/` — TailAdmin Vue 前端（Vue3 + TS + Tailwind 4，规范见 frontend/AGENTS.md）
  - `knowledge-graph/backend-java/` — Spring Boot 3.5.x 后端（Java 17 编译目标，文档原文要求 21，本机仅有 JDK17）
- 数据库：MySQL 8（127.0.0.1:3306，库 `knowledge_graph`；密码不入仓库，经 `KG_DB_PASSWORD` 或本地 application-local.yml 提供）。
  旧表（nodes/edges/documents/extractions/rules/settings/users）**只读保留**，新表（knowledge_libraries、knowledge_nodes、knowledge_edges 等 §11 模型）与旧表同库共存，启动时通过 `db/migration/V1~V3` 幂等建表并从旧表迁移数据。

## 二、阶段进度

- ✅ 阶段 A：Java 骨架、统一响应包络（code=0/错误码 + requestId）、健康检查、图谱/搜索/节点/关系/知识库/洞察/设置接口，旧数据迁移（Spring Boot 3.5.16 / Java 17；35 单测 + 42 接口自测全绿）
- ✅ 阶段 B：TailAdmin 六模块信息架构（图谱工作台/知识库/处理中心/审核中心/数据洞察/系统设置）、顶部全局搜索（唯一入口）、「＋导入资料」唯一按钮、2D SVG 画布、节点详情抽屉、AI 面板（未接模型时显示阶段提示）、各页加载/空/错态；type-check + build 通过；浏览器实测通过
- ✅ 阶段 C：用户已批准 `3d-force-graph@1.80` + `three@0.186`（+ `@types/three`）；`GraphCanvas3D` 实现 3D 星链图谱（旋转/缩放/节点拖动/单击聚焦/双击扩展/悬停提示/方向粒子/中心发光），2D(SVG)/3D 可切换且保持当前节点，WebGL 失败自动回退 2D；URL 同步 `/?node=&depth=&mode=`
- ✅ 阶段 D（2026-09-13）：旧 `documents` 表已按用户决策归档为 `documents_legacy`（RENAME 可逆，0 行数据）；新 §11 摄取表统一由 `V5__ingestion.sql` 定义（V1 中四表定义已移除；node_evidence/edge_evidence 的 chunk 外键因有符号/无符号 bigint 类型冲突移除，阶段 E 建齐后补）；Maven 新增 PDFBox 3.0.3 + POI 5.3.0（Apache-2.0，用户确认）。实现：上传校验（扩展名+魔数+大小上限+ZIP 结构预扫防路径穿越，恶意 ZIP 上传即 415）、SHA-256 去重（409）、UUID 磁盘存储；PDF（逐页文本，<20 字符页渲染 PNG）/PPT/PPTX（文字+备注）/ZIP（自然排序图片）解析器；`OcrProvider`=视觉模型通道（llm_profiles 中 vision=true 档案，实测 GLM 真实转写中文图片）；异步流水线 VALIDATING→PARSING→OCR_RUNNING→CHUNKING→COMPLETED/FAILED（单元间检查取消标记，重试幂等清理旧产物）；E2E 全绿：三类样例均产出带 source_locator 的 chunks、伪 PDF 415、去重 409、COMPLETED 重试 409；夹具生成器 `src/test/java/tools/FixtureGen.java`（§18 自制样例）
- ✅ 阶段 E：LLM 抽取 + 审核入库（2026-09-13 交付）。流水线状态机改为 CHUNKING→AI_EXTRACTING→AWAITING_REVIEW（分段完成≠完成；IMPORTING→COMPLETED 仅由人工确认入库驱动；进度映射 VALIDATING 5/PARSING 10~40/OCR 40~70/CHUNKING 70~80/AI_EXTRACTING 80~95/AWAITING_REVIEW 95/IMPORTING 96~99/COMPLETED 100）。新增 `extraction/` 包：`ExtractionService`（chunks 按字符预算 6000/批 ≤8 条分批、批数上限 60、伪造 chunkId 整批拒绝、任务内按标准名/英文名/别名去重并重定向关系、已有节点/关系匹配写 matched_node_id/matched_edge_id、全部批次成功后同一事务写候选+置 AWAITING_REVIEW；重跑先删旧候选幂等）、`ExtractionPayloadParser`（不可信输出：容忍围栏、nodeType 白名单、confidence 截 0~1、字段限长、evidenceChunkIds 必属当前批次）。`ReviewService` 六接口（§12.5）：查询 DTO 对齐前端（aliases/evidenceChunkIds 数组、sourceName/targetName、真实证据 documentName+source_locator+摘录）、PUT 编辑（EDITED 写 edited_payload_json、非法状态 400、非 AWAITING_REVIEW 409）、bulk-action、**commit 单事务入库**（FOR UPDATE 锁定→IMPORTING→合并/新建节点+别名+library_nodes+node_evidence→复用/新建关系+edge_evidence→job+document COMPLETED；失败整体回滚；重复提交 409）。恢复入口 `POST /api/processing/jobs/{id}/extract`（复用既有 chunks 不重新解析；已有候选 409，force 重抽；无 chunks 422 DOCUMENT_NO_EXTRACTABLE_TEXT）。前端 ReviewView 支持多任务 SelectInput 切换/接受/拒绝/编辑弹窗/入库确认与结果提示；ProcessingView 删除页面内导入按钮（唯一入口=顶栏）并对 COMPLETED/FAILED 且无候选的任务显示「提取知识」。注意：entity_candidates 无 name_en 列——抽取持久化时英文名折叠进 aliases_json；MockitoBean 集成测试用 `spring.sql.init.mode=never`。
- ✅ 阶段 F：节点 AI 问答（2026-09-13 修复交付）。后端新增 `chat/` 包：`ChatController`（§12.6 五条路由）、`ChatService`（会话/消息持久化 + 图谱上下文检索 + 防注入 system prompt + 证据不足判定）、`LlmClient`（OpenAI 兼容 `/chat/completions`，401/403→LLM_AUTH_FAILED、超时/网络/5xx→LLM_UNREACHABLE、解析失败→LLM_BAD_RESPONSE，异常消息密钥打码）；`LlmProfileService.requireDefaultEnabledProfile()` 提供内部默认模型取用（无默认/禁用/缺 Key 均 503 LLM_NOT_CONFIGURED）；测试连接接口已校验非空 `choices[0].message.content`（ping max_tokens=512，思考型模型 8 token 会全耗在思维链）。前端 `useAiChat` 按 sessionId 复用会话（openNode 自动加载最近会话、新对话仅解绑不删历史）、AbortController 真正中断请求、LLM_* 错误码映射中文提示。78 后端测试全绿（含 LlmClient 假上游 401/403/404/500/超时/断连/密钥泄露防护），type-check/build 通过，TCP/HTTP/PHP/停止生成/重启持久化浏览器实测通过；注意 Mockito `eq(9)` 对 long 参数不匹配（Integer.equals(Long)），必须 `eq(9L)`。
- ✅ 搜索未命中时由 AI 补全知识图谱（2026-09-13，需求 §12~§19）。后端 `search/` 包新增：`AiExpandController`（POST /api/search/ai-expand，同步接口，无新依赖）、`AiExpandService`（本地查重→ConcurrentHashMap single-flight→默认模型档案→结构化解析→事务入库，唯一允许因搜索写库的入口）、`AiExpandStore`（写库前事务内再查重；节点/别名/知识库关联/关系同事务；目标节点已存在则复用，边走唯一约束+INSERT IGNORE 防重）、`AiExpandPayloadParser`（模型输出按不可信输入：容忍 Markdown 围栏、类型白名单校验、超长截断、主题一致性校验，非法 JSON→LLM_BAD_RESPONSE 不写库）。AI 节点 status=active，properties_json 记录 origin=ai_search_generation/generatedQuery/modelProfileId/model/generatedAt/reviewStatus=PENDING；原始查询词强制写入别名保证二次命中。前端 SearchBar 本地联想为空显示「本地未找到，使用 AI 生成并收录…」，仅点击/Enter 显式触发（防抖/联想绝不调模型），生成中可取消等待，LLM_* 错误码映射中文文案且失败保留关键词，成功关闭下拉并跳转 `/?node={id}&depth=1&mode=local`（route.query 监听自动重载 2D/3D）；NodeDetailDrawer 显示低干扰徽标「AI 生成 · 待审核」。运行时验收 25/25（工具 `tools/ai-expand-acceptance/`：并发同词仅一次模型调用、超量关系截 8 且去重后入库 7、非法 JSON 无残缺数据、密钥不出现在响应/properties、AI 问答只读）；真实 GLM 生成的 CDN 知识簇（7 节点）已保留在库。97 单测 + type-check/build 全绿
- ✅ 文档属性面板（2026-09-13，对照用户提供的属性界面补充）：V7 迁移新增 `document_metadata`（title 可编辑展示名 / lifecycle_status 在用-归档-过期 / verification_json 可信度汇总：哈希校验+AI 复审通过率+人工校对）、`document_tags`（多值标签，source=human|ai，(document_id,normalized_tag) 唯一，人工同名标签优先、AI 只补不覆盖，≤20 个/资料）、`library_aliases`（知识库别名）。接口：PUT /api/documents/{id}/metadata、POST/DELETE /api/documents/{id}/tags、PUT /api/documents/{id}/verification、PUT /api/libraries/{id}/aliases；上传即写默认元数据+哈希校验；AI 自动整理按「组名+组别名」匹配「库名+库别名」（UNION 查询）归库、组别名沉淀到 library_aliases、主题名写 AI 标签；AI 复审入库完成时把复审汇总合并进 verification_json（同事务）。前端知识库卡片新增「资料 N」展开面板（标题/格式/大小/更新时间/生命周期徽标/哈希-AI-人工可信度徽标/标签 chips 增删），编辑知识库弹窗支持别名。存量文档元数据惰性补建（首次编辑或 mergeVerification 时按原文件名补行）。146 项测试全绿。
- ⏳ 阶段 G：联调、迁移、交付文档（docs/api.md、data-dictionary.md、ingestion-pipeline.md）
- 验收遗留数据（2026-09-13，可由用户决定清理）：知识库「验收测试资料库」(id=101) 含 3 份自制数据结构 PDF（acceptance-ds*.pdf）、任务 #112 已入库（节点 栈/队列/线性表/先进先出 + 4 关系，properties origin=document_extraction）、任务 #113/#114 在审核中心等待人工处理（真实模型候选，可直接体验审核流程，或删除任务与文档）

## 三、运行方式

- 后端：`cd knowledge-graph/backend-java && java -jar target/*.jar`（或 `mvnw.cmd spring-boot:run`），端口 8080；需环境变量 `KG_DB_PASSWORD`（或本地 application-local.yml，见 application-local.yml.example）
- 前端开发：`cd knowledge-graph/frontend && npm run dev`（5173，已代理 /api → 8080）
- 后端构建：`mvnw.cmd test` / `mvnw.cmd package`；前端：`npm run type-check` / `npm run build`
- Maven 位于 `D:\Envirconment\apache-maven-3.9.9\bin\mvn`（PATH 无 mvn）；JDK 17 位于 `D:\Envirconment\jdk-17.0.12`

## 四、关键约定

- 变更管理：开始开发前先读 `docs/变更记录.md`，一次只实施其中一个小批次；完成后必须逐项登记实际的新增、修复、删除、影响范围和真实测试结果。未实施内容只放在分批计划中，不得记作已完成。
- 前端严格遵守 `frontend/AGENTS.md`（TailAdmin 令牌、RTL 逻辑属性、`<script setup lang="ts">`、dark: 变体）；新增 UI 原语放 `components/ui/`，表单原语在 `components/ui/form/`；业务组件按 §10.3 放 `features/`
- UI 最新决策（2026-09-13）：用户提供同为 TailAdmin 的参考程序，要求深入调整布局与按钮，覆盖此前“所有页面流式铺满”的反馈。管理页面使用居中、最大 1280px 内容区；侧栏展开 240px、折叠 76px；图谱画布继续使用可用工作区。概览→主要内容→可选高级设置，主次操作有明确区分。存储与图谱偏好不作为前置表单。细节见 `docs/UI布局与交互规范.md`。`SelectInput` 仍为自绘下拉，**不要再使用原生 `<select>`**；品牌 Logo 共用 `components/layout/AppBrandLogo.vue`；图标使用当前主题颜色。
- API 合同以开发文档 §12 为准，前端统一走 `src/services/http.ts`（包络解析 + ApiError）；**后端实际字段以 backend-java 的 DTO record 为准**（如节点详情是 `name/type` 而非 canonicalName/nodeType，洞察是 `nodeTypeDistribution/topDegreeNodes`），前端已对齐
- 3D 画布容器必须绝对定位（three.js 固定像素画布会撑破 flex 布局）；设置 PUT 是整包 `SaveRequest{llm,ocr,storage,graph}`
- 状态用组合式函数共享（模块级 ref），**不引入 Pinia**；新依赖（npm/Maven）必须先列清单征求用户同意（§0.7）；已批准：3d-force-graph、three、@types/three
- 旧表只读；任何 DROP/清库类操作必须先征求用户同意
- 测试中文 JSON 用 Node fetch，不要用 Git Bash curl 发中文（GBK 问题）
- 环境变量与密钥不入仓库：`application-local.yml.example` 只含占位符；LLM/OCR 未配置时接口返回 503，前端显示「未配置」
- ~~⚠️ 阶段 D 前置决策~~（已解决）：旧 `documents` 表已归档为 `documents_legacy`；V1/V5 迁移分工与证据表 chunk 外键移除原因见阶段 D 条目
- 模型能力与 OCR（2026-09-13 用户决策）：设置页已移除 OCR 配置卡，改为**多模型档案管理**——新表 `llm_profiles`（V4 迁移）+ `/api/settings/llm-profiles` CRUD/设默认/测试接口（`LlmProfileService/Controller`，Key 加密存储只回掩码，重名 409）。每个档案含 `vision` 布尔（视觉能力）与 `is_default`（默认模型）。**取用规则（用户确认的简单方案，不做任务级槽位）**：节点问答与文本抽取固定用「默认」档案；文档解析遇到图片/扫描页时，取已启用且 `vision=true` 的档案做视觉识别（无则跳过并提示）。旧 `llm.*` 单模型键与 `/api/settings` 保留兼容但 UI 已不使用
- 弹窗规范：`Modal` 组件的 body 内容容器**必须加 `relative`**，否则 fixed 遮罩会盖住内容导致按钮点不到（曾致删除确认失效）
- AI 搜索补全（§12~§19）：写库唯一入口是 POST /api/search/ai-expand；同词并发用 `ConcurrentHashMap` single-flight（不引入 Redis/队列）；去重按 `LOWER(TRIM(canonical_name))`/`name_en`/`normalized_alias` 三段精确匹配，调模型前与事务写库前各查一次；模型返回的 relations 最多取前 8 条、relationType 截 100 字、类型白名单外归 other；API Key 绝不入日志/响应/properties_json

## 五、验收入口

- 开发：http://localhost:5173/ （需先后端后前端）
- 快速自检：顶部搜索「TCP」→ 应联想并跳转 `/?node=…&mode=local`，画布聚焦 TCP 邻域，右侧 AI 面板绑定该节点
- 未实现入口的预期行为：3D 按钮 → 已实现；上传 → 阶段 D；AI 问答 → 已实现（未配置默认模型时面板内显示 LLM_NOT_CONFIGURED 中文提示）
- AI 补全自检：搜索一个本地不存在的关键词 → 下拉出现「本地未找到，使用 AI 生成并收录…」→ 点击或 Enter → 成功后跳转 `/​?node={id}&depth=1&mode=local` 聚焦新节点，节点详情含「AI 生成 · 待审核」；再次搜索同名或别名直接本地命中、不再调模型。回归工具：先 `node tools/ai-expand-acceptance/mock-llm-server.mjs`，再 `node tools/ai-expand-acceptance/run-acceptance.mjs`（结束后自动清理测试节点/档案并恢复默认模型档案状态）
- ✅ V5 启动问题（2026-09-13 已根治，无需再绕过）：曾因 V5 `library_id BIGINT UNSIGNED` 与 `knowledge_libraries.id`（有符号）外键类型不匹配、及遗留 document_units 旧结构/证据表孤儿 chunk 外键导致启动失败。已修正 V5 类型、删除四张空新表由 V5 重建（含完整外键）、移除 V1 证据表 chunk 外键、清理 node_evidence/edge_evidence 孤儿外键。现在 `java -jar target/backend-java-0.1.0.jar` 直接可启动；V5 全部四表已建并跑通摄取 E2E
