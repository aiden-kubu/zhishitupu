# 01 H03 说明增量审核

2026-09-30；总控审核。本轮首次阅读原 01 新报告，定向核对六份已匹配前像的构造/三工具调用边界、documentData 与文件存在性 helper，未重审 W1、未运行代码。

报告 `D:/知识图谱-worktrees/assistant-01-ingestion/docs/assistant-team/reports/01-website-read-wiring.md`，raw SHA-256 `a0e66cbd9191aa8bd4f6a84e8395b52938ab8c98415bf61923f326f0e7f2c385`。报告作者/静态分析者原 01；源码修改、编译、测试、真实环境执行均无；桌面本次观察 GPT-6.1 Sol 高，实际服务端参数未独立核实。01 已最终回报并停止。

静态结论支持 08 按既有真实构造器最小接线：真实 SettingsService/SecretCipher/AppProperties/JdbcSourceLocator 仅为私有组合工厂内部对象，JdbcClient 全部为独立 reader，最后仅返回三项资料工具。普通 new 不处理 Spring @Value；内部 cipher 的临时内存随机种子不作为配置密钥/数据库加密凭据，不记录或对外返回。不扫描旧服务、不暴露通用 bean/设置方法、不注册其它七项工具。本说明无需领域契约修改，08 不获准修改 01 实现。

补充边界：list_documents/get_document 会对数据库 storage_path 做 Files.isRegularFile 元数据检查，不是“完全无文件系统访问”。可信路径不来自请求、dataJson 不输出该路径；UNC/挂载、真实文件权限及底层随机数行为本轮未验证，不宣称纯数据库读取或无网络。08 应保持领域现有语义和出站脱敏，不用替代仓储跳过此逻辑。

H03 的静态构造疑问解除，实际装配、严格工具白名单、关闭零连接/无秘密读取、旧路由不可达仍由 08 本轮离线测试。独立 reader 的实际身份/权限、文件挂载、真实 SQL 与启动/网站联调由未来明确批次验证，不因此标 I2/I3 完成。

允许按已批准网站阶段向既有 08 发宿主/API 正式实现指令；01 待命，其他任务不并行。
