package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.common.PageResponse;
import com.knowledgegraph.settings.SettingsService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档服务（§13 DocumentService）：上传校验、哈希去重、存储、删除与原文定位查询。
 */
@Service
public class DocumentService {

    public record DocumentView(long id, long libraryId, String libraryName, String originalName,
                               String mimeType, String extension, long sizeBytes, String sha256,
                               String status, String createdAt) {
    }

    public record UnitView(long id, int unitIndex, String unitType, String sourceLocator,
                           String extractedText, boolean ocrUsed, String status, int chunkCount) {
    }

    public record ChunkView(int chunkIndex, String content, String contentHash,
                            int startOffset, int endOffset) {
    }

    private final JdbcClient jdbc;
    private final DocumentStorage storage;
    private final DocumentValidator validator;
    private final ZipSafety zipSafety;
    private final SettingsService settingsService;

    public DocumentService(JdbcClient jdbc, DocumentStorage storage, DocumentValidator validator,
                           ZipSafety zipSafety, SettingsService settingsService) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.validator = validator;
        this.zipSafety = zipSafety;
        this.settingsService = settingsService;
    }

    @Transactional
    public DocumentView upload(Long libraryId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, ErrorCodes.INVALID_ARGUMENT, "请选择要上传的文件");
        }
        boolean automatic = libraryId == null;
        if (!automatic) requireLibrary(libraryId);
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("upload.bin");
        String extension = validator.extensionOf(originalName);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(400, ErrorCodes.INVALID_ARGUMENT, "无法读取上传文件");
        }
        byte[] head = content.length >= 8 ? java.util.Arrays.copyOf(content, 8) : content;
        validator.validate(originalName, head, file.getSize(), settingsService.maxUploadSizeMb());
        // ZIP 在上传阶段即做结构预扫（路径穿越/类型/条目数），恶意包直接拒绝（§17.3）
        if ("zip".equals(extension)) {
            zipSafety.validateStructure(content);
        }

        DocumentStorage.StoredFile stored = storage.save(new java.io.ByteArrayInputStream(content), extension);
        // 数据库失败或并发重复上传回滚时，一并清理刚保存的文件。
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) storage.delete(stored.path());
                    }
                });
        Long duplicate = jdbc.sql(
                        "SELECT id FROM documents WHERE sha256 = :sha AND (:automatic OR library_id = :libraryId) LIMIT 1")
                .param("sha", stored.sha256()).param("automatic", automatic).param("libraryId", libraryId)
                .query(Long.class).optional().orElse(null);
        if (duplicate != null) {
            storage.delete(stored.path());
            throw new ApiException(409, ErrorCodes.CONFLICT,
                    "该文件已上传，请到处理中心查看原任务或重试，无需重复上传");
        }
        if (automatic) {
            var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.sql("INSERT INTO knowledge_libraries (name, type, description, status) VALUES (:name, 'topic', :description, 'pending')")
                    .param("name", "待 AI 识别 · " + originalName.substring(0, Math.min(originalName.length(), 160)))
                    .param("description", "识别完成后自动命名，按主题归入已有知识库或新建多个知识库。")
                    .update(keys);
            libraryId = keys.getKey().longValue();
        }
        jdbc.sql("""
                        INSERT INTO documents (library_id, original_name, stored_name, mime_type, extension,
                                               size_bytes, sha256, storage_path, status)
                        VALUES (:libraryId, :originalName, :storedName, :mimeType, :extension,
                                :sizeBytes, :sha256, :storagePath, 'UPLOADED')
                        """)
                .param("libraryId", libraryId)
                .param("originalName", originalName)
                .param("storedName", stored.storedName())
                .param("mimeType", file.getContentType())
                .param("extension", extension)
                .param("sizeBytes", file.getSize())
                .param("sha256", stored.sha256())
                .param("storagePath", stored.path().toString())
                .update();
        // (sha256, library_id) 唯一，回查生成的主键
        Long id = jdbc.sql("SELECT id FROM documents WHERE sha256 = :sha AND library_id = :libraryId")
                .param("sha", stored.sha256()).param("libraryId", libraryId)
                .query(Long.class).single();
        jdbc.sql("""
                INSERT INTO ingestion_jobs (document_id, stage, status) VALUES (:documentId, 'UPLOADED', 'UPLOADED')
                """).param("documentId", id).update();
        if (automatic) {
            try {
                jdbc.sql("INSERT INTO auto_document_imports (document_id, sha256) VALUES (:id, :sha)")
                        .param("id", id).param("sha", stored.sha256()).update();
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                throw new ApiException(409, ErrorCodes.CONFLICT, "该文件已上传，请查看处理中心");
            }
        }
        return get(id);
    }

    public PageResponse<DocumentView> list(Long libraryId, String status, Integer page, Integer pageSize) {
        int p = page != null && page > 0 ? page : 1;
        int size = pageSize != null && pageSize > 0 ? pageSize : 20;
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (libraryId != null) {
            where.append(" AND (d.library_id = ").append(libraryId)
                    .append(" OR EXISTS (SELECT 1 FROM document_topic_assignments a WHERE a.document_id = d.id AND a.library_id = ")
                    .append(libraryId).append("))");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND d.status = '").append(status.replace("'", "")).append("'");
        }
        long total = jdbc.sql("SELECT COUNT(*) FROM documents d" + where).query(Long.class).single();
        List<DocumentView> items = jdbc.sql("""
                        SELECT d.id, d.library_id, IFNULL(l.name, ''), d.original_name, d.mime_type, d.extension,
                               d.size_bytes, d.sha256, d.status, d.created_at
                        FROM documents d LEFT JOIN knowledge_libraries l ON l.id = d.library_id
                        """ + where + " ORDER BY d.id DESC LIMIT " + size + " OFFSET " + ((p - 1) * size))
                .query((rs, i) -> new DocumentView(
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8),
                        rs.getString(9), String.valueOf(rs.getTimestamp(10))))
                .list();
        return new PageResponse<>(items, p, size, total, (int) ((total + size - 1) / size));
    }

    public DocumentView get(long id) {
        return jdbc.sql("""
                        SELECT d.id, d.library_id, IFNULL(l.name, ''), d.original_name, d.mime_type, d.extension,
                               d.size_bytes, d.sha256, d.status, d.created_at
                        FROM documents d LEFT JOIN knowledge_libraries l ON l.id = d.library_id
                        WHERE d.id = :id
                        """)
                .param("id", id)
                .query((rs, i) -> new DocumentView(
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8),
                        rs.getString(9), String.valueOf(rs.getTimestamp(10))))
                .optional()
                .orElseThrow(() -> new ApiException(404, ErrorCodes.NOT_FOUND, "资料不存在"));
    }

    public Path storagePathOf(long id) {
        String path = jdbc.sql("SELECT storage_path FROM documents WHERE id = :id").param("id", id)
                .query(String.class).single();
        return Path.of(path);
    }

    public String extensionOf(long id) {
        return jdbc.sql("SELECT extension FROM documents WHERE id = :id").param("id", id)
                .query(String.class).single();
    }

    @Transactional
    public void delete(long id) {
        DocumentView view = get(id);
        try {
            storage.delete(storagePathOf(id));
        } catch (Exception ignored) {
            // 文件已不存在也允许删除记录
        }
        jdbc.sql("DELETE FROM documents WHERE id = :id").param("id", id).update();
        Map.of("deleted", true, "name", view.originalName());
    }

    public UnitView unit(long documentId, int unitIndex) {
        return jdbc.sql("""
                        SELECT u.id, u.unit_index, u.unit_type, u.source_locator, u.extracted_text,
                               u.ocr_used, u.status,
                               (SELECT COUNT(*) FROM document_chunks c WHERE c.unit_id = u.id) AS chunk_count
                        FROM document_units u WHERE u.document_id = :documentId AND u.unit_index = :unitIndex
                        """)
                .param("documentId", documentId).param("unitIndex", unitIndex)
                .query((rs, i) -> new UnitView(
                        rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6), rs.getString(7), rs.getInt(8)))
                .optional()
                .orElseThrow(() -> new ApiException(404, ErrorCodes.NOT_FOUND, "文档单元不存在"));
    }

    public List<ChunkView> chunksOfUnit(long unitId) {
        return jdbc.sql("""
                        SELECT chunk_index, content, content_hash, start_offset, end_offset
                        FROM document_chunks WHERE unit_id = :unitId ORDER BY chunk_index ASC
                        """)
                .param("unitId", unitId)
                .query((rs, i) -> new ChunkView(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getInt(5)))
                .list();
    }

    private void requireLibrary(long libraryId) {
        Long count = jdbc.sql("SELECT COUNT(*) FROM knowledge_libraries WHERE id = :id")
                .param("id", libraryId).query(Long.class).single();
        if (count == null || count != 1) {
            throw new ApiException(404, ErrorCodes.NOT_FOUND, "目标知识库不存在");
        }
    }
}
