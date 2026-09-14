package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.extraction.ExtractionService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 解析流水线（§8.3）：解析 → OCR（视觉模型）→ 分段 → AI 抽取 → 等待人工审核。
 * 状态机（§8.4）：UPLOADED→VALIDATING→PARSING→(OCR_RUNNING)?→CHUNKING→AI_EXTRACTING→AWAITING_REVIEW；
 * 之后的 IMPORTING→COMPLETED 由审核确认入库（ReviewService）驱动。
 * CHUNKING 完成不代表任务完成；抽取失败进入 FAILED 并保留明确错误码。
 * 每个单元之间检查取消标记，取消后停在 CANCELLED；重试幂等（先清理旧产物再重跑）。
 * 原文片段只作数据使用，不执行其中的任何指令（§8.6/§14.2）。
 */
@Component
public class IngestionPipeline {

    private final JdbcClient jdbc;
    private final DocumentStorage storage;
    private final DocumentService documentService;
    private final PdfTextExtractor pdfExtractor;
    private final PowerPointTextExtractor powerPointExtractor;
    private final ZipImageExtractor zipImageExtractor;
    private final ChunkingService chunkingService;
    private final OcrProvider ocrProvider;
    private final ExtractionService extractionService;

    public IngestionPipeline(JdbcClient jdbc, DocumentStorage storage, DocumentService documentService,
                             PdfTextExtractor pdfExtractor, PowerPointTextExtractor powerPointExtractor,
                             ZipImageExtractor zipImageExtractor, ChunkingService chunkingService,
                             OcrProvider ocrProvider, ExtractionService extractionService) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.documentService = documentService;
        this.pdfExtractor = pdfExtractor;
        this.powerPointExtractor = powerPointExtractor;
        this.zipImageExtractor = zipImageExtractor;
        this.chunkingService = chunkingService;
        this.ocrProvider = ocrProvider;
        this.extractionService = extractionService;
    }

    @Async("ingestionExecutor")
    public void run(long documentId, long jobId) {
        try {
            execute(documentId, jobId);
        } catch (ApiException ex) {
            fail(jobId, documentId, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            fail(jobId, documentId, "DOCUMENT_PARSE_FAILED",
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void execute(long documentId, long jobId) {
        byte[] content = storage.read(documentService.storagePathOf(documentId));
        String extension = documentService.extensionOf(documentId);
        markJob(jobId, "VALIDATING", "VALIDATING", -1, null, null);
        setProgress(jobId, "VALIDATING", 5, -1);

        // 解析前清理旧产物，保证重试幂等
        jdbc.sql("DELETE FROM document_units WHERE document_id = :id").param("id", documentId).update();
        markJob(jobId, "PARSING", "PARSING", -1, null, null);

        List<ParsedUnit> units = switch (extension) {
            case "pdf" -> pdfExtractor.extract(content);
            case "ppt", "pptx" -> powerPointExtractor.extract(content, extension);
            case "zip" -> zipImageExtractor.extract(content);
            default -> throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                    "不支持的文件类型：" + extension);
        };
        if (units.isEmpty()) {
            throw new ApiException(422, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "文件中没有可解析的内容");
        }
        insertUnits(documentId, units);
        setProgress(jobId, "PARSING", 40, units.size());        // OCR 阶段：仅处理 NEEDS_OCR 单元；无视觉模型时按用户决策跳过并提示
        long needsOcr = countNeedingOcr(documentId);
        OcrRun ocrRun = null;
        if (needsOcr > 0) {
            markJob(jobId, "OCR_RUNNING", "OCR_RUNNING", -1, null, null);
            boolean visionAvailable = ocrProvider.isAvailable();
            int processed = 0;
            int failed = 0;
            String failureReason = null;
            for (ParsedUnit unit : units) {
                if (isCancelled(jobId)) {
                    finishCancelled(jobId, documentId);
                    return;
                }
                if (!unit.needsOcr() || unit.imageBytes() == null) {
                    continue;
                }
                if (!visionAvailable) {
                    // 没有识别通道：与「识别失败」「该单元本来就没有文字」分开记，重试时的引导文案不同
                    markUnitOcrOutcome(documentId, unit.unitIndex(), "OCR_SKIPPED", null);
                } else {
                    try {
                        String text = ocrProvider.transcribe(unit.imageBytes(), unit.imageFormat());
                        if (text == null || text.isBlank()) {
                            // 返回空文本时记为失败：单元若显示为已识别却对问答毫无贡献，会让知识缺口静默通过
                            failed++;
                            failureReason = "视觉模型未返回文字";
                            markUnitOcrOutcome(documentId, unit.unitIndex(), "OCR_FAILED", null);
                        } else {
                            markUnitOcrOutcome(documentId, unit.unitIndex(), "OCR_OK", text);
                        }
                    } catch (Exception ex) {
                        failed++;
                        failureReason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                        markUnitOcrOutcome(documentId, unit.unitIndex(), "OCR_FAILED", null);
                    }
                }
                processed++;
                setProgress(jobId, "OCR_RUNNING", 40 + (int) (30.0 * processed / needsOcr), units.size());
            }
            ocrRun = new OcrRun(visionAvailable, (int) needsOcr, failed, failureReason);
        }

        if (isCancelled(jobId)) {
            finishCancelled(jobId, documentId);
            return;
        }

        // 分段：不跨单元合并；同文档内重复片段去重
        markJob(jobId, "CHUNKING", "CHUNKING", -1, null, null);
        setProgress(jobId, "CHUNKING", 70, units.size());
        chunkUnits(documentId);

        // 分段完成 ≠ 任务完成：继续 AI 抽取，成功后进入 AWAITING_REVIEW（§8.4）
        finalizeChunkingAndExtract(documentId, jobId, units.size(), ocrRun);
    }

    /**
     * 分段完成后的收尾：抽取 → AWAITING_REVIEW（成功）或 FAILED（失败，带明确错误码）。
     * 包级可见以便集成测试直接驱动（不依赖真实文件解析）。
     */
    void finalizeChunkingAndExtract(long documentId, long jobId, int totalUnits) {
        finalizeChunkingAndExtract(documentId, jobId, totalUnits, null);
    }

    /**
     * @param ocrRun 本次 OCR 阶段的结果；由流水线传入以给出真实失败原因，测试与恢复入口可为 null（按单元状态归因）
     */
    void finalizeChunkingAndExtract(long documentId, long jobId, int totalUnits, OcrRun ocrRun) {
        try {
            if (countChunks(documentId) == 0) {
                throw zeroChunkFailure(documentId, ocrRun);
            }
            setProgress(jobId, "AI_EXTRACTING", 80, totalUnits);
            // 同步执行（当前已在流水线异步线程）；成功 → AWAITING_REVIEW，失败 → 落 FAILED
            extractionService.runExtraction(jobId);

            String warning = OcrGaps.partialWarning(countUnitsByStatus(documentId, "OCR_SKIPPED"),
                    countUnitsByStatus(documentId, "OCR_FAILED"),
                    ocrRun == null ? null : ocrRun.failureReason());
            if (warning != null) {
                jdbc.sql("UPDATE ingestion_jobs SET error_message = :w WHERE id = :id AND status = 'AWAITING_REVIEW'")
                        .param("w", warning).param("id", jobId).update();
            }
        } catch (ApiException ex) {
            fail(jobId, documentId, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            fail(jobId, documentId, "EXTRACT_FAILED",
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * 零分段归因：资料本身没有文字，与「图片没被识别成文字」是两回事，后者要引导用户配置视觉模型后重试。
     */
    private ApiException zeroChunkFailure(long documentId, OcrRun ocrRun) {
        long failedUnits = countUnitsByStatus(documentId, "OCR_FAILED");
        long skippedUnits = countUnitsByStatus(documentId, "OCR_SKIPPED") + countUnitsByStatus(documentId, "NEEDS_OCR");
        return OcrGaps.zeroChunkFailure(skippedUnits, failedUnits,
                ocrRun == null ? null : ocrRun.failureReason());
    }

    private long countChunks(long documentId) {
        Long value = jdbc.sql("""
                        SELECT COUNT(*) FROM document_chunks c JOIN document_units u ON u.id = c.unit_id
                        WHERE u.document_id = :id
                        """)
                .param("id", documentId).query(Long.class).optional().orElse(0L);
        return value == null ? 0 : value;
    }

    private void insertUnits(long documentId, List<ParsedUnit> units) {
        for (ParsedUnit unit : units) {
            String status = unit.needsOcr() ? "NEEDS_OCR" : (unit.text() == null || unit.text().isBlank() ? "NO_OCR" : "READY");
            jdbc.sql("""
                            INSERT INTO document_units (document_id, unit_type, unit_index, source_locator,
                                                        extracted_text, ocr_used, status)
                            VALUES (:documentId, :unitType, :unitIndex, :sourceLocator, :text, 0, :status)
                            """)
                    .param("documentId", documentId)
                    .param("unitType", unit.unitType())
                    .param("unitIndex", unit.unitIndex())
                    .param("sourceLocator", unit.sourceLocator())
                    .param("text", unit.text())
                    .param("status", status)
                    .update();
        }
        jdbc.sql("UPDATE documents SET status = 'PROCESSING' WHERE id = :id").param("id", documentId).update();
    }

    private void chunkUnits(long documentId) {
        List<Map<String, Object>> units = jdbc.sql("""
                        SELECT id, extracted_text FROM document_units
                        WHERE document_id = :id AND status IN ('READY', 'OCR_OK')
                        ORDER BY unit_index ASC
                        """)
                .param("id", documentId)
                .query((rs, i) -> Map.<String, Object>of(
                        "id", rs.getLong(1),
                        "text", rs.getString(2) == null ? "" : rs.getString(2)))
                .list();
        for (Map<String, Object> unit : units) {
            long unitId = (long) unit.get("id");
            String text = (String) unit.get("text");
            List<ChunkingService.Chunk> chunks = chunkingService.split(text);
            for (ChunkingService.Chunk chunk : chunks) {
                jdbc.sql("""
                                INSERT INTO document_chunks (unit_id, chunk_index, content, content_hash, start_offset, end_offset)
                                VALUES (:unitId, :index, :content, :hash, :start, :end)
                                """)
                        .param("unitId", unitId)
                        .param("index", chunk.index())
                        .param("content", chunk.content())
                        .param("hash", chunk.contentHash())
                        .param("start", chunk.startOffset())
                        .param("end", chunk.endOffset())
                        .update();
            }
        }
    }

    private void markJob(long jobId, String stage, String status, int ignoredProgress, String errorCode, String errorMessage) {
        jdbc.sql("""
                        UPDATE ingestion_jobs SET stage = :stage, status = :status,
                              started_at = COALESCE(started_at, :now), error_code = :errorCode, error_message = :errorMessage
                        WHERE id = :id
                        """)
                .param("stage", stage).param("status", status)
                .param("now", LocalDateTime.now())
                .param("errorCode", errorCode).param("errorMessage", errorMessage)
                .param("id", jobId)
                .update();
    }

    private void setProgress(long jobId, String stage, int progress, int totalUnits) {
        jdbc.sql("UPDATE ingestion_jobs SET stage = :stage, progress = :progress, total_units = :total WHERE id = :id")
                .param("stage", stage).param("progress", Math.max(0, Math.min(100, progress)))
                .param("total", totalUnits).param("id", jobId)
                .update();
    }

    private long countNeedingOcr(long documentId) {
        Long value = jdbc.sql("SELECT COUNT(*) FROM document_units WHERE document_id = :id AND status = 'NEEDS_OCR'")
                .param("id", documentId).query(Long.class).single();
        return value == null ? 0 : value;
    }

    private long countUnitsByStatus(long documentId, String status) {
        Long value = jdbc.sql("SELECT COUNT(*) FROM document_units WHERE document_id = :id AND status = :status")
                .param("id", documentId).param("status", status).query(Long.class).single();
        return value == null ? 0 : value;
    }

    /** 单个图片单元的识别结果；NO_OCR 表示没有通道，OCR_FAILED 表示尝试过但失败。 */
    private void markUnitOcrOutcome(long documentId, int unitIndex, String status, String text) {
        if ("OCR_OK".equals(status)) {
            jdbc.sql("""
                            UPDATE document_units SET extracted_text = :text, ocr_used = 1, status = 'OCR_OK'
                            WHERE document_id = :d AND unit_index = :i
                            """)
                    .param("text", text).param("d", documentId).param("i", unitIndex).update();
        } else {
            jdbc.sql("UPDATE document_units SET status = :status WHERE document_id = :d AND unit_index = :i")
                    .param("status", status).param("d", documentId).param("i", unitIndex).update();
        }
    }

    /** 本次 OCR 阶段结果，用于零分段时给出可操作的错误码与真实原因。 */
    record OcrRun(boolean channelAvailable, int requested, int failed, String failureReason) {
    }

    private boolean isCancelled(long jobId) {
        String status = jdbc.sql("SELECT status FROM ingestion_jobs WHERE id = :id").param("id", jobId)
                .query(String.class).single();
        return "CANCELLED".equals(status);
    }

    private void finishCancelled(long jobId, long documentId) {
        jdbc.sql("UPDATE ingestion_jobs SET finished_at = :now WHERE id = :id AND status = 'CANCELLED'")
                .param("now", LocalDateTime.now()).param("id", jobId).update();
        jdbc.sql("UPDATE documents SET status = 'CANCELLED' WHERE id = :id AND status = 'PROCESSING'")
                .param("id", documentId).update();
    }

    private void fail(long jobId, long documentId, String errorCode, String message) {
        jdbc.sql("""
                        UPDATE ingestion_jobs SET status = 'FAILED', stage = 'FAILED', finished_at = :now,
                              error_code = :code, error_message = :message
                        WHERE id = :id
                        """)
                .param("now", LocalDateTime.now())
                .param("code", errorCode == null ? "DOCUMENT_PARSE_FAILED" : errorCode)
                .param("message", message == null ? "未知错误" : message.substring(0, Math.min(message.length(), 490)))
                .param("id", jobId)
                .update();
        jdbc.sql("UPDATE documents SET status = 'FAILED' WHERE id = :id").param("id", documentId).update();
    }
}
