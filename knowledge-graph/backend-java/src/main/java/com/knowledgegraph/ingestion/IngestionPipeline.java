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
        if (needsOcr > 0) {
            markJob(jobId, "OCR_RUNNING", "OCR_RUNNING", -1, null, null);
            boolean visionAvailable = ocrProvider.isAvailable();
            int processed = 0;
            for (ParsedUnit unit : units) {
                if (isCancelled(jobId)) {
                    finishCancelled(jobId, documentId);
                    return;
                }
                if (!unit.needsOcr() || unit.imageBytes() == null) {
                    continue;
                }
                if (!visionAvailable) {
                    jdbc.sql("UPDATE document_units SET status = 'NO_OCR' WHERE document_id = :d AND unit_index = :i")
                            .param("d", documentId).param("i", unit.unitIndex()).update();
                } else {
                    try {
                        String text = ocrProvider.transcribe(unit.imageBytes(), unit.imageFormat());
                        jdbc.sql("""
                                        UPDATE document_units SET extracted_text = :text, ocr_used = 1,
                                              status = 'OCR_OK' WHERE document_id = :d AND unit_index = :i
                                        """)
                                .param("text", text).param("d", documentId).param("i", unit.unitIndex())
                                .update();
                    } catch (Exception ex) {
                        jdbc.sql("UPDATE document_units SET status = 'NO_OCR' WHERE document_id = :d AND unit_index = :i")
                                .param("d", documentId).param("i", unit.unitIndex()).update();
                    }
                }
                processed++;
                setProgress(jobId, "OCR_RUNNING", 40 + (int) (30.0 * processed / needsOcr), units.size());
            }
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
        finalizeChunkingAndExtract(documentId, jobId, units.size());
    }

    /**
     * 分段完成后的收尾：抽取 → AWAITING_REVIEW（成功）或 FAILED（失败，带明确错误码）。
     * 包级可见以便集成测试直接驱动（不依赖真实文件解析）。
     */
    void finalizeChunkingAndExtract(long documentId, long jobId, int totalUnits) {
        try {
            long chunkCount = countChunks(documentId);
            if (chunkCount == 0) {
                throw new ApiException(422, ErrorCodes.DOCUMENT_NO_EXTRACTABLE_TEXT,
                        "文档中没有可抽取的文本片段，无法进行知识抽取");
            }
            setProgress(jobId, "AI_EXTRACTING", 80, totalUnits);
            // 同步执行（当前已在流水线异步线程）；成功 → AWAITING_REVIEW，失败 → 落 FAILED
            extractionService.runExtraction(jobId);

            String warning = countNoOcr(documentId) > 0
                    ? "部分图片单元未识别：未配置具备视觉能力的模型（模型管理 → 视觉能力）"
                    : null;
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

    private long countNoOcr(long documentId) {
        Long value = jdbc.sql("SELECT COUNT(*) FROM document_units WHERE document_id = :id AND status = 'NO_OCR'")
                .param("id", documentId).query(Long.class).single();
        return value == null ? 0 : value;
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
