package com.knowledgegraph.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class LearningPathService {

    private static final String PROFILE = "local-default";
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public LearningPathService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record StepView(String id, long nodeId, String title, String description, String detail,
                           int minutes, String status, LocalDateTime visitedAt, Integer assessmentScore) {}

    public record PathView(int version, long libraryId, String libraryName, String goal, String difficulty,
                           LocalDateTime updatedAt, String selectedStepId, List<StepView> steps) {}

    public record StepRequest(String id, long nodeId, String title, String description, String detail,
                              int minutes, String status) {}

    public record SaveRequest(long libraryId, String goal, String difficulty, List<StepRequest> steps) {}

    public record AssessmentRequest(int score, int correctCount, int totalCount, List<String> weakTopics) {}

    public Optional<PathView> current() {
        return currentPathId().map(this::view);
    }

    @Transactional
    public PathView save(SaveRequest request) {
        validate(request);
        Long libraryExists = jdbc.sql("SELECT COUNT(*) FROM knowledge_libraries WHERE id = :id AND status = 'active'")
                .param("id", request.libraryId()).query(Long.class).single();
        if (libraryExists == 0) throw ApiException.badRequest("所选知识库不存在或不可用");

        List<Long> nodeIds = request.steps().stream().map(StepRequest::nodeId).toList();
        Long validNodes = jdbc.sql("""
                SELECT COUNT(DISTINCT kn.id) FROM knowledge_nodes kn
                JOIN library_nodes ln ON ln.node_id = kn.id
                WHERE ln.library_id = :libraryId AND kn.id IN (:nodeIds) AND kn.status = 'active'
                """).param("libraryId", request.libraryId()).param("nodeIds", nodeIds)
                .query(Long.class).single();
        if (validNodes != nodeIds.size()) throw ApiException.badRequest("路径包含不属于所选知识库或已失效的节点");

        currentPathId().ifPresent(id -> jdbc.sql("DELETE FROM learning_paths WHERE id = :id").param("id", id).update());
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO learning_paths(profile_key, library_id, goal, difficulty, status)
                VALUES (:profile, :libraryId, :goal, :difficulty, 'active')
                """).param("profile", PROFILE).param("libraryId", request.libraryId())
                .param("goal", request.goal().strip()).param("difficulty", request.difficulty()).update(keys);
        long pathId = keys.getKey().longValue();
        for (int index = 0; index < request.steps().size(); index++) {
            StepRequest step = request.steps().get(index);
            jdbc.sql("""
                    INSERT INTO learning_path_steps(path_id, step_key, node_id, position_index, title,
                      description_text, detail_text, estimated_minutes, status)
                    VALUES (:pathId, :stepKey, :nodeId, :position, :title, :description, :detail, :minutes, :status)
                    """).param("pathId", pathId).param("stepKey", step.id()).param("nodeId", step.nodeId())
                    .param("position", index).param("title", step.title()).param("description", step.description())
                    .param("detail", step.detail()).param("minutes", step.minutes()).param("status", step.status()).update();
        }
        return view(pathId);
    }

    @Transactional
    public PathView markVisited(String stepKey) {
        long pathId = requiredCurrentPathId();
        int changed = jdbc.sql("""
                UPDATE learning_path_steps SET visited_at = COALESCE(visited_at, CURRENT_TIMESTAMP)
                WHERE path_id = :pathId AND step_key = :stepKey AND status <> 'locked'
                """).param("pathId", pathId).param("stepKey", stepKey).update();
        if (changed == 0) throw ApiException.badRequest("该学习阶段不存在或尚未解锁");
        return view(pathId);
    }

    @Transactional
    public PathView complete(String stepKey) {
        long pathId = requiredCurrentPathId();
        StepRow step = step(pathId, stepKey);
        if (!"current".equals(step.status())) throw ApiException.badRequest("只有当前阶段可以完成");
        if (step.visitedAt() == null) throw ApiException.conflict("请先进入图谱学习当前知识点");
        if (step.assessmentScore() == null || step.assessmentScore() < 60) {
            throw ApiException.conflict("请先完成智能测评并达到 60 分");
        }
        jdbc.sql("UPDATE learning_path_steps SET status = 'completed', completed_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", step.id()).update();
        jdbc.sql("""
                UPDATE learning_path_steps SET status = 'current'
                WHERE path_id = :pathId AND position_index = :nextPosition AND status = 'locked'
                """).param("pathId", pathId).param("nextPosition", step.position() + 1).update();
        return view(pathId);
    }

    @Transactional
    public PathView recordAssessment(AssessmentRequest request) {
        if (request.score() < 0 || request.score() > 100 || request.totalCount() <= 0 ||
                request.correctCount() < 0 || request.correctCount() > request.totalCount()) {
            throw ApiException.badRequest("测评结果无效");
        }
        long pathId = requiredCurrentPathId();
        Optional<StepRow> currentStep = jdbc.sql("""
                SELECT id, position_index, status, visited_at, assessment_score
                FROM learning_path_steps WHERE path_id = :pathId AND status = 'current' LIMIT 1
                """).param("pathId", pathId).query((rs, i) -> new StepRow(rs.getLong("id"),
                        rs.getInt("position_index"), rs.getString("status"),
                        rs.getTimestamp("visited_at") == null ? null : rs.getTimestamp("visited_at").toLocalDateTime(),
                        rs.getObject("assessment_score", Integer.class))).optional();
        jdbc.sql("""
                INSERT INTO learning_assessment_attempts(path_id, step_id, score, correct_count, total_count, weak_topics)
                VALUES (:pathId, :stepId, :score, :correct, :total, CAST(:weakTopics AS JSON))
                """).param("pathId", pathId).param("stepId", currentStep.map(StepRow::id).orElse(null))
                .param("score", request.score()).param("correct", request.correctCount())
                .param("total", request.totalCount()).param("weakTopics", json(request.weakTopics())).update();
        currentStep.ifPresent(step -> {
            jdbc.sql("UPDATE learning_path_steps SET assessment_score = :score WHERE id = :id")
                    .param("score", request.score()).param("id", step.id()).update();
            if (request.score() >= 60) {
                jdbc.sql("UPDATE learning_path_steps SET status = 'completed', completed_at = CURRENT_TIMESTAMP WHERE id = :id")
                        .param("id", step.id()).update();
                jdbc.sql("""
                        UPDATE learning_path_steps SET status = 'current'
                        WHERE path_id = :pathId AND position_index = :nextPosition AND status = 'locked'
                        """).param("pathId", pathId).param("nextPosition", step.position() + 1).update();
            }
        });
        return view(pathId);
    }

    private PathView view(long pathId) {
        PathRow path = jdbc.sql("""
                SELECT lp.id, lp.library_id, kl.name AS library_name, lp.goal, lp.difficulty, lp.updated_at
                FROM learning_paths lp JOIN knowledge_libraries kl ON kl.id = lp.library_id WHERE lp.id = :id
                """).param("id", pathId).query((rs, i) -> new PathRow(rs.getLong("id"), rs.getLong("library_id"),
                        rs.getString("library_name"), rs.getString("goal"), rs.getString("difficulty"),
                        rs.getTimestamp("updated_at").toLocalDateTime())).single();
        List<StepView> steps = jdbc.sql("""
                SELECT step_key, node_id, title, description_text, detail_text, estimated_minutes,
                       status, visited_at, assessment_score
                FROM learning_path_steps WHERE path_id = :pathId ORDER BY position_index
                """).param("pathId", pathId).query((rs, i) -> new StepView(rs.getString("step_key"),
                        rs.getLong("node_id"), rs.getString("title"), rs.getString("description_text"),
                        rs.getString("detail_text"), rs.getInt("estimated_minutes"), rs.getString("status"),
                        rs.getTimestamp("visited_at") == null ? null : rs.getTimestamp("visited_at").toLocalDateTime(),
                        rs.getObject("assessment_score", Integer.class))).list();
        String selected = steps.stream().filter(item -> "current".equals(item.status())).findFirst()
                .orElse(steps.get(0)).id();
        return new PathView(2, path.libraryId(), path.libraryName(), path.goal(), path.difficulty(),
                path.updatedAt(), selected, steps);
    }

    private void validate(SaveRequest request) {
        if (request == null || request.libraryId() <= 0 || request.goal() == null || request.goal().isBlank() ||
                request.goal().length() > 500 || !Set.of("beginner", "intermediate", "advanced").contains(request.difficulty()) ||
                request.steps() == null || request.steps().size() < 2 || request.steps().size() > 8) {
            throw ApiException.badRequest("学习路径参数无效");
        }
        Set<Long> nodeIds = new HashSet<>();
        int currentCount = 0;
        boolean seenCurrent = false;
        boolean seenLocked = false;
        for (StepRequest step : request.steps()) {
            if (step == null || step.id() == null || step.id().isBlank() || step.nodeId() <= 0 ||
                    step.title() == null || step.title().isBlank() || step.minutes() <= 0 ||
                    !Set.of("completed", "current", "locked").contains(step.status()) || !nodeIds.add(step.nodeId())) {
                throw ApiException.badRequest("学习阶段参数无效或包含重复节点");
            }
            if ("current".equals(step.status())) {
                if (seenLocked) throw ApiException.badRequest("学习阶段状态顺序无效");
                currentCount++;
                seenCurrent = true;
            } else if ("locked".equals(step.status())) {
                seenLocked = true;
            } else if (seenCurrent || seenLocked) {
                throw ApiException.badRequest("学习阶段状态顺序无效");
            }
        }
        boolean allCompleted = request.steps().stream().allMatch(step -> "completed".equals(step.status()));
        if (!allCompleted && currentCount != 1) {
            throw ApiException.badRequest("学习路径必须有且仅有一个当前阶段");
        }
    }

    private Optional<Long> currentPathId() {
        return jdbc.sql("SELECT id FROM learning_paths WHERE profile_key = :profile LIMIT 1")
                .param("profile", PROFILE).query(Long.class).optional();
    }

    private long requiredCurrentPathId() {
        return currentPathId().orElseThrow(() -> ApiException.notFound("尚未生成学习路径"));
    }

    private StepRow step(long pathId, String stepKey) {
        return jdbc.sql("""
                SELECT id, position_index, status, visited_at, assessment_score
                FROM learning_path_steps WHERE path_id = :pathId AND step_key = :stepKey
                """).param("pathId", pathId).param("stepKey", stepKey)
                .query((rs, i) -> new StepRow(rs.getLong("id"), rs.getInt("position_index"),
                        rs.getString("status"), rs.getTimestamp("visited_at") == null ? null : rs.getTimestamp("visited_at").toLocalDateTime(),
                        rs.getObject("assessment_score", Integer.class)))
                .optional().orElseThrow(() -> ApiException.notFound("学习阶段不存在"));
    }

    private String json(List<String> values) {
        try { return objectMapper.writeValueAsString(values == null ? List.of() : values); }
        catch (JsonProcessingException ex) { throw ApiException.badRequest("薄弱知识点格式无效"); }
    }

    private record PathRow(long id, long libraryId, String libraryName, String goal, String difficulty, LocalDateTime updatedAt) {}
    private record StepRow(long id, int position, String status, LocalDateTime visitedAt, Integer assessmentScore) {}
}
