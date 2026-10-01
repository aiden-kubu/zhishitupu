# 09 图谱联动第一阶段任务

执行者新增 09，模型 `gpt-6.1-sol / high`。工作树 `D:/知识图谱-worktrees/assistant-09-graph-ui`，分支 `codex/assistant-09-graph-ui`，基底 `8544b19f89bbcf477b9b458be7f48117775ff621`。等待总控交付获审 08 契约及 07 的联动需求后正式开工。

## 目标和允许范围

在现有图谱工作台提供独立的研究结果只读模式，消费服务端允许显示的真实节点、边、路径、引用和范围。可从助手成果进入、定位路径、查看节点/边来源，再返回原成果。继续复用现有 2D/3D 渲染，不建立第二套全图数据库或写入通道。

只修改本工作树 `frontend/src/views/Graph/GraphView.vue`、`frontend/src/composables/useGraphWorkspace.ts`、`frontend/src/features/graph/GraphToolbar.vue`、`frontend/src/features/graph/NodeDetailDrawer.vue`，确有必要时修改现有 GraphCanvas2D/3D 的只读显示 props；允许新增 `frontend/src/features/graph/ResearchResultPanel.vue` 和 `frontend/src/composables/useResearchGraph.ts`，以及仅用于隔离检查的 `frontend/src/features/graph/verification/**`。使用 07 已提供、经总控审核并由 06 按批准清单同步的 assistantApi/types 及其直接只读辅助依赖，不直接改它们或助手页面；必要契约变化交总控协调。允许新增自身阶段报告 `docs/assistant-team/reports/09-graph-ui-stage1.md`。

首次完整读现行主项目协作规则、frontend/AGENTS.md、WEBSITE-TEAM、获审 HTTP 契约及 I1 页面/旧写隔离方案。总控准备精确依赖清单与审核，06 负责获批同步，不整体覆盖工作树。07 尚未交付审核及依赖尚未同步前，本任务继续待命。

## 显示与安全语义

路由中的 run/artifact 定位值不作为权限凭证；研究模式只显示宿主返回的授权子图，不用旧全局/邻居 API 扩大补齐。节点/边真实 ID、方向和来源保持，草稿单独展示、不并入真实统计；缺失来源或部分覆盖如实显示。

现有图谱路由名为 `GraphWorkspace`，path 为 `/`；研究模式在该现有路由解析 runId/artifactId，保留正常无上下文入口，不新增 `/graph` 路由。任一研究定位参数存在但缺失、数组或格式非法时，显示不可用/参数错误，不降级为无上下文全库模式。与 07 按同一已有路由协议对接。

返回成果链接使用已有 `/assistant` 并保留 runId/artifactId，不能只回一个丢失上下文的首页。07 当前运行状态仅在组件内存，尚未消费返回 URL；这项恢复与图谱按钮启用由总控在 09 交付后交回 07，不允许 09 越界修改助手页面。09 交付说明该联动依赖，不把尚未实现的端到端返回说成已验证。

研究模式隔离搜索扩展、节点/关系创建编辑删除、抽屉保存和键盘快捷写操作，保留正常业务工作台原模式。不能在适配层重写边过滤、学习规则或权限判断。过期/不可用成果提示刷新或返回，不能偷偷回退到全库。

隔离须覆盖 setup、挂载和 watcher，不只是隐藏按钮。当前 GraphView 无条件调用 useGraphWorkspace，后者仍有旧 overview/neighbors 读取；NodeDetailDrawer 的 nodeId watcher 也会调用旧 node/neighbors API。研究模式必须阻止这些旧读取及任何 visited/学习保存副作用，详情只消费已授权成果与新重读接口。不得为了只读模式改写旧业务规则；通过模式/调用边界隔离，正常管理模式保持原有功能。后续验证须记录研究模式实际请求集合，旧端点没有请求不能只靠按钮截图证明。

ID 与现有数值型渲染之间的转换须符合获审安全表示；超过安全整数的 ID 不截断或取近似值，明确显示当前渲染限制并报告，不修改 Java Long 契约。正常选中/路径方向和 source→target 不因高亮被倒置。

## 验证和停止

只在自己的树用现有依赖做类型检查/构建及必要交互验证，不安装新包、不跑未变领域测试、不启动后端/数据库/模型。记录真实 API 联动尚未执行的部分；预置设计数据若仅用于本地展示必须明确隔离，不能进入生产请求结果。

交付真实代码文件清单、必要只读模式验证、与 07/08 的依赖及未覆盖项。最终自动回报总控后停止，等待下一阶段指令，不汇集到主项目或启动 06。

## 正式开工前像与依赖

总控于 2026-09-30 在指定 09 工作树定向记录如下 raw SHA-256；当时 Git 状态为空，三个拟新增源码/验证目录及报告均缺席。06 随后精确同步的三个客户端依赖是已知修改，不能把它们当作未知变更。

| 已有文件 | raw SHA-256 |
| --- | --- |
| frontend/src/views/Graph/GraphView.vue | 18b29648205d17444a742f6b1597945ad8efd59abb2314e7ee3835df3bcb6871 |
| frontend/src/composables/useGraphWorkspace.ts | 167dcc21d6bfa72cf1f3f93e36429a313dda4734fd6ebe80a78cda4104ffe431 |
| frontend/src/features/graph/GraphToolbar.vue | 180d89e4d196c42ed66144c7efb0770b17a45dc7abc27c1b1c8f58f0f6b73823 |
| frontend/src/features/graph/NodeDetailDrawer.vue | 5dd8d21694c2b2ead1c917fa1677d3328e1a6c49ea3ae1834f710ba1521279af |
| frontend/src/features/graph/GraphCanvas2D.vue | 52390283420c4345a7c8d12500925174ac430cde8e7a1bd73c948d09777ac313 |
| frontend/src/features/graph/GraphCanvas3D.vue | ddd56b7171eed2b5d38a75902651726b69cd2798f64736e31010e78a91915b61 |
| frontend/package.json | 6c4e1131279875f15a055b0c3202beef8dbb5272d5aad9ffe1ed6639d389b31c |
| frontend/package-lock.json | 19597f0b5766c8e5f4a55f60010715ea94959350e5f52efe2ac51d9a49a054c2 |

正式指令发出时须一并引用 06 已完成的依赖汇集报告及清单；没有汇集结果不得开工。必要门禁一致直接实施，不返回无阻塞接令审批。

现有依赖复用：package/lock 两端相同且本树 node_modules 缺席时，允许创建本树 frontend/node_modules → D:/知识图谱/frontend/node_modules 的目录联接，不安装/更新包。验证输出及缓存只能在本树；不要运行默认 npm run build/type-check 或复用 07 失败的缓存 monkeypatch。允许采用应用项目显式 --tsBuildInfoFile 本树路径，以及 Vite build --configLoader runner（现有 Vite 6.4.3 已提供该选项），避免默认配置打包临时文件写共享依赖。若其他必要检查不能隔离输出，停报具体问题，不放宽检查标准或反复探针。

定向浏览器检查可用自己创建的 loopback 前端静态服务、独立临时 headless 浏览器 profile 及只在验证脚本中的离线响应夹具，不启动后端、不接管用户浏览器、不调用真实 API。至少检查有效研究上下文、无效/过期上下文、方向与选中、部分/空结果、Long 安全表示，以及研究模式的请求集合无旧 overview/neighbors/node 读取或写入口。正常管理模式不得被全局关闭；若其真实服务未接入，明确标未验证，不制造通过。

主项目治理及任务材料只读；不删除缓存、不动历史证据、不开 DB/模型/I2 批次。代码/报告一律 apply_patch，完成停止。
