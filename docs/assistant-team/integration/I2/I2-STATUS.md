# I2 环境准备阻断记录

当前状态（2026-09-27晚更新）：具名本地管理通道已获授权并成功；原实例SQL身份已核验、验收库和分离账号及样本已建立。I2基础35项、7个数据库变体、3项预算验证通过；大样本2项中1项因质量洞察度数排名SQL超时而出错，更新验收库优化器统计后同预算复测仍复现。原154项回归重新通过；11个reader窗口零业务写入证据一致。现因性能问题停止推进，隔离实例已正常停止；I2未完成、未验收、未进入I3。

请以最新[I2中间报告](D:/知识图谱/docs/assistant-team/integration/I2/I2-REPORT.md)及[总控复核证据](D:/知识图谱/docs/assistant-team/integration/I2/I2-CHECKPOINT-VERIFICATION.json)为准。以下为16:23及更早的历史记录，“尚未建库”等描述不是当前状态。

## 历史：16:23管理通道授权之前

## 本轮英文联接验证结果

- 已创建且解析核实：`D:/kg-i2-env`（Junction）→ `D:/知识图谱-i2-env`。
- 未移动、复制或删除原目录及mysql-data，未再次初始化。原目录与别名读取的auto.cnf hash相同；磁盘UUID仍为`8c6cfcb3-ba49-11f1-bf64-005056c00001`。
- 仅新增I2环境专用ASCII配置`D:/知识图谱-i2-env/mysql-i2-ascii.ini`，路径经联接访问同一原实例。没有更改既有主项目配置或I1文件。
- MySQL二进制及启动日志均显示8.4.11；2026-09-27 16:20:43（UTC+8）日志出现ready for connections，实际监听`127.0.0.1:33078`，监听PID16052。
- I2脚本最初误把MySQL启动父进程PID当作必须的监听PID，因而在发出SQL前拦截。随后只读核实子进程的可执行文件、完整命令、父PID、pid-file和监听地址均对应本次启动。已修正本轮I2脚本的父子进程核验及停止结果提示，通过PowerShell语法检查；没有再次启动。
- 对这个已核实的隔离监听发起一次身份只读查询，在认证前被拒：`ERROR 1130 (HY000): Host '127.0.0.1' is not allowed to connect to this MySQL server`。因此**尚未通过SQL读取运行时@@server_uuid**；不能将磁盘UUID核验冒充SQL身份核验。

## 当前新阻断及安全收尾

本轮引导配置启用了`skip-name-resolve=ON`，而初始化日志只确认创建了`root@localhost`；脚本却通过TCP的127.0.0.1登录。当前证据与MySQL官方说明中的这一账号主机匹配限制一致。这是I2引导连接配置的问题，不是已验收领域JDBC契约问题，不应修改业务代码或生产表。

由于管理认证被拒，不能执行SQL SHUTDOWN。已再次核实身份后仅终止本轮隔离子进程PID16052；父进程28168此前已退出。现在33078无监听、隔离MySQL进程不存在；其他既有MySQL进程未操作。数据目录保留，后续启动可能执行InnoDB崩溃恢复，不重新初始化。

- 未建立成功SQL会话；未建验收库、loader/reader；未执行迁移或样本装载。
- 未运行I2 JDBC测试或重跑154项I1测试；没有新增测试通过数。
- 再核对499个I2操作前既有文件：499/499内容一致，包括主项目配置与I1冻结交付物。
- 未接真实模型、API、前端、部署、W2或I3；未启动子任务。

## 建议的下一项最小调整（尚未执行）

仅为本隔离实例启用具名本地共享内存管理连接，名称拟为`KG_I2_33078_8c6cfcb3`，供管理员使用；loader/reader和实际JDBC验证仍只走127.0.0.1:33078。先通过管理通道读取运行时版本、UUID、datadir，完全匹配后才设置管理员随机口令、创建原批准的验收库与分离账号。管理通道仅用于I2生命周期，不新增网络端口、不跳过权限检查、不更改其他实例。不启用`skip-grant-tables`。

该调整属于新增本地管理连接方式，未包含在本次“仅目录联接”调整中，等待负责人确认后再实施。不自动改变`skip-name-resolve`或重建实例。

依据：[MySQL 8.4系统变量：skip_name_resolve/shared_memory](https://dev.mysql.com/doc/refman/8.4/en/server-system-variables.html)、[官方连接选项：MEMORY与独立共享内存名称](https://dev.mysql.com/doc/refman/8.4/en/connection-options.html)。

本轮机器证据：[I2-ALIAS-START.json](D:/知识图谱/docs/assistant-team/integration/I2/I2-ALIAS-START.json)；原始启动日志：[alias-startup-stderr.txt](D:/知识图谱-i2-env/alias-startup-stderr.txt)。以下保留英文联接创建前的历史状态，非当前结论。

---

## 历史记录：英文联接授权之前

## 已完成

- I1启动前冻结校验：62个Java文件一致，98份重复副本一致，四份冻结清单105条匹配。没有重跑154项测试；仅读取既有结果。
- I2操作前保存499个主项目既有文件hash；阻断时再次核对499/499未变，包括全部I1交付物。
- 指定端口127.0.0.1:33078起初空闲；原验收目录起初不存在。
- 从MySQL官方CDN获取8.4.11 Windows ZIP，MD5与官方页面一致，mysqld.exe数字签名有效（Oracle America, Inc.）。
- 独立程序解压到D:/知识图谱-i2-env；未使用本机原有MySQL8.0。
- 已初始化D:/知识图谱-i2-env/mysql-data；UUID为8c6cfcb3-ba49-11f1-bf64-005056c00001。
- 首次初始化因UTF-8配置中的中文路径解析失败；将本轮新建配置按文本无损转换至Windows ACP936后，初始化成功。未修改任何既有项目配置。

## 阻断证据

MySQL正常启动仍失败。启动诊断将可执行文件路径显示为D:/，并尝试D:/share/english/errmsg.sys及D:/data，而非指定验收路径。隐藏启动、PowerShell包装和直接执行均未形成正常监听。

目前只能确认“正常启动的中文路径处理异常”，尚不能把英文别名能解决当作已验证事实。失败日志在D:/知识图谱-i2-env/startup-stderr.txt，初始化日志在D:/知识图谱-i2-env/mysql-error.log。

## 当前安全状态

- 未连接任何数据库实例。
- 未创建验收库、loader/reader账号；未执行V1～V11或装载样本。
- 未运行I2 JDBC验证，没有可报告的I2测试通过数量。
- 指定端口无监听，本轮MySQL进程已退出。
- 未接真实模型、生产API、前端、部署或W2；未启动I3。
- 下载包、程序和初始化目录均保留；没有删除/移动用户数据。

## 待确认的最小调整

拟增加目录联接D:/kg-i2-env → D:/知识图谱-i2-env，让可执行文件与配置可通过纯英文别名访问。实际数据仍位于原批准目录，版本8.4.11、端口33078和目标库名不变。

目前没有创建该联接；等待负责人确认。确认后先验证联接目标与实例UUID，再尝试同一实例启动；仍失败则继续报告，不改用现有数据库。

机器证据：[I2-ENVIRONMENT-BLOCK.json](D:/知识图谱/docs/assistant-team/integration/I2/I2-ENVIRONMENT-BLOCK.json)。
