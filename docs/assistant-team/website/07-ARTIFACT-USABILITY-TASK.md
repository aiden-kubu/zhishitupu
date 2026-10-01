# 原07：成果可见性与检索提示最小补修（正式执行）

2026-10-01。授权来源：总控与原06完成定向审查并提出先交原07改善展示/提示后，负责人回复“继续”。本批只包含本文件规定的前端补修；不包含后端修复、数据库重启/连接、部署或下一阶段。06与其他任务待命。

## 执行与门禁

执行者为原桌面“智能助手 07｜网站前端”，GPT-6.1 Sol / High。原工作树D:/知识图谱-worktrees/assistant-07-web-ui，分支codex/assistant-07-web-ui，HEAD 8544b19f89bbcf477b9b458be7f48117775ff621。主项目只读，每条命令明确workdir。保留所有既有已跟踪修改和未跟踪文件，不切树、不清理、不提交。

阅读本任务、07-ARTIFACT-USABILITY-BASELINE.json、ARTIFACT-TARGETED-REVIEW.md的新结论；未变的长期规则、frontend/AGENTS及原07已审成果直接复用，不重读全部W1/I2。核对分支/HEAD、四项前像及直接依赖；baseline特意分别记工作树与主项目的只读router前像，既有差异不要求同步。新增报告/输出目录不得占用。门禁一致即直接实施，不回报一轮无阻塞接令再等第二次授权。出现未知变化、前像不符、边界冲突才停报。

允许修改且仅允许修改：

- frontend/src/views/Assistant/AssistantView.vue
- frontend/src/features/assistant/ArtifactPanel.vue
- frontend/src/features/assistant/EvidenceDrawer.vue
- frontend/src/features/assistant/presentation.ts

允许新增自己树docs/assistant-team/reports/07-artifact-usability.md及docs/assistant-team/website/07-artifact-usability/**（前像、overlay、命令输出、简短验收说明）。构建/类型缓存只落自己树frontend/dist/07-artifact-usability-<唯一runId>/**。源码和新增文档用apply_patch，前像副本按受控归档处理，不覆盖旧材料。不新增通用验证框架或浏览器脚本。

## 四项可交付改进

1. **点击能找到成果。** 保留Workbench的loadArtifact、响应身份及代际隔离不变，在页面层补点击反馈与成果区滚动/焦点定位；加载、失败及当前选择都应明确可见。自动预选或轮询不得抢焦点/跳页面；过时点击的异步返回不得把用户带到另一个成果。显示正在查看的步骤及资料（服务已有标题/资料ID即可，不伪造标题），重复点击当前项也能定位，不能将按钮蓝色作为加载成功证据。不要重做整页或新建流程。
2. **检索提示符合能力。** 输入框明确当前是关键词匹配，建议输入原文里实际出现的概念/短语；不再用“检索资料中的依赖关系”暗示已理解整句指令。提示真实模型未接入时的现状，保留原输入/长度/按钮及提交规则，不把“节点甲”硬编码为真实业务数据，也不自动改写主题、拆词、扩大检索或要求后端非空。
3. **结果状态说清楚。** 用真实source_read.items数量说明“已找到N个片段”或“未找到匹配片段”，同时保留partial、截断、资料未覆盖与未证明全资料覆盖的限制，不把partial改成completed。为REQUESTED_DOCUMENTS_UNCOVERED及NO_FORMAL_SOURCE_MATCH补中文解释；技术原因仍可查，未知原因不丢弃。提醒应简洁、不重复通用句；语义未经评估的边界保留。
4. **状态引用与原文分开。** 明确区分资料状态记录、真实可重读原文、图谱/其他引用；“证据数量”不等于片段数量。metadata无指针应解释为资料状态记录不是正文，而非误导为原文读取故障。原文条目/论断按钮打开抽屉时优先定位对应sourceId（只定位确实存在的引用，不伪造）；所有继承/原始引用仍可查看，来源任务/成果身份仍保留。使用既有rereadPointer判断有无重读入口，不能凭标题/文件存在制造指针，不能凭指针推断引用已核查。若分类/定位调整列表顺序，保持原引用与busy/errors/results的对应索引/身份，不能将一个引用的重读正文显示到另一个引用。保留精确重读及陈旧失败清正文、Tab/Escape/关闭焦点恢复和窄屏换行。

只允许内部前端组件展示参数/事件调整，不改变assistantTypes、responseValidation、assistantApi、useAssistantWorkbench、公共Modal/Button、router/Sidebar、09图谱、包/锁/POM/Vite/tsconfig、任何后端或权限。若确需跨模块字段或语义变更，停止该项说明原因，不自行扩范围；普通展示覆盖缺口记录后完成其余独立已批项。

## 验证收敛，不再堆脚本

只做：针对受影响代码的静态自查，一次现有vue-tsc与一次Vite构建。沿用既有资源/缓存约束：vue-tsc --project tsconfig.app.json --noEmit --incremental --tsBuildInfoFile <本批独立cache>；Vite build --configLoader runner --outDir <本批独立site>，所有输出只落自己树。不得运行默认全仓lint/format/build缓存写入、安装依赖、浏览器自动化、历史整套回归、数据库或真实宿主。不要为覆盖本批再开发测试驱动。

如必要类型/构建失败，保留首轮事实；只修本批引入且在四文件内的问题。最多一次定向修正后重跑失败命令，不靠改契约/标准或反复无界重跑转绿；既有范围外阻断交总控判断。没有实际运行的人工/浏览器场景写“待负责人验收”，不声称通过。

交付人工验收指引不超过五项，写具体点哪个按钮、正常应看到什么：切换成果能定位/标明步骤；关键词提示清楚；有片段与无匹配区别；资料状态不冒充正文；原文条目可定位对应引用且可合法重读。当前验收环境已正常停止，不要求负责人现在打开已经下线的网站；总控审核后另由获批的06完成必要汇集/预览准备。

## 交付停止

报告列四文件实际变更及理由、未改边界、前后raw hash、已有只读依赖一致性、命令与实际结果、未执行项及人工验收步骤。作者/类型检查/构建执行者原07；总控审核待完成；主项目汇集、数据库、生产环境及人工验收者本轮无。模型要求与负责人确认/桌面观察分别记录，无法独立回读写实际模型未核实；实际推理强度未核实。保留历史作者、报告和expected hash。

完成后在原07回报并停止，等待总控增量审核；不汇集主项目、不启动06或其他任务、不连接数据库、不部署、不进入I3/W2。总控也不代做这四个文件。
