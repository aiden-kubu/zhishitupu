# 原 01 定向说明：网站只读接线 H03

2026-09-30；本轮为负责人已批准的网站接线范围内的领域所有者说明，正式执行，不另设无阻塞接令审批。只进行静态分析与一份新报告，不授权领域开发或真实环境操作。07 完成当前澄清后，原 01 唯一执行；02～06、08、09 待命。

## 身份与范围

原桌面任务「智能助手 01｜资料建设｜W0 已汇报·待命」，工作树 `D:/知识图谱-worktrees/assistant-01-ingestion`，分支 `codex/assistant-01-ingestion`，HEAD `bcd3b60ea4e6e18daad53f31787028a0f69bc6cf`。所有命令显式指定自己 workdir，主项目及其它树只读。要求 GPT-6.1 Sol / High；记录本次桌面按钮观察与服务端参数不可独立回读的区别。

复用未变长期规则及 W1 结论，不重新全文审核历史交付。只读 08 的 `docs/assistant-team/website/08-stage1/HOST-WIRING.md` 中 H03 及资料接线相关段落（位于 assistant-08-host-api 工作树）。总控已经确认 08 树现有 66 个公共/领域 Java 源码与测试和主项目规范化内容一致，无需为本说明同步源代码。

以下六份相关文件在主项目与原 01 树的规范化 SHA-256 一致；规范为 UTF-8 解码、CRLF 转 LF、保留结尾换行、不 trim：

| backend-java/src/main/java/com/knowledgegraph/ 下相对路径 | SHA-256 |
| --- | --- |
| assistant/ingestion/JdbcIngestionReadRepository.java | 715af87d60c765152628c81cd4c6a6bbf0a1f2b6dc985e2fe58a25696a7c2f43 |
| assistant/ingestion/IngestionAgentToolProvider.java | cc68eee0ba643ec585b69b9620e300c8025d775af7bc4065245dee9688c51f67 |
| assistant/ingestion/IngestionReadRepository.java | 5006737f5c86712ec06602c8170f43100ea3a95113c116b8ffe32ffa0c812fe5 |
| settings/SettingsService.java | 3f66c8affa0dd716439e07c8e2e17a6266d38846612d8d8dc50e8aa02f07c63c |
| settings/SecretCipher.java | 851fda7a9733e2843292ca828a7267488a5db340a507c0d8b4b9e1dc4046c848 |
| config/AppProperties.java | 30289e26829c9aac26acde38c33bf2d2e21c116a923745f6f19e18e3d36bee48 |

## 需要 01 回答的问题

网站首版只暴露 `ingestion.list_libraries`、`ingestion.list_documents`、`ingestion.get_document`。现有 JdbcIngestionReadRepository 构造器持有 SettingsService，后者拥有非本批所需的密钥/模型/写方法。请依据实际调用链说明：

1. 三项读取及构造过程是否触及 SettingsService、密钥读取/解密、网络、文件或业务写入；列真实调用边界，不把静态分析当运行证明。
2. 不扫描旧业务 Spring 服务、不导入旧控制器、只用独立 reader JdbcClient、只注册这三项工具时，能否使用现有真实构造器及真实内部依赖完成支持的最小接线？内部对象不得暴露为 API 或可任意调用的通用宿主工具。
3. 如可行，给 08 一份最小依赖创建/封装说明，明确无需调用的方法、凭据隔离、工具白名单与必须由 08 离线测试的安全断言。不使用 null、mock/fake、反射字段赋值、替代领域仓储作为正式接线方案；测试中的 mock 与正式对象分列。
4. 如果现有契约确实无法做到安全接线，只列最小领域变更候选及影响，停止等待总控和负责人决定；本轮不要改构造器、SQL、领域测试或公共契约。

## 唯一允许写入与停点

只允许新增自己树 `docs/assistant-team/reports/01-website-read-wiring.md`，总控检查时该路径不存在；若已占用或前像变化，停止受影响操作，保留既有修改。报告用 apply_patch，简短区分已证静态事实、建议与未验证事项，列实际作者/执行者/模型实况。

不编译、不运行测试、不连接数据库/模型/真实 API，不读取配置文件中的秘密或真实账号，不改源码、历史报告、共享依赖或主项目，不提交、部署、新建任务、自动推进后续阶段。必要门禁通过直接完成上述分析和报告；完成最终回报后停止。总控审核后才向 08 发接线实现指令。
