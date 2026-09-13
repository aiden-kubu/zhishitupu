-- V4: 模型档案表（多模型支持，2026-09-13 用户需求）
-- 每个档案对应一个 OpenAI 兼容的模型接入；is_default 供抽取/问答任务选择默认模型，
-- vision 标记该模型是否具备视觉能力（图片 OCR / 文档视觉解析路由依据）。
CREATE TABLE IF NOT EXISTS llm_profiles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL COMMENT '显示名，如 deepseek-知识抽取',
    base_url VARCHAR(500) NOT NULL,
    model VARCHAR(128) NOT NULL,
    encrypted_api_key TEXT NULL COMMENT 'AES-GCM 加密存储，接口只返回掩码',
    timeout_ms INT NOT NULL DEFAULT 30000,
    max_output_tokens INT NOT NULL DEFAULT 2048,
    vision TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_llm_profiles_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
