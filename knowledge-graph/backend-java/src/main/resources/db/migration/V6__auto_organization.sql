-- 新上传资料的自动识别与多知识库分类；不重组已有库。
CREATE TABLE IF NOT EXISTS auto_document_imports (
  document_id BIGINT UNSIGNED PRIMARY KEY,
  sha256 CHAR(64) NOT NULL UNIQUE,
  CONSTRAINT fk_auto_import_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS document_topic_assignments (
  document_id BIGINT UNSIGNED NOT NULL,
  temp_key VARCHAR(50) NOT NULL,
  library_id BIGINT NOT NULL,
  PRIMARY KEY (document_id, temp_key, library_id),
  CONSTRAINT fk_topic_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
  CONSTRAINT fk_topic_library FOREIGN KEY (library_id) REFERENCES knowledge_libraries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 串行化自动建库的查重与写入，避免并发上传同主题重复建库。
CREATE TABLE IF NOT EXISTS organization_mutex (id INT PRIMARY KEY) ENGINE=InnoDB;
INSERT IGNORE INTO organization_mutex(id) VALUES (1);
