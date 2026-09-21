# 知识图谱生产部署（2026-09-16）

## 入口与宝塔项目

- 访问入口：https://tupu.gmule.cn （HTTP 入口也可用）。DNS 指向新服务器 `154.201.83.250`。
- 静态项目：`tupu.gmule.cn`，网站根目录 `/www/wwwroot/tupu-app/frontend`。
- Java 项目：`tupu-backend`，Spring Boot 3.5.16，宝塔 JDK 17.0.8，运行用户 `www`，保活配置开启；仅监听 `127.0.0.1:8080`。
- 宝塔安装 Nginx 1.28 系列、MySQL 8.0.45、JDK 17.0.8。MySQL 宝塔镜像下载极慢，改由官方源取得同版本完整包并替换本次下载缓存，原宝塔安装脚本继续完成；未修改面板或插件代码。
- 数据库：`tupu_kg`，专用同名用户，仅本机访问；密码不写入本文。
- 旧服务器 `114.66.16.234` 的 `baota-mcp` 与新服务器的 `baota-mcp-154-201-83-250` 均保留且启用。本次部署仅操作新服务器。

## 目录与启动

- 后端：`/www/wwwroot/tupu-app/backend/tupu.jar`。
- 外部生产配置：`/www/wwwroot/tupu-app/backend/application-production.yml`，`www` 所有、权限 0600。保留原模型密钥加密主密钥，生产数据库独立生成密码；部署 jar 移除了本机 `application-local.yml`。
- 原始资料及存储：`/www/wwwroot/tupu-app/storage`；导出快照中的资料路径已转换为服务器路径，未修改本机业务库。
- 项目源码：`/www/wwwroot/tupu-app/source`，排除本机配置、依赖目录及构建缓存。
- 导入快照、部署包、文件清单、校验脚本：`/www/wwwroot/tupu-app/private`，目录权限 0700，位于公开网站根目录之外；本机另保留 `.local/tupu-deploy`，不入版本库。
- 应用日志：`/www/wwwlogs/java/springboot/tupu-backend.log`。
- 启动参数：`-Xms256m -Xmx1536m -Djava.awt.headless=true`，`--spring.profiles.active=production`，`--spring.config.additional-location=file:/www/wwwroot/tupu-app/backend/application-production.yml`。
- 启停、重启通过宝塔 Java 项目 `tupu-backend` 操作，勿另行启动重复进程。应用端口不对外放行。

## 网站与 HTTPS

- Nginx 主配置：`/www/server/panel/vhost/nginx/html_tupu.gmule.cn.conf`。
- 自定义规则：`/www/server/panel/vhost/rewrite/html_tupu.gmule.cn.conf`，包含 SPA 回退和 `/api/` 转发到 `127.0.0.1:8080`，代理超时 600 秒、上传上限 256 MB。
- 证书由宝塔申请，覆盖 `tupu.gmule.cn`，有效期至 **2026-12-14**。
- 当前 MCP 的通用伪静态及证书部署操作未适配 `html_` 配置文件前缀。已通过文件工具/终端完成配置并通过 `nginx -t`、实际 HTTPS 验证；没有使用浏览器。
- 证书路径：`/www/server/panel/vhost/cert/tupu.gmule.cn/`；私钥权限 0600。启用 HTTPS 前主配置备份为 `private/nginx-before-ssl.conf`。
- **续期自动部署尚未验证**。证书到期前需检查续期及实际 HTTPS；宝塔修改域名或根目录可能重写主配置，操作后复核 443 监听、证书路径与 `nginx -t`，不要仅凭 MCP 的“成功”判断生效。
- 用户已明确选择公开访问并接受当前程序无登录鉴权的风险；本次未增加网站密码。公开访问者可调用管理接口，包括删除资料、修改模型配置及消耗模型额度。

## 实际验收

- 部署前修复原文检索：SQL 在限制 80 条前优先同库，最终排序继续同库优先；解决存量教材挤掉当前图片原文的问题，未新增依赖。
- 后端 `package`：213/213，失败 0、错误 0、跳过 0；前端构建通过。Windows JDK 本地域套接字错误仅通过本次构建的临时目录参数使其自动回退 TCP，未修改测试断言或业务网络实现来规避测试。
- 上传包 136,663,981 字节，SHA-256：`8ed87e48a923e49e0350ed1d5a5d8459026614eebfd45f27c4d316bb9ddd7bd6`。
- 上线后复核：533 个文件 SHA-256、28 张表行数、2 份原始资料 SHA-256 全部一致。
- 生产数据：1,375 节点、2,376 关系、2 资料、2,828 原文分段；原任务 #285 仍 `COMPLETED`。
- 六个前端入口刷新返回正确首页；20 个 JS/CSS 资源内容与本机构建一致。
- 七项接口通过：健康检查（含数据库）、知识库、图谱、TCP 搜索建议、任务 #285、资料 #63 第 1 单元、模型档案。模型档案读取成功，原加密密钥可正常解密并脱敏返回。
- `/private/database.sql`、`/backend/application-production.yml` 均返回 404；HTTPS 证书验证通过。
- 保活设置开启后重启成功，进程与 8080 监听正常；未重跑已完成摄取任务，未执行真实付费模型调用。按用户要求不做浏览器操作，因此未声称完成浏览器视觉/交互验收。
- 日志仅见已有 Commons Logging 冲突提示，未见阻止启动的异常。

## 备份与后续更新

- 本次只向确认为空的新生产库导入；未删除或覆盖旧服务器数据。本机业务数据仍保留。
- 后续发版前先备份生产数据库和存储。不要直接重跑首次导入脚本覆盖已产生线上变更的数据库；脚本检查到非空会拒绝导入。
- 生产密钥、数据库凭据和含凭据的快照仅保留在私有路径，不复制到公开网站目录或提交到仓库。
