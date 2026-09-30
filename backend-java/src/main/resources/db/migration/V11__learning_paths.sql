CREATE TABLE IF NOT EXISTS learning_paths (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  profile_key   VARCHAR(100) NOT NULL DEFAULT 'local-default',
  library_id    BIGINT       NOT NULL,
  goal          VARCHAR(500) NOT NULL,
  difficulty    VARCHAR(20)  NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'active',
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_learning_paths_profile (profile_key),
  CONSTRAINT fk_learning_paths_library FOREIGN KEY (library_id) REFERENCES knowledge_libraries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS learning_path_steps (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  path_id           BIGINT       NOT NULL,
  step_key          VARCHAR(100) NOT NULL,
  node_id           BIGINT       NOT NULL,
  position_index    INT          NOT NULL,
  title             VARCHAR(200) NOT NULL,
  description_text  TEXT         NOT NULL,
  detail_text       TEXT         NOT NULL,
  estimated_minutes INT          NOT NULL,
  status            VARCHAR(20)  NOT NULL,
  visited_at        TIMESTAMP    NULL,
  assessment_score  INT          NULL,
  completed_at      TIMESTAMP    NULL,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_learning_step_key (path_id, step_key),
  UNIQUE KEY uk_learning_step_position (path_id, position_index),
  KEY idx_learning_step_node (node_id),
  CONSTRAINT fk_learning_steps_path FOREIGN KEY (path_id) REFERENCES learning_paths(id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_steps_node FOREIGN KEY (node_id) REFERENCES knowledge_nodes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS learning_assessment_attempts (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  path_id        BIGINT      NOT NULL,
  step_id        BIGINT      NULL,
  score          INT         NOT NULL,
  correct_count  INT         NOT NULL,
  total_count    INT         NOT NULL,
  weak_topics    JSON        NULL,
  created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_learning_attempt_path (path_id, created_at),
  CONSTRAINT fk_learning_attempt_path FOREIGN KEY (path_id) REFERENCES learning_paths(id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_attempt_step FOREIGN KEY (step_id) REFERENCES learning_path_steps(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
