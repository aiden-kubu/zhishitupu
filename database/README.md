# 接手开发与数据库恢复

## 本次交付

`dev-snapshot-2026-09-14.zip` 是 2026-09-14 的一致性事务快照，包含：

- `database.sql`：25 张业务表的结构与数据，MySQL 8 格式。
- `storage/documents/`：数据库引用的原始 PDF/ZIP，保留原存储文件名。
- `manifest.json`：每张表的行数、原文件名、资料 SHA-256。

数据库含 10 个知识库、619 个节点、852 条关系、2828 个文本片段及 2 份资料。任务 #63 已完成；任务 #938（`textbook.zip`）因自动分类识别出超过 12 个主题而停在 FAILED，原始文件和解析结果保留。候选表还保留旧开发测试残留，统计接口会排除不存在的任务，不应把候选总行数视为当前待复审数。

模型 API Key 已删除，模型档案默认停用；秘密设置、MySQL 账户/授权、数据库密码和本机加密主密钥均不包含在压缩包中。保留的模型名称与视觉开关只是原配置记录，不代表已经验证该模型支持视觉。

压缩包 SHA-256 见 `SHA256SUMS`。本次已在独立空库恢复，并核验所有表行数、模型密钥清除、节点关系和原文证据引用完整性。

## 1. 准备环境与解压

需要 JDK 17、Node.js 20.19+ 或 22.12+、MySQL 8，以及 MySQL 命令行客户端。Maven 使用后端自带的 Wrapper。

从仓库根目录在 PowerShell 执行：

```powershell
Expand-Archive -LiteralPath database/dev-snapshot-2026-09-14.zip -DestinationPath database/extracted
Get-FileHash database/dev-snapshot-2026-09-14.zip -Algorithm SHA256
```

Linux/macOS 可用 `unzip database/dev-snapshot-2026-09-14.zip -d database/extracted`。

## 2. 导入到一个全新的数据库

不要覆盖正在使用的数据库。以下库名供新建开发环境使用；若已存在，请换一个新名字。

在解压目录打开终端，运行 `mysql -h 127.0.0.1 -P 3306 -u root -p`，输入自己的 MySQL 管理密码，然后执行：

```sql
CREATE DATABASE knowledge_graph_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE knowledge_graph_dev;
SOURCE database.sql;

-- 改成你电脑上解压后的 storage 绝对路径，Windows 也用正斜杠。
SET @storage_root = 'E:/zhishitupu/database/extracted/storage';
UPDATE documents SET storage_path = CONCAT(@storage_root, '/documents/', stored_name);

SELECT COUNT(*) AS nodes FROM knowledge_nodes; -- 619
SELECT COUNT(*) AS edges FROM knowledge_edges; -- 852
SELECT COUNT(*) AS libraries FROM knowledge_libraries; -- 10
```

SQL 不含 DROP TABLE，不创建系统用户。需要导入到新空库。新建项目数据库账号并授予该开发库权限，或使用你已有的开发账号。

## 3. 配置和启动后端

复制 `knowledge-graph/backend-java/src/main/resources/application-local.yml.example` 为同目录下的 `application-local.yml`，修改数据库地址、库名、账号、密码和文件存储路径。该文件被 Git 忽略。

模板跳过只针对旧 PHP 表的 V2 迁移，使用 V1/V3/V4/V5/V6/V7/V8；适用于本快照。不要在没有旧 PHP 表的数据库上使用 V2。快照包含初始化标记，启动不会重新插入 TCP/UDP 演示知识。

```powershell
cd knowledge-graph/backend-java
.\mvnw.cmd package
.\mvnw.cmd spring-boot:run
```

Linux/macOS 使用 `./mvnw`。默认后端端口 8080。

## 4. 启动前端

另开终端：

```powershell
cd knowledge-graph/frontend
npm ci
npm run dev
```

访问 http://127.0.0.1:5173 。检查知识库 10 个、数据洞察 619 节点/852 关系/待复审 0，处理中心 #63 已完成、#938 失败且保留原资料，AI 复审可展开原文证据。

在系统设置编辑模型，填写自己的 API Key，确认服务商支持的能力并启用，然后测试连接。无需重新识别已有 PDF。后续新上传仍按 AI 整理→AI 复审→自动入库运行。

## 开发与维护

- 根目录 `start-dev.cmd` / `stop-dev.cmd` 是原开发机快捷入口，依赖未提交的 `.local/runtime.json`，其他电脑先按上述步骤启动。
- 验证命令：前端 `npm run build`；后端 `mvnw.cmd test`。本次代码前端 type-check/构建通过，后端 193 项测试通过。
- 后端改动后需重新构建/启动，前端开发服务支持热更新。
- 数据库引用的 PDF/ZIP 已在快照中各保存一份；未被数据库引用的本机孤儿文件没有提交。
- 数据库恢复用过的 `.local` 验证文件、依赖、构建产物及机器配置不在本次交付中。
