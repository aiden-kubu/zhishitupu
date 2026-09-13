package com.knowledgegraph.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.graph.dto.NodeCreateRequest;
import com.knowledgegraph.graph.dto.NodeDetail;
import com.knowledgegraph.graph.dto.NodeUpdateRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 节点 CRUD（§13 GraphService）。删除在事务内级联清理边、证据、别名与知识库关联。
 */
@Service
public class NodeService {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public NodeService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public NodeDetail getById(long id) {
        NodeDetail node = jdbc.sql("""
                        SELECT kn.id, kn.canonical_name, kn.name_en, kn.node_type, kn.definition,
                               kn.properties_json, kn.status, kn.created_at, kn.updated_at,
                               (SELECT COUNT(*) FROM knowledge_edges e
                                 WHERE e.status = 'active'
                                   AND (e.source_node_id = kn.id OR e.target_node_id = kn.id)) AS degree,
                               (SELECT COUNT(*) FROM node_evidence ne WHERE ne.node_id = kn.id) AS source_count
                        FROM knowledge_nodes kn WHERE kn.id = :id
                        """)
                .param("id", id)
                .query((rs, i) -> new NodeDetail(
                        rs.getLong("id"), rs.getString("canonical_name"), rs.getString("name_en"),
                        rs.getString("node_type"), rs.getString("definition"),
                        toJsonMap(rs.getString("properties_json")), rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime(),
                        List.of(), rs.getInt("degree"), rs.getInt("source_count"), List.of()))
                .optional()
                .orElseThrow(() -> ApiException.notFound("节点不存在: " + id));

        List<String> aliases = jdbc.sql("SELECT alias FROM node_aliases WHERE node_id = :id ORDER BY id")
                .param("id", id)
                .query((rs, i) -> rs.getString(1))
                .list();
        List<NodeDetail.LibraryRef> libraries = jdbc.sql("""
                        SELECT kl.id, kl.name FROM knowledge_libraries kl
                        JOIN library_nodes ln ON ln.library_id = kl.id
                        WHERE ln.node_id = :id ORDER BY kl.id
                        """)
                .param("id", id)
                .query((rs, i) -> new NodeDetail.LibraryRef(rs.getLong("id"), rs.getString("name")))
                .list();
        return new NodeDetail(node.id(), node.name(), node.nameEn(), node.type(), node.definition(),
                node.properties(), node.status(), node.createdAt(), node.updatedAt(),
                aliases, node.degree(), node.sourceCount(), libraries);
    }

    @Transactional
    public NodeDetail create(NodeCreateRequest req) {
        long id;
        try {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("""
                            INSERT INTO knowledge_nodes (canonical_name, name_en, node_type, definition, properties_json, status)
                            VALUES (:name, :nameEn, :type, :definition, :properties, 'active')
                            """)
                    .param("name", req.name().trim())
                    .param("nameEn", blankToNull(req.nameEn()))
                    .param("type", req.type())
                    .param("definition", req.definition())
                    .param("properties", toJsonString(req.properties()))
                    .update(keys);
            id = keys.getKey().longValue();
        } catch (DuplicateKeyException ex) {
            // 预留：节点名唯一约束如启用时映射为 409
            throw new ApiException(409, com.knowledgegraph.common.ErrorCodes.CONFLICT, "节点已存在");
        }
        if (req.aliases() != null) {
            insertAliases(id, req.aliases());
        }
        if (req.libraryId() != null) {
            requireLibrary(req.libraryId());
            jdbc.sql("INSERT INTO library_nodes (library_id, node_id, sort_order) VALUES (:libraryId, :nodeId, :nodeId)")
                    .param("libraryId", req.libraryId())
                    .param("nodeId", id)
                    .update();
        }
        return getById(id);
    }

    @Transactional
    public NodeDetail update(long id, NodeUpdateRequest req) {
        getById(id);
        jdbc.sql("""
                        UPDATE knowledge_nodes
                        SET canonical_name = COALESCE(:name, canonical_name),
                            name_en        = CASE WHEN :nameEnProvided THEN :nameEn ELSE name_en END,
                            node_type      = COALESCE(:type, node_type),
                            definition     = CASE WHEN :definitionProvided THEN :definition ELSE definition END,
                            properties_json = CASE WHEN :propertiesProvided THEN :properties ELSE properties_json END,
                            status         = COALESCE(:status, status)
                        WHERE id = :id
                        """)
                .param("name", req.name() != null ? req.name().trim() : null)
                .param("nameEnProvided", req.nameEn() != null)
                .param("nameEn", blankToNull(req.nameEn()))
                .param("type", req.type())
                .param("definitionProvided", req.definition() != null)
                .param("definition", req.definition())
                .param("propertiesProvided", req.properties() != null)
                .param("properties", toJsonString(req.properties()))
                .param("status", req.status())
                .param("id", id)
                .update();
        if (req.aliases() != null) {
            jdbc.sql("DELETE FROM node_aliases WHERE node_id = :id").param("id", id).update();
            insertAliases(id, req.aliases());
        }
        return getById(id);
    }

    @Transactional
    public Map<String, Object> delete(long id) {
        getById(id);
        // 事务内级联清理：关系证据、关系、节点证据、别名、知识库关联，最后删除节点本身
        jdbc.sql("""
                        DELETE FROM edge_evidence
                        WHERE edge_id IN (SELECT id FROM knowledge_edges
                                          WHERE source_node_id = :id OR target_node_id = :id)
                        """)
                .param("id", id)
                .update();
        jdbc.sql("DELETE FROM knowledge_edges WHERE source_node_id = :id OR target_node_id = :id")
                .param("id", id)
                .update();
        jdbc.sql("DELETE FROM node_evidence WHERE node_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM node_aliases WHERE node_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM library_nodes WHERE node_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM knowledge_nodes WHERE id = :id").param("id", id).update();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("id", id);
        return result;
    }

    private void insertAliases(long nodeId, List<String> aliases) {
        Set<String> normalizedSeen = new LinkedHashSet<>();
        for (String alias : new LinkedHashSet<>(aliases)) {
            if (alias == null || alias.isBlank()) {
                continue;
            }
            String trimmed = alias.trim();
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (!normalizedSeen.add(normalized)) {
                continue; // 同批次内去重，避免违反唯一约束
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

    private void requireLibrary(long libraryId) {
        Boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM knowledge_libraries WHERE id = :id)")
                .param("id", libraryId)
                .query((rs, i) -> rs.getBoolean(1))
                .single();
        if (!Boolean.TRUE.equals(exists)) {
            throw ApiException.notFound("知识库不存在: " + libraryId);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJsonString(Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("properties 无法序列化为 JSON");
        }
    }

    private Map<String, Object> toJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
