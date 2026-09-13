package com.knowledgegraph.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.search.AiExpandPayloadParser.NormalizedNode;
import com.knowledgegraph.search.AiExpandPayloadParser.NormalizedPayload;
import com.knowledgegraph.search.AiExpandPayloadParser.NormalizedRelation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 生成结果的入库（任务约定 §15/§16）。
 * 写库前在同一事务内再次查库去重（canonical_name / name_en / normalized_alias，大小写与首尾空白不敏感）；
 * 已存在的关系目标节点直接复用原 ID；关系写入走唯一约束 + INSERT IGNORE 防重复；
 * 节点、别名、知识库关联与关系在同一事务提交，任一步失败全部回滚。
 */
@Service
public class AiExpandStore {

    public record PersistOutcome(long nodeId, boolean created, int relationsCreated) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AiExpandStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** libraryId 缺省时落库到第一个知识库所用的查询。 */
    private static final String FIRST_LIBRARY_SQL =
            "SELECT id FROM knowledge_libraries WHERE status = 'active' ORDER BY id ASC LIMIT 1";

    public void requireLibrary(long libraryId) {
        Boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM knowledge_libraries WHERE id = :id)")
                .param("id", libraryId)
                .query((rs, i) -> rs.getBoolean(1))
                .single();
        if (!Boolean.TRUE.equals(exists)) {
            throw ApiException.notFound("知识库不存在: " + libraryId);
        }
    }

    /**
     * 本地去重（§15.1-3）：canonical_name → name_en → normalized_alias 依次精确匹配
     * （LOWER(TRIM(...))，查询词先在 Java 侧做同样归一）。命中返回节点 ID。
     */
    public Long findExistingNodeId(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        Long id = jdbc.sql("""
                        SELECT id FROM knowledge_nodes
                        WHERE LOWER(TRIM(canonical_name)) = :q ORDER BY id ASC LIMIT 1
                        """)
                .param("q", normalized)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (id != null) {
            return id;
        }
        id = jdbc.sql("""
                        SELECT id FROM knowledge_nodes
                        WHERE name_en IS NOT NULL AND LOWER(TRIM(name_en)) = :q ORDER BY id ASC LIMIT 1
                        """)
                .param("q", normalized)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (id != null) {
            return id;
        }
        return jdbc.sql("SELECT node_id FROM node_aliases WHERE normalized_alias = :q ORDER BY id ASC LIMIT 1")
                .param("q", normalized)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    /**
     * 事务内写库前复查：对查询词与模型返回的标准名/英文名逐一查重（别名不参与拦截，
     * 只在写入时让位给已有归属），命中任一即视为节点已存在，不重复创建。
     */
    Long findExistingByAnyName(String query, NormalizedPayload payload) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(query);
        candidates.add(payload.node().name());
        if (payload.node().nameEn() != null) {
            candidates.add(payload.node().nameEn());
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Long id = findExistingNodeId(candidate);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** 同一事务写入节点、别名、知识库关联与关系（§15.10）。 */
    @Transactional
    public PersistOutcome persist(String query, Long libraryId, NormalizedPayload payload,
                                  Map<String, Object> nodeProperties,
                                  Map<String, Object> targetNodeProperties,
                                  Map<String, Object> edgeProperties) {
        // 1. 入库前再次查库，防止并发重复（§15.9）
        Long existing = findExistingByAnyName(query, payload);
        if (existing != null) {
            return new PersistOutcome(existing, false, 0);
        }

        // 2. 主节点：立即 active，供下次搜索直接命中（§16）
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO knowledge_nodes (canonical_name, name_en, node_type, definition, properties_json, status)
                        VALUES (:name, :nameEn, :type, :definition, :properties, 'active')
                        """)
                .param("name", payload.node().name())
                .param("nameEn", payload.node().nameEn())
                .param("type", payload.node().type())
                .param("definition", payload.node().definition())
                .param("properties", toJson(nodeProperties))
                .update(keys);
        long nodeId = keys.getKey().longValue();

        // 3. 别名：模型别名 + 原始查询词（保证下次搜索同一名称直接命中本地库，§12.7）
        insertAliases(nodeId, payload.node(), query);

        // 4. 知识库关联：libraryId 缺省时取第一个知识库；系统尚无知识库则跳过
        long targetLibraryId = libraryId != null ? libraryId : firstLibraryId();
        if (targetLibraryId > 0) {
            jdbc.sql("INSERT IGNORE INTO library_nodes (library_id, node_id, sort_order) VALUES (:libraryId, :nodeId, 0)")
                    .param("libraryId", targetLibraryId)
                    .param("nodeId", nodeId)
                    .update();
        }

        // 5. 关系：目标节点已存在则复用原 ID（§15.5），唯一约束 + INSERT IGNORE 防重复边（§15.6）
        int relationsCreated = 0;
        for (NormalizedRelation relation : payload.relations()) {
            long targetId = resolveTargetNodeId(relation, targetNodeProperties);
            if (targetId == nodeId) {
                continue;
            }
            int inserted = jdbc.sql("""
                            INSERT IGNORE INTO knowledge_edges
                                (source_node_id, target_node_id, relation_type, weight, properties_json, status)
                            VALUES (:source, :target, :relation, 1.0, :properties, 'active')
                            """)
                    .param("source", nodeId)
                    .param("target", targetId)
                    .param("relation", relation.relationType())
                    .param("properties", toJson(edgeProperties))
                    .update();
            relationsCreated += inserted;
        }
        return new PersistOutcome(nodeId, true, relationsCreated);
    }

    /** 目标节点查重（canonical_name / name_en / 别名均可命中复用），不存在则新建（同样带 AI 来源标记）。 */
    private long resolveTargetNodeId(NormalizedRelation relation, Map<String, Object> targetNodeProperties) {
        Long existing = findExistingNodeId(relation.targetName());
        if (existing == null && relation.targetNameEn() != null) {
            existing = findExistingNodeId(relation.targetNameEn());
        }
        if (existing != null) {
            return existing;
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO knowledge_nodes (canonical_name, name_en, node_type, definition, properties_json, status)
                        VALUES (:name, :nameEn, :type, :definition, :properties, 'active')
                        """)
                .param("name", relation.targetName())
                .param("nameEn", relation.targetNameEn())
                .param("type", relation.targetType())
                .param("definition", relation.targetDefinition())
                .param("properties", toJson(targetNodeProperties))
                .update(keys);
        return keys.getKey().longValue();
    }

    private void insertAliases(long nodeId, NormalizedNode node, String query) {
        String nameLower = node.name().trim().toLowerCase(Locale.ROOT);
        String nameEnLower = node.nameEn() == null ? null : node.nameEn().trim().toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>(node.aliases());
        candidates.add(query);
        Set<String> seen = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String trimmed = candidate.trim();
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (normalized.equals(nameLower) || normalized.equals(nameEnLower) || !seen.add(normalized)) {
                continue;
            }
            // 别名已被其他节点占用时不写入，保持「别名 → 节点」映射唯一
            Long owner = jdbc.sql("SELECT node_id FROM node_aliases WHERE normalized_alias = :n ORDER BY id ASC LIMIT 1")
                    .param("n", normalized)
                    .query(Long.class)
                    .optional()
                    .orElse(null);
            if (owner != null) {
                continue;
            }
            jdbc.sql("""
                            INSERT IGNORE INTO node_aliases (node_id, alias, normalized_alias, language)
                            VALUES (:nodeId, :alias, :normalized, 'zh')
                            """)
                    .param("nodeId", nodeId)
                    .param("alias", trimmed)
                    .param("normalized", normalized)
                    .update();
        }
    }

    private long firstLibraryId() {
        return jdbc.sql(FIRST_LIBRARY_SQL)
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApiException(500, com.knowledgegraph.common.ErrorCodes.INTERNAL_ERROR,
                    "AI 生成来源信息序列化失败");
        }
    }
}
