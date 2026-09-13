package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.common.PageResponse;
import com.knowledgegraph.settings.SettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
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
                               String status, String createdAt, String title, String lifecycleStatus,
                               List<TagView> tags, Map<String, Object> verification) {
    }

    /** 文档标签（source：human 人工 / ai 自动整理）。 */
    public record TagView(String tag, String source) {
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
    private final ObjectMapper objectMapper;

    public DocumentService(JdbcClient jdbc, DocumentStorage storage, DocumentValidator validator,
                           ZipSafety zipSafety, SettingsService settingsService, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.validator = validator;
        this.zipSafety = zipSafety;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
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
        // 默认元数据：标题=原文件名、生命周期 active，并记录上传时的哈希校验
        jdbc.sql("""
                INSERT INTO document_metadata (document_id, title, lifecycle_status, verification_json)
                VALUES (:id, :title, 'active', :verification)
                ON DUPLICATE KEY UPDATE title = IFNULL(title, VALUES(title))
                """)
                .param("id", id)
                .param("title", originalName)
                .param("verification", toJson(Map.of("hash", Map.of(
                        "sha256", stored.sha256(), "checkedAt", LocalDateTime.now().toString(), "matched", true))))
                .update();
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
                               d.size_bytes, d.sha256, d.status, d.created_at,
                               m.title, m.lifecycle_status, m.verification_json
                        FROM documents d
                        LEFT JOIN knowledge_libraries l ON l.id = d.library_id
                        LEFT JOIN document_metadata m ON m.document_id = d.id
                        """ + where + " ORDER BY d.id DESC LIMIT " + size + " OFFSET " + ((p - 1) * size))
                .query(this::documentRow)
                .list();
        attachTags(items);
        return new PageResponse<>(items, p, size, total, (int) ((total + size - 1) / size));
    }

    public DocumentView get(long id) {
        DocumentView view = jdbc.sql("""
                        SELECT d.id, d.library_id, IFNULL(l.name, ''), d.original_name, d.mime_type, d.extension,
                               d.size_bytes, d.sha256, d.status, d.created_at,
                               m.title, m.lifecycle_status, m.verification_json
                        FROM documents d
                        LEFT JOIN knowledge_libraries l ON l.id = d.library_id
                        LEFT JOIN document_metadata m ON m.document_id = d.id
                        WHERE d.id = :id
                        """)
                .param("id", id)
                .query(this::documentRow)
                .optional()
                .orElseThrow(() -> new ApiException(404, ErrorCodes.NOT_FOUND, "资料不存在"));
        attachTags(List.of(view));
        return view;
    }

    // ---------------------------------------------------------------- 文档元数据 / 标签 / 校验

    private static final Set<String> LIFECYCLE_STATUSES = Set.of("active", "archived", "outdated");
    private static final int MAX_TAGS_PER_DOCUMENT = 20;
    private static final int MAX_TITLE_CHARS = 255;
    private static final int MAX_TAG_CHARS = 100;

    /** 更新标题与生命周期（缺省字段保持原值；元数据行不存在时按原文件名补建）。 */
    public DocumentView updateMetadata(long id, String title, String lifecycleStatus) {
        requireDocument(id);
        if (title != null && title.strip().isEmpty()) throw ApiException.badRequest("标题不能为空字符串");
        if (title != null && title.strip().length() > MAX_TITLE_CHARS)
            throw ApiException.badRequest("标题不能超过 " + MAX_TITLE_CHARS + " 字");
        String lifecycle = lifecycleStatus == null ? null : lifecycleStatus.strip().toLowerCase(java.util.Locale.ROOT);
        if (lifecycle != null && !LIFECYCLE_STATUSES.contains(lifecycle))
            throw ApiException.badRequest("非法的资料生命周期: " + lifecycleStatus);
        ensureMetadata(id);
        jdbc.sql("""
                UPDATE document_metadata SET
                  title = COALESCE(:title, title),
                  lifecycle_status = COALESCE(:lifecycle, lifecycle_status)
                WHERE document_id = :id
                """)
                .param("title", title == null ? null : title.strip())
                .param("lifecycle", lifecycle)
                .param("id", id).update();
        return get(id);
    }

    /** 人工添加标签（来源 human；归一化去重，AI 同名标签被唯一键阻挡，人工优先）。 */
    public DocumentView addTag(long id, String tag) {
        requireDocument(id);
        NormalizedTag normalized = normalizeTag(tag);
        Long count = jdbc.sql("SELECT COUNT(*) FROM document_tags WHERE document_id = :id")
                .param("id", id).query(Long.class).single();
        if (count != null && count >= MAX_TAGS_PER_DOCUMENT)
            throw ApiException.badRequest("每个资料最多 " + MAX_TAGS_PER_DOCUMENT + " 个标签");
        jdbc.sql("""
                INSERT INTO document_tags (document_id, tag, normalized_tag, source)
                VALUES (:id, :tag, :normalized, 'human')
                ON DUPLICATE KEY UPDATE source = 'human', tag = VALUES(tag)
                """)
                .param("id", id).param("tag", normalized.tag())
                .param("normalized", normalized.normalizedTag()).update();
        return get(id);
    }

    /** 删除标签（人工与 AI 标签都可删，对应标签 chips 的 ×）。 */
    public DocumentView removeTag(long id, String tag) {
        requireDocument(id);
        jdbc.sql("DELETE FROM document_tags WHERE document_id = :id AND normalized_tag = :n")
                .param("id", id).param("n", normalizeTag(tag).normalizedTag()).update();
        return get(id);
    }

    /** 人工校对记录：合并进 verification_json.human。 */
    public DocumentView setHumanVerification(long id, boolean checked, String note) {
        requireDocument(id);
        ensureMetadata(id);
        Map<String, Object> human = new LinkedHashMap<>();
        human.put("checked", checked);
        if (note != null && !note.isBlank()) human.put("note", note.strip());
        human.put("checkedAt", LocalDateTime.now().toString());
        mergeVerificationJson(id, "$.human", human);
        return get(id);
    }

    /** 汇总信息写入 verification_json 的指定路径（行不存在时按原文件名补建）。 */
    public void mergeVerificationJson(long documentId, String path, Object value) {
        ensureMetadata(documentId);
        jdbc.sql("""
                UPDATE document_metadata
                SET verification_json = JSON_SET(COALESCE(verification_json, JSON_OBJECT()), :path, CAST(:value AS JSON))
                WHERE document_id = :id
                """)
                .param("path", path).param("value", toJson(value)).param("id", documentId).update();
    }

    private void ensureMetadata(long documentId) {
        String originalName = jdbc.sql("SELECT original_name FROM documents WHERE id = :id")
                .param("id", documentId).query(String.class).optional().orElse(null);
        if (originalName == null) throw ApiException.notFound("资料不存在: " + documentId);
        jdbc.sql("INSERT IGNORE INTO document_metadata (document_id, title, lifecycle_status) VALUES (:id, :title, 'active')")
                .param("id", documentId).param("title", originalName).update();
    }

    private void requireDocument(long id) {
        Boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM documents WHERE id = :id)")
                .param("id", id).query((rs, i) -> rs.getBoolean(1)).single();
        if (!Boolean.TRUE.equals(exists)) throw ApiException.notFound("资料不存在: " + id);
    }

    private record NormalizedTag(String tag, String normalizedTag) {
    }

    private static NormalizedTag normalizeTag(String raw) {
        if (raw == null || raw.strip().isEmpty()) throw ApiException.badRequest("标签不能为空");
        String tag = raw.strip().replaceAll("[\\p{Cntrl}]", " ");
        if (tag.length() > MAX_TAG_CHARS) throw ApiException.badRequest("标签不能超过 " + MAX_TAG_CHARS + " 字");
        return new NormalizedTag(tag, tag.toLowerCase(java.util.Locale.ROOT));
    }

    private DocumentView documentRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Map<String, Object> verification = null;
        String verificationJson = rs.getString("verification_json");
        if (verificationJson != null && !verificationJson.isBlank()) {
            try {
                verification = objectMapper.readValue(verificationJson,
                        new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                        });
            } catch (JsonProcessingException ignored) {
                verification = null;
            }
        }
        return new DocumentView(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8),
                rs.getString(9), String.valueOf(rs.getTimestamp(10)),
                rs.getString("title"), rs.getString("lifecycle_status"),
                new ArrayList<>(), verification);
    }

    /** 批量装配标签（避免逐行查询）。 */
    private void attachTags(List<DocumentView> views) {
        if (views.isEmpty()) return;
        List<Long> ids = views.stream().map(DocumentView::id).toList();
        Map<Long, List<TagView>> byDocument = new LinkedHashMap<>();
        jdbc.sql("SELECT document_id, tag, source FROM document_tags WHERE document_id IN (:ids) ORDER BY id")
                .param("ids", ids)
                .query((rs, i) -> new Object[] { rs.getLong(1), new TagView(rs.getString(2), rs.getString(3)) })
                .list()
                .forEach(pair -> byDocument.computeIfAbsent((Long) pair[0], k -> new ArrayList<>())
                        .add((TagView) pair[1]));
        views.forEach(view -> {
            List<TagView> tags = byDocument.get(view.id());
            if (tags != null) view.tags().addAll(tags);
        });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
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
