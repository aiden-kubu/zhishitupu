package com.knowledgegraph.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.graph.dto.EdgeCreateRequest;
import com.knowledgegraph.graph.dto.EdgeDetail;
import com.knowledgegraph.graph.dto.EdgeUpdateRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 关系 CRUD（§13 GraphService）。唯一约束 source+target+relation 冲突返回 409。
 */
@Service
public class EdgeService {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public EdgeService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public EdgeDetail getById(long id) {
        return jdbc.sql("""
                        SELECT e.id, e.source_node_id, e.target_node_id, e.relation_type, e.weight,
                               e.status, e.properties_json, e.created_at, e.updated_at
                        FROM knowledge_edges e WHERE e.id = :id
                        """)
                .param("id", id)
                .query((rs, i) -> new EdgeDetail(
                        rs.getLong("id"), rs.getLong("source_node_id"), rs.getLong("target_node_id"),
                        rs.getString("relation_type"), rs.getDouble("weight"), rs.getString("status"),
                        toJsonMap(rs.getString("properties_json")),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()))
                .optional()
                .orElseThrow(() -> ApiException.notFound("关系不存在: " + id));
    }

    @Transactional
    public EdgeDetail create(EdgeCreateRequest req) {
        if (Objects.equals(req.sourceId(), req.targetId())) {
            throw ApiException.badRequest("sourceId 与 targetId 不能相同");
        }
        requireNode(req.sourceId());
        requireNode(req.targetId());
        double weight = req.weight() == null ? 1.0 : req.weight();
        long id;
        try {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("""
                            INSERT INTO knowledge_edges (source_node_id, target_node_id, relation_type, weight, properties_json, status)
                            VALUES (:source, :target, :relation, :weight, :properties, 'active')
                            """)
                    .param("source", req.sourceId())
                    .param("target", req.targetId())
                    .param("relation", req.relation().trim())
                    .param("weight", weight)
                    .param("properties", toJsonString(req.properties()))
                    .update(keys);
            id = keys.getKey().longValue();
        } catch (DuplicateKeyException ex) {
            throw duplicateRelation();
        }
        return getById(id);
    }

    @Transactional
    public EdgeDetail update(long id, EdgeUpdateRequest req) {
        EdgeDetail current = getById(id);
        String relation = req.relation() != null ? req.relation().trim() : current.relation();
        double weight = req.weight() != null ? req.weight() : current.weight();
        String status = req.status() != null ? req.status() : current.status();
        String properties = req.properties() != null ? toJsonString(req.properties()) : null;
        try {
            jdbc.sql("""
                            UPDATE knowledge_edges
                            SET relation_type = :relation,
                                weight = :weight,
                                status = :status,
                                properties_json = COALESCE(:properties, properties_json)
                            WHERE id = :id
                            """)
                    .param("relation", relation)
                    .param("weight", weight)
                    .param("status", status)
                    .param("properties", properties)
                    .param("id", id)
                    .update();
        } catch (DuplicateKeyException ex) {
            throw duplicateRelation();
        }
        return getById(id);
    }

    @Transactional
    public Map<String, Object> delete(long id) {
        getById(id);
        // edge_evidence 通过外键级联删除；显式清理保证语义清楚且在同一事务内
        jdbc.sql("DELETE FROM edge_evidence WHERE edge_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM knowledge_edges WHERE id = :id").param("id", id).update();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("id", id);
        return result;
    }

    private void requireNode(long nodeId) {
        Boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM knowledge_nodes WHERE id = :id)")
                .param("id", nodeId)
                .query((rs, i) -> rs.getBoolean(1))
                .single();
        if (!Boolean.TRUE.equals(exists)) {
            throw ApiException.notFound("节点不存在: " + nodeId);
        }
    }

    private static ApiException duplicateRelation() {
        return new ApiException(409, com.knowledgegraph.common.ErrorCodes.DUPLICATE_RELATION,
                "相同起点、终点和类型的关系已存在");
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
