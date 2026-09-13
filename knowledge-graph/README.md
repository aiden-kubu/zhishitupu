# 课程/书籍知识图谱系统（Java + TailAdmin Vue 重构版）

2026-09-13 更新：已支持资料解析、AI 自动整理和复审入库、2D/3D 图谱、知识问答及新版 UI。**接手开发请优先阅读 [数据库恢复与最新启动步骤](../database/README.md)**；仓库已附脱敏开发快照与原资料。下方为早期阶段的环境说明，最新流程以上述接手文档为准。

## 目录

```
knowledge-graph/
├─ frontend/       # TailAdmin Vue 前端（Vue 3 + TS + Tailwind CSS 4）
└─ backend-java/   # Spring Boot 后端（Java 17 / Spring Boot 3.5.x / MySQL 8）
```

## 快速启动（开发）

1. **数据库**：本机 MySQL 8（127.0.0.1:3306，库 `knowledge_graph`）。后端首次启动会自动幂等建新表并从旧表迁移数据（旧表只读不动）。
2. **后端**（端口 8080）：
   ```
   cd backend-java
   mvnw.cmd spring-boot:run        # 或 java -jar target/*.jar
   ```
   需要 `KG_DB_PASSWORD`（本机默认见 `application-local.yml`，勿提交）。LLM/OCR 未配置时相应功能显示「未配置」，不影响图谱浏览。
3. **前端**（端口 5173，已代理 /api → 8080）：
   ```
   cd frontend
   npm install
   npm run dev
   ```
4. 访问 http://localhost:5173/ ，顶部搜索「TCP」验证搜索聚焦。

## 构建

- 前端：`npm run type-check` → `npm run build`（产物 `frontend/dist/`）
- 后端：`mvnw.cmd test` → `mvnw.cmd package`（可运行 JAR）

## 环境变量（§15）

`KG_DB_URL`、`KG_DB_USERNAME`、`KG_DB_PASSWORD`、`KG_STORAGE_ROOT`、`KG_LLM_BASE_URL`、`KG_LLM_API_KEY`、`KG_LLM_MODEL`、`KG_OCR_MODE`、`KG_OCR_DATA_PATH`。仓库只提交 `application-local.yml.example` 占位模板。

## 功能导航（V1 六模块）

图谱工作台（搜索/2D 图谱/节点详情/AI 面板）· 知识库 · 处理中心 · 审核中心 · 数据洞察 · 系统设置。
AI 问答、3D 图谱、文档解析流水线、审核入库等随阶段 C–G 交付。
