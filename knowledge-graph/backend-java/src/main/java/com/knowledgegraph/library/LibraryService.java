package com.knowledgegraph.library;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.library.dto.LibraryCreateRequest;
import com.knowledgegraph.library.dto.LibraryDetail;
import com.knowledgegraph.library.dto.LibraryUpdateRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库 CRUD（§13 LibraryService）。
 */
@Service
public class LibraryService {

    private final JdbcClient jdbc;

    public LibraryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<LibraryDetail> list() {
        return jdbc.sql("""
                        SELECT kl.*,
                               (SELECT COUNT(*) FROM library_nodes ln WHERE ln.library_id = kl.id) AS node_count,
                               (SELECT COUNT(*) FROM knowledge_edges e
                                 WHERE e.status = 'active'
                                   AND EXISTS (SELECT 1 FROM library_nodes la WHERE la.library_id = kl.id AND la.node_id = e.source_node_id)
                                   AND EXISTS (SELECT 1 FROM library_nodes lb WHERE lb.library_id = kl.id AND lb.node_id = e.target_node_id)) AS edge_count
                        FROM knowledge_libraries kl
                        ORDER BY kl.id
                        """)
                .query(libraryRowMapper())
                .list();
    }

    public LibraryDetail getById(long id) {
        return jdbc.sql("""
                        SELECT kl.*,
                               (SELECT COUNT(*) FROM library_nodes ln WHERE ln.library_id = kl.id) AS node_count,
                               (SELECT COUNT(*) FROM knowledge_edges e
                                 WHERE e.status = 'active'
                                   AND EXISTS (SELECT 1 FROM library_nodes la WHERE la.library_id = kl.id AND la.node_id = e.source_node_id)
                                   AND EXISTS (SELECT 1 FROM library_nodes lb WHERE lb.library_id = kl.id AND lb.node_id = e.target_node_id)) AS edge_count
                        FROM knowledge_libraries kl WHERE kl.id = :id
                        """)
                .param("id", id)
                .query(libraryRowMapper())
                .optional()
                .orElseThrow(() -> ApiException.notFound("知识库不存在: " + id));
    }

    @Transactional
    public LibraryDetail create(LibraryCreateRequest req) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO knowledge_libraries (name, type, description, status)
                        VALUES (:name, :type, :description, 'active')
                        """)
                .param("name", req.name().trim())
                .param("type", req.type())
                .param("description", req.description())
                .update(keys);
        return getById(keys.getKey().longValue());
    }

    @Transactional
    public LibraryDetail update(long id, LibraryUpdateRequest req) {
        getById(id);
        jdbc.sql("""
                        UPDATE knowledge_libraries
                        SET name        = COALESCE(:name, name),
                            type        = COALESCE(:type, type),
                            description = CASE WHEN :descriptionProvided THEN :description ELSE description END,
                            status      = COALESCE(:status, status)
                        WHERE id = :id
                        """)
                .param("name", req.name() != null ? req.name().trim() : null)
                .param("type", req.type())
                .param("descriptionProvided", req.description() != null)
                .param("description", req.description())
                .param("status", req.status())
                .param("id", id)
                .update();
        return getById(id);
    }

    /**
     * 删除知识库：仅删除知识库本身与其节点关联（library_nodes，外键级联）；
     * 知识节点为全局资源，不受影响。
     */
    @Transactional
    public Map<String, Object> delete(long id) {
        LibraryDetail detail = getById(id);
        jdbc.sql("DELETE FROM knowledge_libraries WHERE id = :id").param("id", id).update();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("id", id);
        result.put("affectedNodes", detail.nodeCount());
        result.put("affectedDocuments", detail.documentCount());
        return result;
    }

    /**
     * 资料数。注意：旧库已有同名 documents 表（旧结构，course_id 列），
     * 新结构（library_id 列）只在全新库中生效，这里对两种结构都兼容。
     */
    private long countDocuments(long libraryId) {
        try {
            return jdbc.sql("SELECT COUNT(*) FROM documents WHERE library_id = :id")
                    .param("id", libraryId)
                    .query((rs, i) -> rs.getLong(1))
                    .single();
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            try {
                return jdbc.sql("SELECT COUNT(*) FROM documents WHERE course_id = :id")
                        .param("id", libraryId)
                        .query((rs, i) -> rs.getLong(1))
                        .single();
            } catch (Exception ignored) {
                return 0;
            }
        } catch (Exception ignored) {
            return 0;
        }
    }

    private RowMapper<LibraryDetail> libraryRowMapper() {
        return (rs, i) -> new LibraryDetail(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime(),
                countDocuments(rs.getLong("id")),
                rs.getLong("node_count"),
                rs.getLong("edge_count"));
    }
}
