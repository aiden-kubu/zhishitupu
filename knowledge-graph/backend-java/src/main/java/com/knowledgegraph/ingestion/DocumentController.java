package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.common.ApiResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.Map;

/**
 * 文档接口（§12.3）：上传、列表、详情、删除、原文定位单元、触发解析。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ProcessingService processingService;
    private final IngestionPipeline pipeline;
    private final JdbcClient jdbc;

    public DocumentController(DocumentService documentService, ProcessingService processingService,
                              IngestionPipeline pipeline, JdbcClient jdbc) {
        this.documentService = documentService;
        this.processingService = processingService;
        this.pipeline = pipeline;
        this.jdbc = jdbc;
    }

    @PostMapping
    public ApiResponse<DocumentService.DocumentView> upload(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long libraryId) {
        if (libraryId == null) {
            throw new ApiException(400, ErrorCodes.INVALID_ARGUMENT, "请选择导入的知识库");
        }
        return ApiResponse.ok(documentService.upload(libraryId, file));
    }

    @GetMapping
    public ApiResponse<Object> list(@RequestParam(required = false) Long libraryId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(documentService.list(libraryId, status, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentService.DocumentView> get(@PathVariable long id) {
        return ApiResponse.ok(documentService.get(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable long id) {
        documentService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}/units/{unitIndex}")
    public ApiResponse<Object> unit(@PathVariable long id, @PathVariable int unitIndex) {
        DocumentService.UnitView unit = documentService.unit(id, unitIndex);
        return ApiResponse.ok(Map.of(
                "unit", unit,
                "chunks", documentService.chunksOfUnit(unit.id())));
    }

    /** 触发解析流水线：异步执行，进度经 /api/processing/jobs 轮询（§12.4）。 */
    @PostMapping("/{id}/process")
    public ApiResponse<ProcessingService.IngestionJobRow> process(@PathVariable long id) {
        documentService.get(id);
        if (!Files.exists(documentService.storagePathOf(id))) {
            throw new ApiException(409, ErrorCodes.CONFLICT, "文件已丢失，无法解析，请重新上传");
        }
        // 上传时创建的初始 UPLOADED 任务：直接交给流水线执行
        Long pendingJobId = jdbc.sql("""
                        SELECT id FROM ingestion_jobs
                        WHERE document_id = :id AND status = 'UPLOADED' ORDER BY id DESC LIMIT 1
                        """)
                .param("id", id).query(Long.class).optional().orElse(null);
        if (pendingJobId != null) {
            pipeline.run(id, pendingJobId);
            return ApiResponse.ok(processingService.get(pendingJobId));
        }
        Long active = jdbc.sql("""
                        SELECT COUNT(*) FROM ingestion_jobs
                        WHERE document_id = :id AND status IN
                              ('VALIDATING','PARSING','OCR_RUNNING','CHUNKING','AI_EXTRACTING','IMPORTING')
                        """)
                .param("id", id).query(Long.class).single();
        if (active != null && active > 0) {
            throw new ApiException(409, ErrorCodes.CONFLICT, "该资料已有正在进行的解析任务");
        }
        jdbc.sql("INSERT INTO ingestion_jobs (document_id, stage, status) VALUES (:id, 'VALIDATING', 'VALIDATING')")
                .param("id", id).update();
        long jobId = jdbc.sql(
                        "SELECT id FROM ingestion_jobs WHERE document_id = :id ORDER BY id DESC LIMIT 1")
                .param("id", id).query(Long.class).single();
        pipeline.run(id, jobId);
        return ApiResponse.ok(processingService.get(jobId));
    }
}
