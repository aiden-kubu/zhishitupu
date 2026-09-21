-- =====================================================================
-- V7: 文档元数据与标签（2026-09-13 属性面板需求：标题/生命周期/可信度/标签/知识库别名）
-- 全部幂等（CREATE TABLE IF NOT EXISTS），不修改既有表结构、不迁移数据。
-- 人工标签来源 human 优先；AI 自动整理只补充 source='ai' 的标签，不覆盖人工标签。
-- =====================================================================

-- 文档元数据：与 documents 1:1 的扩展属性（标题/生命周期/可信度汇总）
CREATE TABLE IF NOT EXISTS document_metadata (
    document_id       BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    title             VARCHAR(255) NULL COMMENT '可编辑展示标题，默认取原文件名',
    lifecycle_status  VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active 在用 | archived 已归档 | outdated 已过期',
    verification_json JSON         NULL COMMENT '哈希校验 / AI 复审汇总 / 人工校对记录',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_metadata_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文档元数据';

-- 文档标签：多值、人工与 AI 双来源，(document_id, normalized_tag) 唯一去重
CREATE TABLE IF NOT EXISTS document_tags (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id    BIGINT UNSIGNED NOT NULL,
    tag            VARCHAR(100) NOT NULL,
    normalized_tag VARCHAR(100) NOT NULL,
    source         VARCHAR(10)  NOT NULL DEFAULT 'human' COMMENT 'human 人工 | ai 自动整理',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_tags (document_id, normalized_tag),
    KEY idx_document_tags_normalized (normalized_tag),
    CONSTRAINT fk_document_tags_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文档标签';

-- 知识库别名：AI 自动整理按同义名称归入已有库的匹配依据，人工可维护
CREATE TABLE IF NOT EXISTS library_aliases (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    library_id       BIGINT NOT NULL,
    alias            VARCHAR(200) NOT NULL,
    normalized_alias VARCHAR(200) NOT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_library_aliases (library_id, normalized_alias),
    KEY idx_library_aliases_normalized (normalized_alias),
    CONSTRAINT fk_library_aliases_library FOREIGN KEY (library_id) REFERENCES knowledge_libraries (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库别名';
