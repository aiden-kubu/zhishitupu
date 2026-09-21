-- 增量落库 + 断点续跑：抽取批次成果按批持久化
--
-- 背景：整本教材量级资料需要数百次模型调用，此前「全部批次成功后同一事务写候选」意味着
-- 任何一批失败（超时、限流、输出不合规）都要把前面所有批次重跑一遍，既贵又慢。
-- 现在每批解析校验通过即写入本表，任务失败/取消/重试时按批指纹复用，只补齐缺失批次。
--
-- batch_signature：批次内片段内容与顺序的 SHA-256。重新解析同一份文件时片段内容不变即视为
--   同一批（可复用）；OCR 文本变化、重新分段、资料被替换都会改变指纹，该批自动作废重抽。
-- chunk_ids_json：落库当时该批的真实 chunkId 顺序，用于复用时的证据重映射
--   （重新解析后 chunkId 会变，但指纹相同即内容相同，按位置一一对应）。
-- payload_json：校验后的规范化抽取结果（entities/relations），复用时重新走一遍校验。
-- 任务进入 AWAITING_REVIEW（或明确要求重抽）时由业务代码删除对应行；随 ingestion_jobs 级联删除。
CREATE TABLE IF NOT EXISTS extraction_batch_results (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT UNSIGNED NOT NULL,
    batch_index INT NOT NULL,
    batch_signature CHAR(64) NOT NULL,
    chunk_ids_json TEXT NOT NULL,
    payload_json LONGTEXT NOT NULL,
    entity_count INT NOT NULL DEFAULT 0,
    relation_count INT NOT NULL DEFAULT 0,
    model VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_extraction_batch (job_id, batch_index),
    CONSTRAINT fk_extraction_batch_job FOREIGN KEY (job_id) REFERENCES ingestion_jobs (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
