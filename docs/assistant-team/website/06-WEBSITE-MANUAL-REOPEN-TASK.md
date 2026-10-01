# 原06：重新开放人工验收窗口（正式执行）

日期：2026-10-01。负责人在总控明确说明旧窗口关闭后回复“开始吧我看看”，批准本次重新开放。执行者是原06「集成测试」，GPT-6.1 Sol / High，原工作树D:/知识图谱-worktrees/assistant-06-integration-test、分支codex/assistant-06-integration-test、HEAD86d1e1fcff6b7b492faec3d499837a42246df320。本单无阻塞门禁通过即执行，不再接令检查或向负责人二次申请。

## 范围与最短交付

只重开同一只读网站供负责人操作，不做离线回归、自动页面/业务套件、性能采样、EXPLAIN或额外异常探针。源码、SQL、数据库数据/权限/索引/迁移、超时预算、前端构建均不修改。不启动其它子任务/I2其它项/I3/W2/模型，不提交或部署。

旧165/165、旧六份汇集/144份构建及旧权限结论复用，只核对与启动安全直接相关的前像是否改变，不全仓扫描/全文重审。MySQL只用原8.4.11、UUID8c6cfcb3-ba49-11f1-bf64-005056c00001、127.0.0.1:33078、kg_assistant_i2_20260927_r01；原英文junction D:/kg-i2-env→D:/知识图谱-i2-env，原实例数据保留。宿主使用kg_i2_reader，loader仍锁定，原观察账号仅按既有流程只读观察及正常启停。保持connect3000/socket4000/statement2s/session1500，绝不使用过期身份或改旧期限。

## 允许文件与前像

- 原06新增docs/assistant-team/website/06-live-readonly/WebsiteLive-20261001T151755471/manual-reopen-20261001/**，当前路径不存在。新的受限身份/config放D:/知识图谱-i2-env/restricted-evidence/website/WebsiteLive-20261001T151755471/manual-reopen-20261001/**，不覆盖identity-04和旧证据/索引。
- 仅在新目录复用既有脚本，必要修改限本批路径、run/launch标识、正确UTC期限、人工窗口参数；不再搭建新的通用测试框架。旧脚本、probe源码/编译结果、classpath保留，不重新编译。
- 总控审核落点D:/知识图谱/docs/assistant-team/website/06-live-approvals/WebsiteLive-20261001T151755471-reopen.json，06不代写APPROVED。
- 复用脚本前像见06-WEBSITE-MANUAL-REOPEN-BASELINE.json；生产启动六份当前版本使用原06-WEBSITE-LIVE-MANIFEST.json。历史作者、失败、hash不倒改。

## 执行步骤

1. 确认当前旧宿主/实例已关闭、目标端口未占用；不强杀他人进程、不新初始化数据库。正常启动同一隔离实例。
2. 复用已修正驱动，只取得一轮新的真实身份/reader GRANTS/只读范围及既有零写入窗口基线，生成新证明（原60分钟有效期，不扩大）。范围仍为库7101、资料7201/7202、nodeIds空，宿主仅原21张完整SELECT表，两张既有敏感列权限表仍不进宿主白名单。
3. 立即把本轮脱敏identity-summary.json及真实证明路径/hash写入新公开目录，状态identity_evidence_ready，并在原聊天通知总控。不自批总控、不要等负责人重批；总控本次会在同一轮读取新材料完成纯增量审核/固定摘要。未获得本轮固定摘要前不启动宿主。
4. 收到总控固定摘要后，复用已有运行产物启动一次AssistantReadAcceptanceApplication，只绑定127.0.0.1:18088、同一获审静态子目录。只允许一轮页面/能力可达检查确认实际网址，不代负责人跑研究。若失败保留真实安全错误、正常收尾并报告，不现场改代码/反复补脚本。
5. 页面确实可用后立即写ready.json：实际URL、readyAt（Asia/Shanghai/UTC明确）、humanWindowUntil、proofValidUntil、两个进程及启动结果。提供“I1 授权验收库”、A.txt/B.txt和现有样本可确定的检索词，不额外检索造样。
6. 本次完整人工窗口30分钟，从页面可用并交付网址时起算，不从材料核验时起算；开始前证明剩余应足以覆盖该窗口，若不足不得把窗口缩成几分钟后宣称可验收，报告总控。不得超出新证明有效期或自动续期。负责人说“验收完成/停止”后提前正常关闭本批进程；到期前至少在原会话提示一次并安全收尾。06守候期间不执行其它测试。

网站就绪后负责人依06-WEBSITE-MANUAL-ACCEPTANCE.md的六项页面操作亲自反馈。总控不代做06启停/环境工作，06不修改领域/宿主/前端实现。异常以安全停点为准，普通功能问题交回对应owner，不影响安全的空结果如实提示。完成或安全停止后留简短结果，不自动扩大范围。
