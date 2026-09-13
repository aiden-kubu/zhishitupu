-- V5: 阶段 D 资料摄取表（§11.1/§11.2）
-- 前提：旧结构 documents 表已由用户确认归档为 documents_legacy（0 行，RENAME 可逆）。
-- 若在仍存在旧结构 documents 表的库上执行，本脚本的 CREATE TABLE IF NOT EXISTS documents 会被跳过，
-- 相关外键将创建失败；此时请先执行：RENAME TABLE documents TO documents_legacy;

-- 资料文档（新 §11 结构）
CREATE TABLE IF NOT EXISTS documents (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    library_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(128) NOT NULL,
    mime_type VARCHAR(128) NULL,
    extension VARCHAR(16) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
    sha256 CHAR(64) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_documents_sha_library (sha256, library_id),
    KEY idx_documents_library (library_id),
    KEY idx_documents_status (status),
    CONSTRAINT fk_documents_library FOREIGN KEY (library_id) REFERENCES knowledge_libraries (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 文档单元：PDF 页 / PPT 幻灯片 / 单张图片
CREATE TABLE IF NOT EXISTS document_units (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT UNSIGNED NOT NULL,
    unit_type VARCHAR(16) NOT NULL COMMENT 'page | slide | image',
    unit_index INT NOT NULL,
    source_locator VARCHAR(255) NOT NULL COMMENT '页码 / 幻灯片号 / 图片文件名',
    extracted_text MEDIUMTEXT NULL,
    ocr_used TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/READY/NEEDS_OCR/OCR_OK/NO_OCR',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_units_order (document_id, unit_index),
    KEY idx_document_units_document (document_id),
    CONSTRAINT fk_units_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 文本分段（证据定位最小单元）
CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    chunk_index INT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    start_offset INT NOT NULL DEFAULT 0,
    end_offset INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chunks_unit (unit_id),
    CONSTRAINT fk_chunks_unit FOREIGN KEY (unit_id) REFERENCES document_units (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 处理任务
CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT UNSIGNED NOT NULL,
    stage VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    progress INT NOT NULL DEFAULT 0,
    processed_units INT NOT NULL DEFAULT 0,
    total_units INT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    KEY idx_jobs_document (document_id),
    KEY idx_jobs_status (status),
    CONSTRAINT fk_jobs_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
