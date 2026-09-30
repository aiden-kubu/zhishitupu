# 06 I2 环境与证据交接卡（只读资料，不是操作授权）

## 固定身份和当前停点

MySQL 8.4.11；UUID 8c6cfcb3-ba49-11f1-bf64-005056c00001；127.0.0.1:33078；库 kg_assistant_i2_20260927_r01。
实体 D:/知识图谱-i2-env；联接 D:/kg-i2-env；原 datadir mysql-data。不能重建、移动、复制、初始化或用其他本机 MySQL 代替。
7901：10000 节点/30000 边/2000 资料/20000 chunk；连接 3000ms、socket 4000ms、statement 2s、session 1500ms；既定组合 p95≤3000ms，20 单线程＋20 四并发，各组排序第 19 个，不丢慢值。
R3 历史收尾记录正常停止、残留进程/监听为空；这不是本轮实时探测。首次入职不启动或连接。

## 账号与凭据

reader=kg_i2_reader@127.0.0.1，原 23 项 SELECT 表/列授权、无角色。loader 原记录 locked=Y。管理身份只用于获批运行器身份/快照/日志/正常停库；测试进程只获 reader，禁止任意 root SQL。
凭据由原环境 DPAPI 机制保管（credentials.dpapi.json），不复制到工作树/报告/聊天，不读取或解密用于入职。未来若用户上下文无法使用，不重设密码、不扩大 ACL/权限、不新建账号，先报告。不能把本机 root 的任意凭据当该隔离实例凭据。

## 执行入口分类

以下主项目 docs/assistant-team/integration/I2 路径均仅供阅读，入职全部禁止执行：

| 入口 | 风险和用途 |
| --- | --- |
| Invoke-I2Environment.ps1、Start-I2Server.ps1 | 环境管理/服务启动，不是纯文件检查 |
| Build-I2Fixture.ps1、Invoke-I2BaseVerification.ps1、Invoke-I2Scale.ps1、Invoke-I2Variants.ps1 | 涉及样本生成/装载、规模、故障状态切换或验证；必须逐项阅读，不能当通用只读启动器 |
| Invoke-I2Tests.ps1；R1/R2/R3 下 Invoke-*Tests.ps1 | 启动 Maven 测试，可能使用真实 reader；本轮禁止 |
| R1/R2/R3 下 Invoke-*Verification.ps1 | 汇集保护、测试、服务管理、管理快照与日志；旧版本有各自场景/状态前置，禁止直接续跑 |
| Verify-I1.ps1、Verify-I2Checkpoint.ps1、R1/R2/R3 Verify-*Baseline.ps1 | 检查旧封包前提；本轮治理变更需新 overlay，不可为了通过修改旧 expected hash |
| .local/assistant-team/* | 历史控制入口，禁止执行 |

## 后续获批验证流程

前像/后像和治理增量确认 → 原证据受限保全 → 环境/权限/规模/预算身份门禁 → 独立运行 ID → reader 静态窗口前快照 → 原样例/整批计划 → 窗口后数据/DDL/binlog/权限/活跃查询检查 → 正常停库 → 结果索引/失败缺口 → 停止交总控审核。

loader 装载/故障注入/并发写入/销毁都需要单独批准并放在 reader 静态窗口之外。本次没有该授权。预算探针预期 3024 与非预期业务超时分开记录。

## 接手证据和风险

R3 FINAL-INDEX 指向 77 份包内文件、31 份本地 XML 和累计 general log。旧 hash 不改；未来编译 clean、日志追加前先按批准清单保全。原 XML 可能含系统属性，原件受限保存，分享材料脱敏；不要把密钥、环境凭据或整个 mysql-data 复制入仓库。
R3 控制台中文名称转录问题以原 UTF-8 XML 和 XML-TRANSCRIPTION-CHECK 为准；这是后续 06 测试运行器维护项，本轮不修封包脚本、不重跑。
R3 零写入证明的是八个 reader 静态窗口，不是历史样本装载从未写过数据；启动 binlog 轮换不与上一轮位置简单比较。

当前 03 outgoingEdges 超时交原 03 分析/修复；06 只理解失败证据，等待审核后的复验任务。并发写入一致性、物理长原文链路及其他 I2 遗留、I3/W2 未自动批准。
