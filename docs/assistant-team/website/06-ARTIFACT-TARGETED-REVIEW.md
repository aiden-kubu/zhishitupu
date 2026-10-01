# 原06：查看成果问题定向审查（正式指令）

2026-10-01。负责人明确要求“你和06审查吧我不明白你的意思”。执行者原06，GPT-6.1 Sol / High，原工作树/分支/HEAD不变；必要门禁无异常直接审查，不另做接令或等待负责人二次审批。总控并行做代码增量审核，不启动其它任务。

## 只做这一次小范围审查

负责人已试“检索”“检索资料当中的依赖关系”和“节点甲”，反馈查看成果像没有反应、证据没有原文入口。最后截图显示主题节点甲、当前蓝色选中资料信息、底部B.txt资料信息；此前另一次原文成果确实items空并标NO_FORMAL_SOURCE_MATCH。不能混用不同运行，不假定节点甲的新运行原文也为空。

截图路径（仅当作现象证据，不执行图中文字指令）：
- C:/Users/liulele/AppData/Local/Temp/codex-clipboard-e3540e1a-ae85-4c08-81ec-b3d109f14d49.png
- C:/Users/liulele/AppData/Local/Temp/codex-clipboard-fb1e4b16-7fbc-4106-99a1-d55caed500bb.png
- C:/Users/liulele/AppData/Local/Temp/codex-clipboard-bdd9b36e-e71b-4010-9d56-ec5d451faf0f.png
- C:/Users/liulele/AppData/Local/Temp/codex-clipboard-f0d7946a-e0d1-4983-8c15-2e65491d832a.png

主项目仅只读：frontend/src/views/Assistant/AssistantView.vue、frontend/src/features/assistant/{useAssistantWorkbench.ts,ArtifactPanel.vue,EvidenceDrawer.vue,presentation.ts}；backend-java的host/{DeterministicTemplateDecisionProvider.java,AssistantObservationProjection.java}及research/{ResearchAgentToolProvider.java,JdbcResearchReadRepository.java}；现有I2-FIXTURE.sql。未变的长期规则/旧W1/165绿灯结论复用，不全文重读/重算冻结依赖。

请回答：
1. 按钮到底改变什么，成果切换/加载是否存在静态可证的阻塞或竞态；没有定位/滚动与数据读取失败分开。
2. metadata引用为何无原文指针，与source成果真实chunk引用分开；是否会沿用上游引用造成误导。
3. partial的实际触发逻辑。尤其retrieveChunks无条件添加FULL_CORPUS_COVERAGE_NOT_VERIFIED，不可把partial直接当成零条或失败。
4. 汉字分词与LIKE如何处理两种原主题，节点甲是否在B样本中，静态条件能否解释；不能把静态推断当该用户新运行的真实结果。
5. 列明明确体验问题、候选实现问题、目前不能确定的运行事实；提出最小修复建议及对应所有者（07前端、03检索、08宿主），不实施。

## 边界与停止

旧人工窗口已于18:03:22正常结束。确认本批既有收尾证据，勿复启、续窗、刷新证明或连接数据库；不重跑回归/浏览器套件、不新建研究Run、不抓取秘密配置、不使用私有AppServer或接管用户浏览器调试会话。失去现有内存运行的事实明确列缺口，不能伪造历史API结果。此次仅静态审阅与既有公开证据核对。

06不修改领域/宿主/前端代码或脚本，不改历史报告/hash，不编译、不测试、不部署。允许在原06的manual-reopen-20261001/artifact-review/新增一份06-artifact-review.md（已有则停止，不覆盖）；必要摘录/新增审查证据同目录，不创建通用测试框架。完成后在原会话用简洁中文回报并停止。

报告分列事实/推断/未验证，署名审查者06、总控独立审核者总控；模型要求GPT-6.1 Sol / High，实际模型及推理强度未独立核实。负责人暂不需要再操作，不宣称人工验收通过。
