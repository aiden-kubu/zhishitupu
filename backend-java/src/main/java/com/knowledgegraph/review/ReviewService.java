package com.knowledgegraph.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.extraction.ExtractionPayloadParser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 审核中心服务（§12.5、§8.8）：候选查询（对齐前端 reviewApi.ts DTO）、逐条/批量审核、事务入库。
 * commit 在单个数据库事务内完成：FOR UPDATE 锁定任务 → IMPORTING → 合并/新建节点（含别名、
 * 知识库关联、节点证据）→ 复用或创建关系（含关系证据）→ 任务与文档置 COMPLETED。
 * 任何一步失败整体回滚；重复提交被 AWAITING_REVIEW 状态守卫拒绝，不产生重复数据。
 */
@Service
public class ReviewService {

    // ---------------------------------------------------------------- DTO（与前端 reviewApi.ts 对齐）

    public record EvidenceView(long chunkId, String documentName, String locator, String excerpt) {
    }

    public record EntityCandidateView(long id, long jobId, String tempKey, String name, List<String> aliases,
                                      String nodeType, String definition, double confidence,
                                      List<Long> evidenceChunkIds, Long matchedNodeId, String matchedNodeName,
                                      String reviewStatus, List<EvidenceView> evidence) {
    }

    public record RelationCandidateView(long id, long jobId, String sourceTempKey, String targetTempKey,
                                        String sourceName, String targetName, String relationType, double confidence,
                                        List<Long> evidenceChunkIds, Long matchedEdgeId, String reviewStatus,
                                        List<EvidenceView> evidence) {
    }

    public record CommitResult(long createdNodes, long mergedNodes, long createdEdges, long skipped) {
    }

    public record UpdateEntityRequest(String reviewStatus, EditedEntity edited) {
        public record EditedEntity(String name, String nodeType, String definition, List<String> aliases) {
        }
    }

    public record UpdateRelationRequest(String reviewStatus, EditedRelation edited) {
        public record EditedRelation(String sourceTempKey, String targetTempKey, String relationType) {
        }
    }

    public record BulkActionRequest(String kind, String action, List<Long> ids) {
    }

    private static final int MAX_EXCERPT_CHARS = 160;
    private static final int MAX_NAME_CHARS = 200;
    private static final int MAX_DEFINITION_CHARS = 1000;
    private static final int MAX_RELATION_TYPE_CHARS = 100;
    private static final int MAX_ALIASES = 10;
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING", "ACCEPTED", "REJECTED", "EDITED", "MERGE");
    private static final Set<String> COMMIT_ENTITY_STATUSES = Set.of("ACCEPTED", "EDITED", "MERGE");
    private static final Set<String> COMMIT_RELATION_STATUSES = Set.of("ACCEPTED", "EDITED");

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ReviewService(JdbcClient jdbc, ObjectMapper objectMapper, TransactionTemplate transactionTemplate) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    // ---------------------------------------------------------------- 查询

    public List<EntityCandidateView> listEntities(long jobId) {
        requireJobExists(jobId);
        record Row(long id, String tempKey, String name, String aliasesJson, String nodeType, String definition,
                   Double confidence, String evidenceJson, Long matchedNodeId, String matchedNodeName,
                   String reviewStatus) {
        }
        List<Row> rows = jdbc.sql("""
                        SELECT e.id, e.temp_key, e.name, e.aliases_json, e.node_type, e.definition, e.confidence,
                               e.evidence_chunk_ids_json, e.matched_node_id, kn.canonical_name AS matched_name,
                               e.review_status
                        FROM entity_candidates e
                        LEFT JOIN knowledge_nodes kn ON kn.id = e.matched_node_id
                        WHERE e.job_id = :jobId ORDER BY e.id
                        """)
                .param("jobId", jobId)
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getString("temp_key"), rs.getString("name"),
                        rs.getString("aliases_json"), rs.getString("node_type"), rs.getString("definition"),
                        rs.getObject("confidence") == null ? null : rs.getDouble("confidence"),
                        rs.getString("evidence_chunk_ids_json"),
                        rs.getObject("matched_node_id") == null ? null : rs.getLong("matched_node_id"),
                        rs.getString("matched_name"), rs.getString("review_status")))
                .list();
        List<EntityCandidateView> views = new ArrayList<>();
        for (Row row : rows) {
            List<Long> chunkIds = parseLongArray(row.evidenceJson());
            views.add(new EntityCandidateView(row.id(), jobId, row.tempKey(), row.name(),
                    parseStringArray(row.aliasesJson()), row.nodeType(), row.definition(),
                    orDefault(row.confidence()), chunkIds, row.matchedNodeId(), row.matchedNodeName(),
                    row.reviewStatus(), loadEvidence(chunkIds)));
        }
        return views;
    }

    public List<RelationCandidateView> listRelations(long jobId) {
        requireJobExists(jobId);
        record Row(long id, String sourceTempKey, String targetTempKey, String relationType, Double confidence,
                   String evidenceJson, Long matchedEdgeId, String reviewStatus) {
        }
        List<Row> rows = jdbc.sql("""
                        SELECT id, source_temp_key, target_temp_key, relation_type, confidence,
                               evidence_chunk_ids_json, matched_edge_id, review_status
                        FROM relation_candidates WHERE job_id = :jobId ORDER BY id
                        """)
                .param("jobId", jobId)
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getString("source_temp_key"),
                        rs.getString("target_temp_key"), rs.getString("relation_type"),
                        rs.getObject("confidence") == null ? null : rs.getDouble("confidence"),
                        rs.getString("evidence_chunk_ids_json"),
                        rs.getObject("matched_edge_id") == null ? null : rs.getLong("matched_edge_id"),
                        rs.getString("review_status")))
                .list();
        Map<String, String> nameByTempKey = loadEntityNames(jobId);
        List<RelationCandidateView> views = new ArrayList<>();
        for (Row row : rows) {
            List<Long> chunkIds = parseLongArray(row.evidenceJson());
            views.add(new RelationCandidateView(row.id(), jobId, row.sourceTempKey(), row.targetTempKey(),
                    nameByTempKey.get(row.sourceTempKey()), nameByTempKey.get(row.targetTempKey()),
                    row.relationType(), orDefault(row.confidence()), chunkIds, row.matchedEdgeId(),
                    row.reviewStatus(), loadEvidence(chunkIds)));
        }
        return views;
    }

    // ---------------------------------------------------------------- 逐条审核

    public EntityCandidateView updateEntity(long id, UpdateEntityRequest request) {
        EntityRow entity = requireEntity(id);
        requireJobAwaitingReview(entity.jobId());
        String reviewStatus = normalizeStatus(request == null ? null : request.reviewStatus());
        if (reviewStatus != null && !REVIEW_STATUSES.contains(reviewStatus)) {
            throw ApiException.badRequest("非法的审核状态: " + reviewStatus);
        }
        UpdateEntityRequest.EditedEntity edited = request == null ? null : request.edited();
        if (edited != null) {
            validateEditedEntity(edited);
            jdbc.sql("UPDATE entity_candidates SET edited_payload_json = :payload WHERE id = :id")
                    .param("payload", toJson(Map.of(
                            "name", edited.name().trim(),
                            "nodeType", normalizeType(edited.nodeType()),
                            "definition", edited.definition() == null ? "" : edited.definition(),
                            "aliases", edited.aliases() == null ? List.of() : edited.aliases())))
                    .param("id", id).update();
        }
        if (reviewStatus != null) {
            jdbc.sql("UPDATE entity_candidates SET review_status = :status WHERE id = :id")
                    .param("status", reviewStatus).param("id", id).update();
        }
        return listEntities(entity.jobId()).stream().filter(v -> v.id() == id).findFirst().orElseThrow();
    }

    public RelationCandidateView updateRelation(long id, UpdateRelationRequest request) {
        RelationRow relation = requireRelation(id);
        requireJobAwaitingReview(relation.jobId());
        String reviewStatus = normalizeStatus(request == null ? null : request.reviewStatus());
        if (reviewStatus != null && !REVIEW_STATUSES.contains(reviewStatus)) {
            throw ApiException.badRequest("非法的审核状态: " + reviewStatus);
        }
        UpdateRelationRequest.EditedRelation edited = request == null ? null : request.edited();
        if (edited != null) {
            validateEditedRelation(relation.jobId(), edited);
            jdbc.sql("UPDATE relation_candidates SET edited_payload_json = :payload WHERE id = :id")
                    .param("payload", toJson(Map.of(
                            "sourceTempKey", edited.sourceTempKey().trim(),
                            "targetTempKey", edited.targetTempKey().trim(),
                            "relationType", edited.relationType().trim())))
                    .param("id", id).update();
        }
        if (reviewStatus != null) {
            jdbc.sql("UPDATE relation_candidates SET review_status = :status WHERE id = :id")
                    .param("status", reviewStatus).param("id", id).update();
        }
        return listRelations(relation.jobId()).stream().filter(v -> v.id() == id).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- 批量审核

    public Map<String, Object> bulkAction(long jobId, BulkActionRequest request) {
        requireJobExists(jobId);
        requireJobAwaitingReview(jobId);
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            throw ApiException.badRequest("缺少候选 id 列表");
        }
        String table;
        if ("entity".equals(request.kind())) {
            table = "entity_candidates";
        } else if ("relation".equals(request.kind())) {
            table = "relation_candidates";
        } else {
            throw ApiException.badRequest("非法的批量目标类型: " + request.kind());
        }
        String action;
        if ("accept".equals(request.action())) {
            action = "ACCEPTED";
        } else if ("reject".equals(request.action())) {
            action = "REJECTED";
        } else {
            throw ApiException.badRequest("非法的批量操作: " + request.action());
        }
        int updated = jdbc.sql("UPDATE %s SET review_status = :status WHERE job_id = :jobId AND id IN (:ids)"
                        .formatted(table))
                .param("status", action).param("jobId", jobId).param("ids", request.ids())
                .update();
        return Map.of("updated", updated);
    }

    // ---------------------------------------------------------------- 确认入库（单事务）

    public CommitResult commit(long jobId) {
        CommitResult result = transactionTemplate.execute(status -> doCommit(jobId));
        return result == null ? new CommitResult(0, 0, 0, 0) : result;
    }

    private CommitResult doCommit(long jobId) {
        // 1. 锁定并校验任务状态（FOR UPDATE 防并发重复提交）
        String stage = jdbc.sql("SELECT stage FROM ingestion_jobs WHERE id = :id FOR UPDATE")
                .param("id", jobId).query(String.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("处理任务不存在: " + jobId));
        if (!"AWAITING_REVIEW".equals(stage)) {
            throw new ApiException(409, ErrorCodes.CONFLICT,
                    "任务当前状态为 " + stage + "，仅「等待审核」任务可以确认入库");
        }
        // 2. IMPORTING（96）
        jdbc.sql("UPDATE ingestion_jobs SET stage = 'IMPORTING', status = 'IMPORTING', progress = 99 WHERE id = :id")
                .param("id", jobId).update();

        long documentId = jdbc.sql("SELECT document_id FROM ingestion_jobs WHERE id = :id")
                .param("id", jobId).query(Long.class).single();
        long libraryId = jdbc.sql("SELECT library_id FROM documents WHERE id = :id")
                .param("id", documentId).query(Long.class).single();
        List<Long> documentChunkIdList = jdbc.sql("""
                        SELECT c.id FROM document_chunks c JOIN document_units u ON u.id = c.unit_id
                        WHERE u.document_id = :id
                        """)
                .param("id", documentId)
                .query((rs, i) -> rs.getLong(1))
                .list();
        Set<Long> documentChunkIds = new LinkedHashSet<>(documentChunkIdList);
        Map<Long, String> chunkExcerptById = loadChunkExcerpts(documentChunkIds);

        long createdNodes = 0;
        long mergedNodes = 0;
        long skipped = 0;
        long createdEdges = 0;

        // 3-6. 候选节点：合并到已有节点或新建（含别名、知识库关联、节点证据）
        Map<String, Long> nodeIdByTempKey = new LinkedHashMap<>();
        Map<String, Long> nodeIdByNameKey = new LinkedHashMap<>();
        Set<String> writtenEvidencePairs = new HashSet<>();
        for (EntityRow entity : listEntityCandidates(jobId)) {
            if (!COMMIT_ENTITY_STATUSES.contains(entity.reviewStatus())) {
                skipped++;
                continue;
            }
            EffectiveEntity effective = effectiveEntity(entity);
            if ("MERGE".equals(entity.reviewStatus()) && entity.matchedNodeId() == null
                    && matchNodeInTx(keysOf(effective)) == null) {
                skipped++; // 明确要求合并但没有可合并对象：跳过而不是创建
                continue;
            }
            NodeResolution resolution = resolveNodeId(entity, effective, nodeIdByNameKey, jobId);
            if (resolution == null) {
                skipped++;
                continue;
            }
            nodeIdByTempKey.put(entity.tempKey(), resolution.nodeId());
            if (resolution.created()) {
                createdNodes++;
            } else {
                mergedNodes++;
            }
            List<Long> targets = jdbc.sql("SELECT library_id FROM document_topic_assignments WHERE document_id = :d AND temp_key = :k")
                    .param("d", documentId).param("k", entity.tempKey()).query(Long.class).list();
            if (targets.isEmpty()) targets = List.of(libraryId);
            for (long target : targets) insertLibraryNode(target, resolution.nodeId());
            List<String> aliasesToInsert = new ArrayList<>(effective.aliases());
            if (effective.nameEn() != null && !effective.nameEn().isBlank()) {
                aliasesToInsert.add(effective.nameEn());
            }
            insertAliases(resolution.nodeId(), aliasesToInsert);
            insertNodeEvidence(resolution.nodeId(), effective.evidenceChunkIds(), documentChunkIds,
                    chunkExcerptById, entity.confidence(), writtenEvidencePairs);
        }

        // 7-9. 候选关系：复用或创建；两端节点不存在时跳过，不产生孤儿边
        for (RelationRow relation : listRelationCandidates(jobId)) {
            if (!COMMIT_RELATION_STATUSES.contains(relation.reviewStatus())) {
                skipped++;
                continue;
            }
            EffectiveRelation effective = effectiveRelation(relation);
            Long sourceId = nodeIdByTempKey.get(effective.sourceTempKey());
            Long targetId = nodeIdByTempKey.get(effective.targetTempKey());
            if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
                skipped++;
                continue;
            }
            EdgeResolution edge = resolveEdgeId(relation, sourceId, targetId, effective.relationType(), jobId);
            if (edge.created()) {
                createdEdges++;
            }
            insertEdgeEvidence(edge.edgeId(), effective.evidenceChunkIds(), documentChunkIds,
                    chunkExcerptById, relation.confidence());
        }

        // 10. 完成
        jdbc.sql("""
                        UPDATE ingestion_jobs SET stage = 'COMPLETED', status = 'COMPLETED', progress = 100,
                              finished_at = :now, error_code = NULL, error_message = NULL
                        WHERE id = :id
                        """)
                .param("now", LocalDateTime.now()).param("id", jobId).update();
        jdbc.sql("UPDATE documents SET status = 'COMPLETED' WHERE id = :id").param("id", documentId).update();
        return new CommitResult(createdNodes, mergedNodes, createdEdges, skipped);
    }

    // ---------------------------------------------------------------- 入库内部

    private record EffectiveEntity(String name, String nameEn, String nodeType, String definition,
                                   List<String> aliases, List<Long> evidenceChunkIds) {
    }

    private record EffectiveRelation(String sourceTempKey, String targetTempKey, String relationType,
                                     List<Long> evidenceChunkIds) {
    }

    private record NodeResolution(long nodeId, boolean created) {
    }

    private record EdgeResolution(long edgeId, boolean created) {
    }

    private EffectiveEntity effectiveEntity(EntityRow entity) {
        List<Long> evidence = parseLongArray(entity.evidenceChunkIdsJson());
        if ("EDITED".equals(entity.reviewStatus()) && entity.editedPayloadJson() != null) {
            JsonNode payload = readTree(entity.editedPayloadJson());
            if (payload != null && payload.isObject()) {
                return new EffectiveEntity(
                        // 名称不做截断：DB 列宽是最终约束，异常数据触发整体回滚
                        orDefaultText(cleanLine(textOrNull(payload.path("name")), 1000), entity.name()),
                        null,
                        normalizeType(orDefaultText(textOrNull(payload.path("nodeType")), entity.nodeType())),
                        orDefaultText(multilineOrNull(textOrNull(payload.path("definition"))), entity.definition()),
                        stringArrayOrNull(payload.path("aliases")) != null
                                ? stringArrayOrNull(payload.path("aliases"))
                                : parseStringArray(entity.aliasesJson()),
                        evidence);
            }
        }
        return new EffectiveEntity(entity.name(), null, entity.nodeType(), entity.definition(),
                parseStringArray(entity.aliasesJson()), evidence);
    }

    private EffectiveRelation effectiveRelation(RelationRow relation) {
        List<Long> evidence = parseLongArray(relation.evidenceChunkIdsJson());
        if ("EDITED".equals(relation.reviewStatus()) && relation.editedPayloadJson() != null) {
            JsonNode payload = readTree(relation.editedPayloadJson());
            if (payload != null && payload.isObject()) {
                return new EffectiveRelation(
                        orDefaultText(cleanLine(textOrNull(payload.path("sourceTempKey")), 50), relation.sourceTempKey()),
                        orDefaultText(cleanLine(textOrNull(payload.path("targetTempKey")), 50), relation.targetTempKey()),
                        orDefaultText(cleanLine(textOrNull(payload.path("relationType")), MAX_RELATION_TYPE_CHARS),
                                relation.relationType()),
                        evidence);
            }
        }
        return new EffectiveRelation(relation.sourceTempKey(), relation.targetTempKey(), relation.relationType(),
                evidence);
    }

    /** 节点解析：同事务内 内存映射 → matched_node_id → 库内精确匹配 → 新建。 */
    private NodeResolution resolveNodeId(EntityRow entity, EffectiveEntity effective,
                                         Map<String, Long> nodeIdByNameKey, long jobId) {
        Set<String> keys = keysOf(effective);
        for (String key : keys) {
            Long cached = nodeIdByNameKey.get(key);
            if (cached != null) {
                return new NodeResolution(cached, false);
            }
        }
        if (entity.matchedNodeId() != null) {
            Boolean exists = existsNode(entity.matchedNodeId());
            if (Boolean.TRUE.equals(exists)) {
                keys.forEach(key -> nodeIdByNameKey.putIfAbsent(key, entity.matchedNodeId()));
                return new NodeResolution(entity.matchedNodeId(), false);
            }
        }
        Long existing = matchNodeInTx(keys);
        if (existing != null) {
            keys.forEach(key -> nodeIdByNameKey.putIfAbsent(key, existing));
            return new NodeResolution(existing, false);
        }
        GeneratedKeyHolder keys2 = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO knowledge_nodes (canonical_name, name_en, node_type, definition, properties_json, status)
                        VALUES (:name, :nameEn, :type, :definition, :properties, 'active')
                        """)
                .param("name", effective.name())
                .param("nameEn", effective.nameEn())
                .param("type", effective.nodeType())
                .param("definition", effective.definition())
                .param("properties", toJson(Map.of("origin", "document_extraction", "jobId", jobId)))
                .update(keys2);
        Long newNodeId = keys2.getKey().longValue();
        keys.forEach(key -> nodeIdByNameKey.putIfAbsent(key, newNodeId));
        return new NodeResolution(newNodeId, true);
    }

    private Boolean existsNode(long nodeId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM knowledge_nodes WHERE id = :id)")
                .param("id", nodeId).query((rs, i) -> rs.getBoolean(1)).single();
    }

    private Long matchNodeInTx(Set<String> normalizedKeys) {
        for (String key : normalizedKeys) {
            if (key.isEmpty()) {
                continue;
            }
            Long id = jdbc.sql("""
                            SELECT id FROM (
                              (SELECT id FROM knowledge_nodes WHERE LOWER(TRIM(canonical_name)) = :v ORDER BY id LIMIT 1)
                              UNION ALL
                              (SELECT id FROM knowledge_nodes WHERE name_en IS NOT NULL AND LOWER(TRIM(name_en)) = :v ORDER BY id LIMIT 1)
                              UNION ALL
                              (SELECT node_id AS id FROM node_aliases WHERE normalized_alias = :v ORDER BY node_id, id LIMIT 1)
                            ) hits LIMIT 1
                            """)
                    .param("v", key)
                    .query(Long.class)
                    .optional()
                    .orElse(null);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private void insertAliases(long nodeId, List<String> aliases) {
        Set<String> seen = new HashSet<>();
        for (String alias : aliases) {
            String trimmed = alias == null ? "" : alias.trim();
            if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_CHARS) {
                continue;
            }
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                continue;
            }
            jdbc.sql("INSERT IGNORE INTO node_aliases (node_id, alias, normalized_alias, language) VALUES (:n, :a, :na, 'zh')")
                    .param("n", nodeId).param("a", trimmed).param("na", normalized).update();
        }
    }

    private void insertLibraryNode(long libraryId, long nodeId) {
        jdbc.sql("INSERT IGNORE INTO library_nodes (library_id, node_id) VALUES (:l, :n)")
                .param("l", libraryId).param("n", nodeId).update();
    }

    private void insertNodeEvidence(long nodeId, List<Long> chunkIds, Set<Long> documentChunkIds,
                                    Map<Long, String> excerpts, Double confidence, Set<String> writtenPairs) {
        for (Long chunkId : chunkIds) {
            // 证据 chunk 必须属于当前文档（§五.6），并保证同一节点+chunk 不重复写
            if (!documentChunkIds.contains(chunkId) || !writtenPairs.add(nodeId + ":" + chunkId)) {
                continue;
            }
            jdbc.sql("INSERT INTO node_evidence (node_id, chunk_id, evidence_text, confidence) VALUES (:n, :c, :t, :conf)")
                    .param("n", nodeId).param("c", chunkId)
                    .param("t", excerpt(excerpts.get(chunkId))).param("conf", confidence)
                    .update();
        }
    }

    private EdgeResolution resolveEdgeId(RelationRow relation, long sourceId, long targetId,
                                         String relationType, long jobId) {
        if (relation.matchedEdgeId() != null) {
            Boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM knowledge_edges WHERE id = :id)")
                    .param("id", relation.matchedEdgeId()).query((rs, i) -> rs.getBoolean(1)).single();
            if (Boolean.TRUE.equals(exists)) {
                return new EdgeResolution(relation.matchedEdgeId(), false);
            }
        }
        Long existing = jdbc.sql("""
                        SELECT id FROM knowledge_edges
                        WHERE source_node_id = :s AND target_node_id = :t AND relation_type = :r AND status = 'active'
                        ORDER BY id LIMIT 1
                        """)
                .param("s", sourceId).param("t", targetId).param("r", relationType)
                .query(Long.class).optional().orElse(null);
        if (existing != null) {
            return new EdgeResolution(existing, false);
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO knowledge_edges (source_node_id, target_node_id, relation_type, weight, properties_json, status)
                        VALUES (:s, :t, :r, 1.0, :properties, 'active')
                        """)
                .param("s", sourceId).param("t", targetId).param("r", relationType)
                .param("properties", toJson(Map.of("origin", "document_extraction", "jobId", jobId)))
                .update(keys);
        return new EdgeResolution(keys.getKey().longValue(), true);
    }

    private void insertEdgeEvidence(long edgeId, List<Long> chunkIds, Set<Long> documentChunkIds,
                                    Map<Long, String> excerpts, Double confidence) {
        for (Long chunkId : chunkIds) {
            if (!documentChunkIds.contains(chunkId)) {
                continue;
            }
            jdbc.sql("INSERT INTO edge_evidence (edge_id, chunk_id, evidence_text, confidence) VALUES (:e, :c, :t, :conf)")
                    .param("e", edgeId).param("c", chunkId)
                    .param("t", excerpt(excerpts.get(chunkId))).param("conf", confidence)
                    .update();
        }
    }

    // ---------------------------------------------------------------- 编辑校验

    private void validateEditedEntity(UpdateEntityRequest.EditedEntity edited) {
        if (edited.name() == null || edited.name().isBlank()) {
            throw ApiException.badRequest("节点名称不能为空");
        }
        if (edited.name().trim().length() > MAX_NAME_CHARS) {
            throw ApiException.badRequest("节点名称不能超过 " + MAX_NAME_CHARS + " 字");
        }
        String type = normalizeType(edited.nodeType());
        if (!"other".equals(type) && !ExtractionPayloadParser.NODE_TYPE_WHITELIST.contains(type)) {
            throw ApiException.badRequest("非法的节点类型: " + edited.nodeType());
        }
        if (edited.definition() == null || edited.definition().isBlank()) {
            throw ApiException.badRequest("节点定义不能为空");
        }
        if (edited.definition().length() > MAX_DEFINITION_CHARS) {
            throw ApiException.badRequest("节点定义不能超过 " + MAX_DEFINITION_CHARS + " 字");
        }
        if (edited.aliases() != null && edited.aliases().size() > MAX_ALIASES) {
            throw ApiException.badRequest("别名数量不能超过 " + MAX_ALIASES + " 个");
        }
    }

    private void validateEditedRelation(long jobId, UpdateRelationRequest.EditedRelation edited) {
        if (isBlank(edited.sourceTempKey()) || isBlank(edited.targetTempKey())) {
            throw ApiException.badRequest("关系的起点与终点 tempKey 不能为空");
        }
        if (edited.relationType() == null || edited.relationType().isBlank()) {
            throw ApiException.badRequest("关系类型不能为空");
        }
        if (edited.relationType().length() > MAX_RELATION_TYPE_CHARS) {
            throw ApiException.badRequest("关系类型不能超过 " + MAX_RELATION_TYPE_CHARS + " 字");
        }
        Set<String> jobTempKeys = new HashSet<>();
        jdbc.sql("SELECT temp_key FROM entity_candidates WHERE job_id = :id")
                .param("id", jobId).query((rs, i) -> rs.getString(1)).list()
                .forEach(key -> {
                    if (key != null) {
                        jobTempKeys.add(key);
                    }
                });
        if (!jobTempKeys.contains(edited.sourceTempKey()) || !jobTempKeys.contains(edited.targetTempKey())) {
            throw ApiException.badRequest("关系的起点/终点必须是本任务的候选节点 tempKey");
        }
    }

    private void requireJobAwaitingReview(long jobId) {
        String stage = jdbc.sql("SELECT stage FROM ingestion_jobs WHERE id = :id")
                .param("id", jobId).query(String.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("处理任务不存在: " + jobId));
        if (!"AWAITING_REVIEW".equals(stage)) {
            throw new ApiException(409, ErrorCodes.CONFLICT,
                    "任务当前状态为 " + stage + "，其候选结果不可修改（不允许修改其他任务的候选数据）");
        }
    }

    private void requireJobExists(long jobId) {
        Boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM ingestion_jobs WHERE id = :id)")
                .param("id", jobId).query((rs, i) -> rs.getBoolean(1)).single();
        if (!Boolean.TRUE.equals(exists)) {
            throw ApiException.notFound("处理任务不存在: " + jobId);
        }
    }

    // ---------------------------------------------------------------- 数据访问

    record EntityRow(long id, long jobId, String tempKey, String name, String aliasesJson,
                     String nodeType, String definition, Double confidence, String evidenceChunkIdsJson,
                     Long matchedNodeId, String reviewStatus, String editedPayloadJson) {
    }

    record RelationRow(long id, long jobId, String sourceTempKey, String targetTempKey, String relationType,
                       Double confidence, String evidenceChunkIdsJson, Long matchedEdgeId, String reviewStatus,
                       String editedPayloadJson) {
    }

    private static final String ENTITY_SELECT = """
            SELECT id, job_id, temp_key, name, aliases_json, node_type, definition, confidence,
                   evidence_chunk_ids_json, matched_node_id, review_status, edited_payload_json
            FROM entity_candidates
            """;

    private List<EntityRow> listEntityCandidates(long jobId) {
        return jdbc.sql(ENTITY_SELECT + " WHERE job_id = :jobId ORDER BY id")
                .param("jobId", jobId)
                .query(ReviewService::entityRow)
                .list();
    }

    private EntityRow requireEntity(long id) {
        return jdbc.sql(ENTITY_SELECT + " WHERE id = :id")
                .param("id", id)
                .query(ReviewService::entityRow)
                .optional()
                .orElseThrow(() -> ApiException.notFound("候选节点不存在: " + id));
    }

    private static EntityRow entityRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new EntityRow(rs.getLong("id"), rs.getLong("job_id"), rs.getString("temp_key"),
                rs.getString("name"), rs.getString("aliases_json"),
                rs.getString("node_type"), rs.getString("definition"),
                rs.getObject("confidence") == null ? null : rs.getDouble("confidence"),
                rs.getString("evidence_chunk_ids_json"),
                rs.getObject("matched_node_id") == null ? null : rs.getLong("matched_node_id"),
                rs.getString("review_status"), rs.getString("edited_payload_json"));
    }

    private static final String RELATION_SELECT = """
            SELECT id, job_id, source_temp_key, target_temp_key, relation_type, confidence,
                   evidence_chunk_ids_json, matched_edge_id, review_status, edited_payload_json
            FROM relation_candidates
            """;

    private List<RelationRow> listRelationCandidates(long jobId) {
        return jdbc.sql(RELATION_SELECT + " WHERE job_id = :jobId ORDER BY id")
                .param("jobId", jobId)
                .query(ReviewService::relationRow)
                .list();
    }

    private RelationRow requireRelation(long id) {
        return jdbc.sql(RELATION_SELECT + " WHERE id = :id")
                .param("id", id)
                .query(ReviewService::relationRow)
                .optional()
                .orElseThrow(() -> ApiException.notFound("候选关系不存在: " + id));
    }

    private static RelationRow relationRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new RelationRow(rs.getLong("id"), rs.getLong("job_id"),
                rs.getString("source_temp_key"), rs.getString("target_temp_key"), rs.getString("relation_type"),
                rs.getObject("confidence") == null ? null : rs.getDouble("confidence"),
                rs.getString("evidence_chunk_ids_json"),
                rs.getObject("matched_edge_id") == null ? null : rs.getLong("matched_edge_id"),
                rs.getString("review_status"), rs.getString("edited_payload_json"));
    }

    private Map<String, String> loadEntityNames(long jobId) {
        Map<String, String> names = new LinkedHashMap<>();
        jdbc.sql("SELECT temp_key, name FROM entity_candidates WHERE job_id = :id")
                .param("id", jobId)
                .query((rs, i) -> new String[] { rs.getString(1), rs.getString(2) })
                .list()
                .forEach(pair -> {
                    if (pair[0] != null) {
                        names.put(pair[0], pair[1]);
                    }
                });
        return names;
    }

    /** 证据：真实文档名 + source_locator + 片段摘录，绝不伪造。 */
    private List<EvidenceView> loadEvidence(List<Long> chunkIds) {
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT c.id, IFNULL(d.original_name, ''), u.source_locator, c.content
                        FROM document_chunks c
                        JOIN document_units u ON u.id = c.unit_id
                        JOIN documents d ON d.id = u.document_id
                        WHERE c.id IN (:ids)
                        ORDER BY c.id
                        """)
                .param("ids", chunkIds)
                .query((rs, i) -> new EvidenceView(rs.getLong(1), rs.getString(2), rs.getString(3),
                        excerpt(rs.getString(4))))
                .list();
    }

    private Map<Long, String> loadChunkExcerpts(Set<Long> chunkIds) {
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> excerpts = new LinkedHashMap<>();
        jdbc.sql("SELECT id, content FROM document_chunks WHERE id IN (:ids)")
                .param("ids", chunkIds)
                .query((rs, i) -> new String[] { String.valueOf(rs.getLong(1)), rs.getString(2) })
                .list()
                .forEach(pair -> excerpts.put(Long.parseLong(pair[0]), pair[1]));
        return excerpts;
    }

    // ---------------------------------------------------------------- 工具

    private String normalizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "other";
        }
        String type = rawType.trim().toLowerCase(Locale.ROOT);
        return ExtractionPayloadParser.NODE_TYPE_WHITELIST.contains(type) ? type : "other";
    }

    private String normalizeStatus(String rawStatus) {
        return rawStatus == null || rawStatus.isBlank() ? null : rawStatus.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Long> parseLongArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> values = objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
            return values == null ? List.of() : values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").strip();
        return normalized.length() <= MAX_EXCERPT_CHARS ? normalized : normalized.substring(0, MAX_EXCERPT_CHARS) + "…";
    }

    private static Set<String> keysOf(EffectiveEntity entity) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(normalize(entity.name()));
        if (entity.nameEn() != null) {
            keys.add(normalize(entity.nameEn()));
        }
        for (String alias : entity.aliases()) {
            keys.add(normalize(alias));
        }
        return keys;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asText();
    }

    private static String cleanLine(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replaceAll("\\p{Cntrl}+", " ").strip();
        return cleaned.length() <= maxChars ? cleaned : cleaned.substring(0, maxChars);
    }

    private static String multilineOrNull(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("\r\n", "\n").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static List<String> stringArrayOrNull(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = textOrNull(item);
            if (text != null && !text.isBlank()) {
                values.add(text.strip());
            }
        }
        return values;
    }

    private static String orDefaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static double orDefault(Double value) {
        return value == null ? 0.5 : value;
    }
}
