package com.knowledgegraph.assistant.assessment;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.util.List;

/** Bound, scoped SELECTs only. The caller also validates the complete path through LearningReadRepository. */
public final class JdbcAssessmentReadRepository implements AssessmentReadRepository {
    private final JdbcClient jdbc;

    public JdbcAssessmentReadRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public Page legacyAttempts(Scope scope, long pathId, Long stepId, int limit, Long beforeAttemptId) {
        if (pathId <= 0 || stepId != null && stepId <= 0 || limit < 1 || limit > 20
                || beforeAttemptId != null && beforeAttemptId <= 0) {
            throw new IllegalArgumentException("INVALID_HISTORY_QUERY");
        }
        String sql = """
                SELECT a.id,a.path_id,a.step_id,s.path_id AS actual_step_path_id,
                       a.score,a.correct_count,a.total_count,CAST(a.weak_topics AS CHAR),a.created_at
                FROM learning_assessment_attempts a
                JOIN learning_paths p ON p.id=a.path_id AND p.profile_key='local-default'
                LEFT JOIN learning_path_steps s ON s.id=a.step_id
                WHERE a.path_id=:pathId
                """;
        if (stepId != null) sql += " AND a.step_id=:stepId";
        if (beforeAttemptId != null) sql += " AND a.id<:beforeAttemptId";
        if (!scope.libraryIds().isEmpty()) sql += " AND p.library_id IN (:libraryIds)";
        if (!scope.documentIds().isEmpty()) sql += """
                 AND EXISTS(SELECT 1 FROM documents d WHERE d.id IN (:documentIds)
                    AND (d.library_id=p.library_id OR EXISTS
                      (SELECT 1 FROM document_topic_assignments x
                       WHERE x.document_id=d.id AND x.library_id=p.library_id)))
                """;
        if (!scope.nodeIds().isEmpty()) sql += """
                 AND EXISTS(SELECT 1 FROM learning_path_steps visible_step
                    WHERE visible_step.path_id=p.id AND visible_step.node_id IN (:nodeIds))
                """;
        sql += " ORDER BY a.id DESC LIMIT :maxRows";
        JdbcClient.StatementSpec query = jdbc.sql(sql).param("pathId", pathId).param("maxRows", limit + 1);
        if (stepId != null) query = query.param("stepId", stepId);
        if (beforeAttemptId != null) query = query.param("beforeAttemptId", beforeAttemptId);
        if (!scope.libraryIds().isEmpty()) query = query.param("libraryIds", scope.libraryIds());
        if (!scope.documentIds().isEmpty()) query = query.param("documentIds", scope.documentIds());
        if (!scope.nodeIds().isEmpty()) query = query.param("nodeIds", scope.nodeIds());
        List<LegacyAttempt> rows = query.query((rs, i) -> {
            Timestamp created = rs.getTimestamp(9);
            return new LegacyAttempt(rs.getLong(1), rs.getLong(2), rs.getObject(3, Long.class),
                    rs.getObject(4, Long.class), rs.getInt(5), rs.getInt(6), rs.getInt(7),
                    rs.getString(8), created == null ? null : created.toInstant());
        }).list();
        return new Page(rows.subList(0, Math.min(rows.size(), limit)), rows.size() > limit);
    }
}
