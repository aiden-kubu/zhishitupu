# 原06：本地网站查看窗口（正式执行）

授权来源：负责人2026-10-01在总控会话提出“我现在要登录本地端查看”。仅重开已验收的本机只读预览，不再进行验收批次。执行者为原06「集成测试」，工作树D:/知识图谱-worktrees/assistant-06-integration-test，分支codex/assistant-06-integration-test，HEAD86d1e1fcff6b7b492faec3d499837a42246df320。模型要求GPT-6.1 Sol / High，负责人此前确认，工具独立回读未核实。

## 范围与允许操作

必要门禁无异常直接执行，不另设接令审批。复用已有编译运行产物及下述144文件静态站点，不编译、不构建、不运行测试、业务SELECT、EXPLAIN或性能采样；只允许既有启动身份/权限/零写入基线观察、一轮页面与capabilities可达检查及正常收尾。用户在网站中操作已批准的只读能力不是自动测试。

- MySQL8.4.11，UUID8c6cfcb3-ba49-11f1-bf64-005056c00001，127.0.0.1:33078，kg_assistant_i2_20260927_r01；使用原联接D:/kg-i2-env及原实例，不初始化或使用其它数据库。
- 宿主仍为AssistantReadAcceptanceApplication、kg_i2_reader、role NONE、原21表白名单和原权限，loader保持锁定。范围库7101、资料7201/7202、nodeIds空；connect3000/socket4000/statement2s/session1500保持不变。仅127.0.0.1:18088监听。
- 静态站点D:/知识图谱/frontend/dist/06-artifact-acceptance-ArtifactAcceptance-20261001T210357779/site；当前前端四个已汇集后像依06-ARTIFACT-ACCEPTANCE-MANIFEST.json及已验收Correction证据复用，不重新汇集源码。
- 在原06下新增docs/assistant-team/website/06-local-view/<唯一runId>/**，受限文件新增D:/知识图谱-i2-env/restricted-evidence/website/06-local-view/<同一runId>/**。路径占用即换未占用runId，绝不覆盖历史。仅允许本批脚本的路径、runId、证明引用及30分钟查看窗口参数调整，不另建通用测试框架。
- 总控新审批文件落D:/知识图谱/docs/assistant-team/website/06-local-view-approvals/<runId>.json。旧审批及证明全部过期且已结束，不复用APPROVED。原证明有效期60分钟不扩大。

## 可复用的已审入口

原目录为docs/assistant-team/website/06-artifact-acceptance/Correction-20261001T220000115，原文件只读、不改。2026-10-01本轮只核对启动相关前像，不重复全文审查：

| 文件 | raw SHA-256 |
| --- | --- |
| prepare-environment.ps1 | a97ff63f80115d63fe3f4bd91eda1bd0326bb5581c97df662a924a8a86c0fcfc |
| database-guard.ps1 | 66531129d5c6550016bdaf0267746be0ed366652ba4a68e359b14c8b5d633f26 |
| launch-and-guard.py | adb55a552d9e166d6dca2b55b03413575ed76698e732339b3b28d663f5e6ca30 |
| start-database-once.ps1 | cd042039a306b4bfdb86d3f24c67d4d4615c726559b2cbb08d44c103acb97286 |
| start-host-once.ps1 | eef32359a9ded4c1afe63d94145b64f9cb25a78fa037abd7f2a4a530bea2b145 |
| confirm-ready-once.ps1 | 80377aeaa95f5275736a83bd800e1671a43189f633e45f0449a84ed00869f6d2 |
| derive-reviewed-create-new.ps1 | 081f7f63528f2aad4384e9e67c2dc133f1becc0460bfaf06fbe742acfc0e1e66 |
| startup-gate.json | 6f1df3f519967fc7b364d90ff5ad1bc1f66a979ce1fe124fd71553586cd146f0 |
| gate-site.json | f529df4ec3ea23ad7607ffc60179e9080455ec33989543e165fc1d556c449e73 |

## 最短交付步骤

1. 仅核对上述启动相关前像、运行产物及端口；发现未知修改、实例/权限不符或他人进程占用即停报，不强杀、不改账号。历史绿灯直接引用，不重跑验收。
2. 正常启动原隔离实例一次；沿用已审guard取得新身份和静态基线，输出脱敏identity-summary.json和真实受限文件路径/hash。通知总控进行同一批内部增量审核，不要求负责人重复批准。本轮总控审批固定新config/evidence摘要后再启动宿主，06不自批。
3. 私密配置只能经已修正的内存派生/CreateNew及当前用户ACL落盘。禁止把密码或含密码补丁放入进程参数、公共工具输入/输出、日志或本任务文件。上轮已知reader进程参数暴露风险继续留档，本轮没有口令轮换授权，不宣称风险已修复，也不得使用生产凭据。新暴露或安全异常立即停止并正常收尾。
4. 用既有产物启动宿主一次。只检查GET /assistant及capabilities可达，不自动发起Run或原文重读。就绪立即交付URL及ready.json，注明readyAt、humanWindowUntil、proofValidUntil、实际进程；总控马上告知负责人，不等窗口结束再汇报。
5. 页面可用后提供完整30分钟查看窗口，限新证明有效期内，不自动续期。在原会话到期前提醒；负责人说“停止/看完了”可提前正常关闭。守候期间不做其它测试；到期正常关闭本批宿主/实例并记录最小零写入/无残留结果。普通已知功能缺口不阻断页面查看。

## 展示边界与停止条件

当前是isolated_local_single_owner、合成A.txt/B.txt资料、关键词检索的只读预览，不是网站账号登录、多用户鉴权或真实模型语义问答。旧Run内存已丢失，不恢复/伪造旧成果。研究get_neighbors错误与全局健康标记问题沿用已知结论，不在本轮修复。

不修改领域、公共核心、宿主、前端、SQL、数据、索引、迁移、权限、口令、预算、历史hash或证据；不汇集新成果，不启动其它子任务、不进入剩余I2/I3/W2，不提交或部署。安全/前像/身份异常立即停止并留最小证据，普通功能问题记录后继续独立安全查看能力。

报告只列本轮06启停和可达事实、总控新身份摘要审核、未进行测试/修复、实际模型未核实。无需再生成大型归档套件或全历史重审。
