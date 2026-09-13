package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.common.PageResponse;
import com.knowledgegraph.extraction.ExtractionService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 处理任务服务（§8.4/§13 IngestionJobService）：状态机、进度、重试与取消、抽取恢复。
 */
@Service
public class ProcessingService {

    public record IngestionJobRow(long id, Long documentId, String documentName, String stage, String status,
                                  int progress, int processedUnits, int totalUnits, String errorCode,
                                  String errorMessage, int retryCount, LocalDateTime createdAt,
                                  LocalDateTime startedAt, LocalDateTime finishedAt,
                                  long candidateCount, boolean extractable) {
    }

    /** 合法的取消/重试判断依据：仍处于活动阶段。 */
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "UPLOADED", "VALIDATING", "PARSING", "OCR_RUNNING", "CHUNKING", "AI_EXTRACTING", "IMPORTING");

    private final JdbcClient jdbc;
    private final IngestionPipeline pipeline;
    private final ExtractionService extractionService;

    /** 候选数量与 extractable 标记（已解析但无候选结果的旧任务可发起提取知识） */
    private static final String EXTRA_COLUMNS = """
                (SELECT COUNT(*) FROM entity_candidates e WHERE e.job_id = j.id)
              + (SELECT COUNT(*) FROM relation_candidates r WHERE r.job_id = j.id) AS candidate_count,
                CASE WHEN (
                        SELECT COUNT(*) FROM document_chunks c
                        JOIN document_units u ON u.id = c.unit_id WHERE u.document_id = j.document_id
                    ) > 0
                    AND ((SELECT COUNT(*) FROM entity_candidates e WHERE e.job_id = j.id)
                       + (SELECT COUNT(*) FROM relation_candidates r WHERE r.job_id = j.id)) = 0
                    AND j.status IN ('COMPLETED', 'FAILED')
                    THEN 1 ELSE 0 END AS extractable
            """;

    public ProcessingService(JdbcClient jdbc, IngestionPipeline pipeline, ExtractionService extractionService) {
        this.jdbc = jdbc;
        this.pipeline = pipeline;
        this.extractionService = extractionService;
    }

    public PageResponse<IngestionJobRow> list(String status, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        String where = status != null && !status.isBlank() ? "WHERE j.status = :status" : "";
        long total = jdbc.sql("SELECT COUNT(*) FROM ingestion_jobs j " + where)
                .param("status", status)
                .query((rs, i) -> rs.getLong(1))
                .single();
        List<IngestionJobRow> items = jdbc.sql("""
                        SELECT j.id, j.document_id, IFNULL(d.original_name, ''), j.stage, j.status, j.progress,
                               j.processed_units, j.total_units, j.error_code, j.error_message, j.retry_count,
                               j.created_at, j.started_at, j.finished_at,
                               %s
                        FROM ingestion_jobs j LEFT JOIN documents d ON d.id = j.document_id
                        %s ORDER BY j.id DESC LIMIT :size OFFSET :offset
                        """.formatted(EXTRA_COLUMNS, where))
                .param("status", status)
                .param("size", size)
                .param("offset", (long) (p - 1) * size)
                .query((rs, i) -> toRow(rs))
                .list();
        int totalPages = size > 0 ? (int) ((total + size - 1) / size) : 0;
        return new PageResponse<>(items, p, size, total, totalPages);
    }

    public IngestionJobRow get(long id) {
        return jdbc.sql("""
                        SELECT j.id, j.document_id, IFNULL(d.original_name, ''), j.stage, j.status, j.progress,
                               j.processed_units, j.total_units, j.error_code, j.error_message, j.retry_count,
                               j.created_at, j.started_at, j.finished_at,
                               %s
                        FROM ingestion_jobs j LEFT JOIN documents d ON d.id = j.document_id
                        WHERE j.id = :id
                        """.formatted(EXTRA_COLUMNS))
                .param("id", id)
                .query((rs, i) -> toRow(rs))
                .optional()
                .orElseThrow(() -> new ApiException(404, ErrorCodes.NOT_FOUND, "处理任务不存在"));
    }

    /** 重试（幂等）：仅允许失败/取消任务；清理旧产物后从解析阶段重跑。 */
    @Transactional
    public IngestionJobRow retry(long id) {
        IngestionJobRow job = get(id);
        if (!"FAILED".equals(job.status()) && !"CANCELLED".equals(job.status())) {
            throw new ApiException(409, ErrorCodes.CONFLICT, "仅失败或已取消的任务可以重试");
        }
        jdbc.sql("""
                        UPDATE ingestion_jobs SET stage = 'PARSING', status = 'PARSING', progress = 0,
                              processed_units = 0, total_units = 0, error_code = NULL, error_message = NULL,
                              retry_count = retry_count + 1, started_at = :now, finished_at = NULL
                        WHERE id = :id
                        """)
                .param("now", LocalDateTime.now()).param("id", id).update();
        jdbc.sql("DELETE FROM document_units WHERE document_id = :id")
                .param("id", job.documentId()).update();
        jdbc.sql("UPDATE documents SET status = 'PROCESSING' WHERE id = :id").param("id", job.documentId()).update();
        IngestionJobRow refreshed = get(id);
        pipeline.run(job.documentId(), id);
        return refreshed;
    }

    /** 取消：标记 CANCELLED，流水线在单元间检查标记后收尾。 */
    @Transactional
    public IngestionJobRow cancel(long id) {
        IngestionJobRow job = get(id);
        if (!ACTIVE_STATUSES.contains(job.status())) {
            throw new ApiException(409, ErrorCodes.CONFLICT, "任务已结束，无法取消");
        }
        jdbc.sql("UPDATE ingestion_jobs SET status = 'CANCELLED', stage = 'CANCELLED', finished_at = :now WHERE id = :id")
                .param("now", LocalDateTime.now()).param("id", id).update();
        jdbc.sql("UPDATE documents SET status = 'CANCELLED' WHERE id = :id AND status = 'PROCESSING'")
                .param("id", job.documentId()).update();
        return get(id);
    }

    private IngestionJobRow toRow(java.sql.ResultSet rs) {
        try {
            return new IngestionJobRow(
                    rs.getLong("id"),
                    rs.getObject("document_id") == null ? null : rs.getLong("document_id"),
                    rs.getString(3),
                    rs.getString("stage"), rs.getString("status"), rs.getInt("progress"),
                    rs.getInt("processed_units"), rs.getInt("total_units"),
                    rs.getString("error_code"), rs.getString("error_message"), rs.getInt("retry_count"),
                    rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toLocalDateTime(),
                    rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime(),
                    rs.getLong("candidate_count"),
                    rs.getBoolean("extractable"));
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * 恢复入口（§七）：对旧流水线已解析完成但无候选结果的任务发起 AI 抽取。
     * 同步完成校验（无 chunks → 422；已有候选 → 409）后异步执行，复用既有 chunks，不重新解析文件。
     */
    public IngestionJobRow requestExtraction(long id, boolean force) {
        IngestionJobRow job = get(id);
        extractionService.validateRecovery(id, force);
        jdbc.sql("""
                        UPDATE ingestion_jobs SET stage = 'AI_EXTRACTING', status = 'AI_EXTRACTING', progress = 80,
                              error_code = NULL, error_message = NULL, finished_at = NULL
                        WHERE id = :id
                        """)
                .param("id", id).update();
        jdbc.sql("UPDATE documents SET status = 'PROCESSING' WHERE id = :id")
                .param("id", job.documentId()).update();
        extractionService.runRecoveryAsync(id);
        return get(id);
    }
}
