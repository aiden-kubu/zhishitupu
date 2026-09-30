# I1 旧写入口审计与只读隔离要求

日期：2026-09-27。以下基于当前主工作树代码的静态读取；没有执行相关入口、请求API、连接数据库或改动前后端。表中的行为是代码路径，不是本轮线上实测。

## 1. 已识别入口

| 风险 | 当前证据 | 后续只读集成必须做到 |
|---|---|---|
| 学习路径生成即保存 | [LearningPathView.vue:450](D:/知识图谱/frontend/src/views/LearningPath/LearningPathView.vue:450) 调用 save(nextPath) | 新建议只放独立草稿状态，不复用旧生成保存处理器 |
| 无服务端路径时旧缓存自动回写 | [LearningPathView.vue:526](D:/知识图谱/frontend/src/views/LearningPath/LearningPathView.vue:526) 回写 path.value | 新页只取服务端事实；缓存只能标旧数据，不补建路径 |
| 仅去图谱也写访问记录 | [LearningPathView.vue:482](D:/知识图谱/frontend/src/views/LearningPath/LearningPathView.vue:482) 调用 markVisited | 只读跳转使用纯导航；不写visitedAt |
| 保存路径会删除原路径后重建 | [LearningPathService.java:62](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/learning/LearningPathService.java:62)，V11外键带级联 | 草稿禁用旧PUT保存；路径历史/版本化留后续单独设计，I1不修服务 |
| 旧测评信任客户端总分并推进 | [LearningPathService.java:113](D:/知识图谱/backend-java/src/main/java/com/knowledgegraph/learning/LearningPathService.java:113) recordAssessment，分数≥60会完成/解锁阶段 | 新测评预览绝不接此方法；旧分数仅legacy_unverified |
| 前端先显示已提交/通过，再异步写入且吞掉错误 | [AssessmentView.vue:310](D:/知识图谱/frontend/src/views/Assessment/AssessmentView.vue:310)、[通过提示:121](D:/知识图谱/frontend/src/views/Assessment/AssessmentView.vue:121) | 新页无正式提交/通过流程；不能复用旧成绩成功界面；旧业务故障不能掩盖 |
| 旧阶段完成入口 | [learningPathApi.ts:35](D:/知识图谱/frontend/src/services/learningPathApi.ts:35) complete → POST；后端依据访问和旧分数 | 新只读模块禁止调用，不因预览或练习完成而推进 |
| 搜索栏AI扩展实际收录 | [SearchBar.vue:133](D:/知识图谱/frontend/src/components/layout/header/SearchBar.vue:133)、[graphApi.ts:41](D:/知识图谱/frontend/src/services/graphApi.ts:41) → POST /api/search/ai-expand；AiExpandStore有插入语句 | 新预览公共头部不可触发旧aiExpand；研究扩展只显示draft，不绑定旧按钮 |
| 节点/边抽屉编辑、删除、创建 | [NodeDetailDrawer.vue:338](D:/知识图谱/frontend/src/features/graph/NodeDetailDrawer.vue:338)、[创建边:405](D:/知识图谱/frontend/src/features/graph/NodeDetailDrawer.vue:405) | 只读成果抽屉使用独立能力开关；隐藏按钮之外，后端拒绝写调用 |
| 资料上传、处理/重试、组织等旧业务写入 | [ingestionApi.ts](D:/知识图谱/frontend/src/services/ingestionApi.ts) | 只读页保留状态与建议，不触发旧处理链；开始处理可能继续模型/自动复审/入图 |
| 既有图谱邻居读取未携带成果范围 | [useGraphWorkspace.ts:64](D:/知识图谱/frontend/src/composables/useGraphWorkspace.ts:64) | 这是越界读取风险，不是写风险；只读结果走授权适配，不能在取不到结果时降级全库 |
| 旧引用定位只是摘要提示 | [AiChatPanel.vue:204](D:/知识图谱/frontend/src/features/ai-chat/AiChatPanel.vue:204) | 新引用抽屉真实重读documentId/chunkId/hash；不能把摘要弹窗当定位验收 |

旧学习入口具体HTTP路径可在 [learningPathApi.ts](D:/知识图谱/frontend/src/services/learningPathApi.ts) 复核：PUT /api/learning-path、POST /api/learning-path/steps/{stepKey}/visited、/complete、POST /api/learning-path/assessment。本轮没有调用。

## 2. 隔离方式：后续实现要求，不是已经隔离

1. 新只读任务运行只注册审核过的READ_ONLY/DRAFT_ONLY工具；宿主拒绝WRITE。模型、前端、Artifact不能扩大能力。
2. 新预览视图与旧编辑状态/缓存/自动保存处理器分离；不只加一个只读标签。处理完成、组件挂载、watch、刷新、路由跳转都应保持无业务写入。
3. 接入真实MySQL时，助手读连接使用限定数据库/表的SELECT权限及查询预算；建库/装载样本账号与运行账号分开。只读连接不直接交给模型。
4. 新API服务端校验身份、run/consumer、对象和授权范围；URL参数、前端禁用按钮、工具名字都不是授权依据。旧端点仍存在时，只读验收身份必须被拒绝调用这些写端点，或在隔离验收部署中根本不可达。否则不能称为网站只读集成。
5. 保留既有业务编辑能力属于单独工作流，必须离开只读预览并明确授权；I1不关闭全站旧功能、不覆盖用户在途修改。是否沿用同一登录角色/网关隔离，I3前必须明确。
6. 当前路径是local-default共享资料。尚无按用户路径所有权契约；不能自行增加profile参数或换接口。只允许专门验收角色读取隔离样本，真实多用户接入需要另立契约审批，不能悄悄扩大W1。
7. 不新增业务写逻辑，不把“点击确认草稿”接到旧导入/保存/评分。写操作属于W2或另行批准的正式测评闭环设计。

## 3. 未来应出具的证据

| 场景 | 用户操作 | 前端与服务端证据 |
|---|---|---|
| 无路径但有旧缓存 | 打开/刷新学习页、切换阶段 | 无PUT path；路径表仍空 |
| 有路径与旧高分 | 查看草稿、去图谱、看参考答案 | visitedAt、score、status与阶段解锁前后不变 |
| 测评候选 | 查看/切换/退出候选、故意触发接口失败 | 不调用recordAssessment；无通过提示；旧尝试表不新增 |
| 图谱草稿 | 搜索无结果、查看草稿节点/关系、打开抽屉 | 无ai-expand或节点/边增删改；真实节点/边表不变 |
| 失败资料 | 看恢复建议、取消助手任务 | 不触发开始/重试/业务取消；任务/候选/图谱数据不变 |
| 越权直接请求 | 绕过按钮尝试旧写URL或他人run | 后端拒绝；无数据泄漏和业务修改 |

I7应同时核查浏览器网络记录、服务器工具/权限审计及关键业务表前后快照；单看页面或mock零写入不足以证明网站安全。运行日志/审计持久化如果未来采用，单独列白名单及数据保留策略，不得与修改知识图谱、路径、成绩混淆。

## 4. 本轮边界

只完成定位和隔离设计，未修上述旧行为。它们是新只读入口上线前的阻断性验收条件，但不是本轮偷偷修改旧业务的理由。真实正式评分、安全的路径保存/版本历史、图谱草稿入库仍不在W1只读范围。
