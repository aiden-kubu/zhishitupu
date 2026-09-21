-- Persist the batching strategy so upgrading never silently invalidates a legacy task's checkpoints.
CREATE TABLE IF NOT EXISTS extraction_plan_versions (
 job_id BIGINT UNSIGNED PRIMARY KEY,
 strategy VARCHAR(32) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_extraction_plan_version FOREIGN KEY (job_id) REFERENCES ingestion_jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
