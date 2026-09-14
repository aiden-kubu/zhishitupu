package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.common.PageResponse;
import com.knowledgegraph.extraction.ExtractionService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
                                  long candidateCount, boolean extractable, int stagedBatches) {
    }

    /** 合法的取消/重试判断依据：仍处于活动阶段。 */
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "UPLOADED", "VALIDATING", "PARSING", "OCR_RUNNING", "CHUNKING", "AI_EXTRACTING", "AI_REVIEWING", "IMPORTING");

    private final JdbcClient jdbc;
    private final IngestionPipeline pipeline;
    private final ExtractionService extractionService;

    /** 候选数量、已落库的抽取批次（断点续跑进度）与 extractable 标记（已解析但无候选结果的旧任务可发起提取知识） */
    private static final String EXTRA_COLUMNS = """
                (SELECT COUNT(*) FROM entity_candidates e WHERE e.job_id = j.id)
              + (SELECT COUNT(*) FROM relation_candidates r WHERE r.job_id = j.id) AS candidate_count,
                (SELECT COUNT(*) FROM extraction_batch_results b WHERE b.job_id = j.id) AS staged_batches,
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

    /**
     * 重试（幂等）：仅允许失败/取消任务。
     * 断点续跑：若解析与分段产物仍然完整（已有片段、且没有待识别/未识别的图片单元），
     * 则保留这些产物，只从 AI 抽取阶段继续 —— 已落库的抽取批次会被复用，不再重复调用模型。
     * 其余情况（解析中断、图片未识别、资料本身无片段）仍按原逻辑清理产物、从解析阶段整体重跑，
     * 这样「配置视觉模型后重试」依旧会重新走 OCR。
     */
    @Transactional
    public IngestionJobRow retry(long id) {
        IngestionJobRow job = get(id);
        if (!"FAILED".equals(job.status()) && !"CANCELLED".equals(job.status())) {
            throw new ApiException(409, ErrorCodes.CONFLICT, "仅失败或已取消的任务可以重试");
        }
        if (canResumeExtraction(job.documentId())) {
            IngestionJobRow resuming = resumeExtraction(id);
            // 事务提交后再调度：否则异步线程可能读到提交前的 CANCELLED/FAILED 而立刻自我取消
            afterCommit(() -> extractionService.runRecoveryAsync(id));
            return resuming;
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

    /**
     * 续跑抽取：不重新解析文件，复用既有 chunks 与已落库的批次成果。
     * 没有可复用的批次时，本路径等价于对同一批 chunks 重新抽取（不重复磁盘解析与 OCR 调用）。
     */
    private IngestionJobRow resumeExtraction(long id) {
        extractionService.validateRecovery(id, false);
        jdbc.sql("""
                        UPDATE ingestion_jobs SET stage = 'AI_EXTRACTING', status = 'AI_EXTRACTING', progress = 80,
                              processed_units = 0, total_units = 0, error_code = NULL, error_message = NULL,
                              retry_count = retry_count + 1, finished_at = NULL
                        WHERE id = :id
                        """)
                .param("id", id).update();
        jdbc.sql("""
                        UPDATE documents SET status = 'PROCESSING'
                        WHERE id = (SELECT document_id FROM ingestion_jobs WHERE id = :id)
                        """)
                .param("id", id).update();
        return get(id);
    }

    /** 当前事务提交后执行（无事务时立即执行）。 */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * 解析产物是否可直接复用于续跑：有片段，且没有「重新跑一遍能得到更多内容」的单元。
     * NEEDS_OCR（解析被中断）、OCR_SKIPPED（当时没有视觉模型）、OCR_FAILED（识别失败）
     * 都要求重新解析，否则用户配置好视觉模型后重试也补不全图片知识。
     */
    private boolean canResumeExtraction(Long documentId) {
        if (documentId == null) {
            return false;
        }
        Long chunks = jdbc.sql("""
                        SELECT COUNT(*) FROM document_chunks c JOIN document_units u ON u.id = c.unit_id
                        WHERE u.document_id = :id
                        """)
                .param("id", documentId).query(Long.class).optional().orElse(0L);
        if (chunks == null || chunks == 0) {
            return false;
        }
        Long pending = jdbc.sql("""
                        SELECT COUNT(*) FROM document_units
                        WHERE document_id = :id AND status IN ('NEEDS_OCR', 'OCR_SKIPPED', 'OCR_FAILED')
                        """)
                .param("id", documentId).query(Long.class).optional().orElse(0L);
        return pending == null || pending == 0;
    }

    /** 取消：标记 CANCELLED，流水线在单元间检查标记后收尾。 */
    @Transactional
    public IngestionJobRow cancel(long id) {
        jdbc.sql("SELECT id FROM ingestion_jobs WHERE id=:id FOR UPDATE").param("id", id).query(Long.class).optional();
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
                    rs.getBoolean("extractable"),
                    rs.getInt("staged_batches"));
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
