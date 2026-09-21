-- =====================================================================
-- V1__schema.sql — 知识图谱 Java 后端新表结构（§11 数据模型）
-- 全部为新建表；与旧 PHP 后端遗留表（nodes/edges/extractions/rules/settings/users）同库共存。
-- 可重复执行：全部使用 CREATE TABLE IF NOT EXISTS。
-- 注意：documents / document_units / document_chunks / ingestion_jobs 四表
--       自阶段 D 起由 V5__ingestion.sql 统一定义（含外键），
--       旧结构 documents 已按用户决策归档为 documents_legacy。
-- =====================================================================

-- 11.1 知识库（课程/书籍/主题）
CREATE TABLE IF NOT EXISTS knowledge_libraries (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(200) NOT NULL,
  type        VARCHAR(20)  NOT NULL DEFAULT 'topic' COMMENT 'course|book|topic',
  description TEXT         NULL,
  status      VARCHAR(20)  NOT NULL DEFAULT 'active',
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_libraries_name (name),
  KEY idx_libraries_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库/课程/书籍';

-- 11.1 知识节点
CREATE TABLE IF NOT EXISTS knowledge_nodes (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  canonical_name  VARCHAR(200) NOT NULL,
  name_en         VARCHAR(200) NULL,
  node_type       VARCHAR(20)  NOT NULL DEFAULT 'other'
                  COMMENT 'course,chapter,knowledge,concept,method,application,other',
  definition      TEXT         NULL,
  properties_json JSON         NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'active',
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_nodes_name (canonical_name),
  KEY idx_nodes_type (node_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识节点';

-- 11.1 节点别名
CREATE TABLE IF NOT EXISTS node_aliases (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  node_id          BIGINT       NOT NULL,
  alias            VARCHAR(200) NOT NULL,
  normalized_alias VARCHAR(200) NOT NULL,
  language         VARCHAR(10)  NOT NULL DEFAULT 'zh',
  PRIMARY KEY (id),
  UNIQUE KEY uk_aliases_node_normalized (node_id, normalized_alias),
  KEY idx_aliases_normalized (normalized_alias),
  CONSTRAINT fk_aliases_node FOREIGN KEY (node_id) REFERENCES knowledge_nodes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节点别名';

-- 11.1 知识库-节点关联（一个节点可属于多个知识库）
CREATE TABLE IF NOT EXISTS library_nodes (
  library_id    BIGINT       NOT NULL,
  node_id       BIGINT       NOT NULL,
  chapter_label VARCHAR(200) NULL,
  sort_order    INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (library_id, node_id),
  KEY idx_library_nodes_node (node_id),
  CONSTRAINT fk_library_nodes_library FOREIGN KEY (library_id) REFERENCES knowledge_libraries (id) ON DELETE CASCADE,
  CONSTRAINT fk_library_nodes_node    FOREIGN KEY (node_id)    REFERENCES knowledge_nodes (id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库-节点关联';

-- 11.1 知识关系
CREATE TABLE IF NOT EXISTS knowledge_edges (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  source_node_id  BIGINT        NOT NULL,
  target_node_id  BIGINT        NOT NULL,
  relation_type   VARCHAR(100)  NOT NULL,
  weight          DOUBLE        NOT NULL DEFAULT 1.0,
  properties_json JSON          NULL,
  status          VARCHAR(20)   NOT NULL DEFAULT 'active',
  created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_edges_source_target_relation (source_node_id, target_node_id, relation_type),
  KEY idx_edges_target (target_node_id),
  CONSTRAINT fk_edges_source FOREIGN KEY (source_node_id) REFERENCES knowledge_nodes (id) ON DELETE CASCADE,
  CONSTRAINT fk_edges_target FOREIGN KEY (target_node_id) REFERENCES knowledge_nodes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识关系';

-- 11.1 节点证据
CREATE TABLE IF NOT EXISTS node_evidence (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  node_id       BIGINT        NOT NULL,
  chunk_id      BIGINT        NULL,
  evidence_text TEXT          NULL,
  confidence    DOUBLE        NULL,
  created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_node_evidence_node (node_id),
  KEY idx_node_evidence_chunk (chunk_id),
  -- chunk_id 外键待 V5 建表后由阶段 E 补齐（避免迁移顺序依赖），以下同
  CONSTRAINT fk_node_evidence_node  FOREIGN KEY (node_id)  REFERENCES knowledge_nodes  (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节点证据';

-- 11.1 关系证据
CREATE TABLE IF NOT EXISTS edge_evidence (
  id            BIGINT     NOT NULL AUTO_INCREMENT,
  edge_id       BIGINT     NOT NULL,
  chunk_id      BIGINT     NULL,
  evidence_text TEXT       NULL,
  confidence    DOUBLE     NULL,
  created_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_edge_evidence_edge (edge_id),
  KEY idx_edge_evidence_chunk (chunk_id),
  CONSTRAINT fk_edge_evidence_edge  FOREIGN KEY (edge_id)  REFERENCES knowledge_edges  (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关系证据';

-- 11.2 摄取任务（状态枚举见 §8.4，字面值由应用层写入）
-- 11.2 处理任务（阶段 D 起由 V5__ingestion.sql 定义，含外键）

-- 11.2 候选实体（审核状态见 §8.8：PENDING/ACCEPTED/REJECTED/MERGE/EDITED）
CREATE TABLE IF NOT EXISTS entity_candidates (
  id                       BIGINT        NOT NULL AUTO_INCREMENT,
  job_id                   BIGINT        NOT NULL,
  temp_key                 VARCHAR(50)   NULL,
  name                     VARCHAR(200)  NULL,
  aliases_json             JSON          NULL,
  node_type                VARCHAR(20)   NULL,
  definition               TEXT          NULL,
  confidence               DOUBLE        NULL,
  evidence_chunk_ids_json  JSON          NULL,
  matched_node_id          BIGINT        NULL,
  review_status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  edited_payload_json      JSON          NULL,
  created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_entity_candidates_job (job_id),
  KEY idx_entity_candidates_review (review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选实体';

-- 11.2 候选关系（审核状态见 §8.8：PENDING/ACCEPTED/REJECTED/EDITED）
CREATE TABLE IF NOT EXISTS relation_candidates (
  id                       BIGINT        NOT NULL AUTO_INCREMENT,
  job_id                   BIGINT        NOT NULL,
  source_temp_key          VARCHAR(50)   NULL,
  target_temp_key          VARCHAR(50)   NULL,
  relation_type            VARCHAR(100)  NULL,
  confidence               DOUBLE        NULL,
  evidence_chunk_ids_json  JSON          NULL,
  matched_edge_id          BIGINT        NULL,
  review_status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  edited_payload_json      JSON          NULL,
  created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_relation_candidates_job (job_id),
  KEY idx_relation_candidates_review (review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选关系';

-- 11.3 AI 会话
CREATE TABLE IF NOT EXISTS chat_sessions (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  node_id    BIGINT       NOT NULL,
  title      VARCHAR(200) NULL,
  mode       VARCHAR(20)  NOT NULL DEFAULT 'knowledge_only',
  created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chat_sessions_node (node_id),
  CONSTRAINT fk_chat_sessions_node FOREIGN KEY (node_id) REFERENCES knowledge_nodes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 会话';

-- 11.3 AI 消息
CREATE TABLE IF NOT EXISTS chat_messages (
  id                     BIGINT      NOT NULL AUTO_INCREMENT,
  session_id             BIGINT      NOT NULL,
  role                   VARCHAR(10) NOT NULL COMMENT 'user|assistant|system',
  content                MEDIUMTEXT  NULL,
  citations_json         JSON        NULL,
  retrieval_summary_json JSON        NULL,
  status                 VARCHAR(20) NOT NULL DEFAULT 'completed',
  created_at             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chat_messages_session (session_id),
  CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 消息';

-- 11.4 系统配置（密钥加密存储，接口返回只给掩码）
CREATE TABLE IF NOT EXISTS system_settings (
  setting_key     VARCHAR(100) NOT NULL,
  encrypted_value TEXT         NULL,
  is_secret       TINYINT(1)   NOT NULL DEFAULT 0,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置';
