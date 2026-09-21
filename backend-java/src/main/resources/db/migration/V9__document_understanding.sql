-- Content-based course identity and resumable organization; existing data is preserved.
CREATE TABLE IF NOT EXISTS document_analyses (
 document_id BIGINT UNSIGNED PRIMARY KEY,
 source_signature CHAR(64) NOT NULL,
 analysis_json JSON NOT NULL,
 model VARCHAR(128) NOT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT fk_document_analysis FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS organization_checkpoints (
 job_id BIGINT UNSIGNED PRIMARY KEY,
 input_signature CHAR(64) NOT NULL,
 next_offset INT NOT NULL,
 groups_json JSON NOT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT fk_organization_checkpoint FOREIGN KEY (job_id) REFERENCES ingestion_jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
