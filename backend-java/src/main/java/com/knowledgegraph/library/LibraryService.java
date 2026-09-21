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
     * 删除知识库及仅属于该库的节点；共享节点及共享文档保留。
     */
    @Transactional
    public Map<String, Object> delete(long id) {
        LibraryDetail detail = getById(id);
        List<Long> ownedNodes = jdbc.sql("SELECT node_id FROM library_nodes WHERE library_id = :id")
                .param("id", id).query(Long.class).list();
        // 多主题资料可能同时被其他库引用；删除当前库前保留其原文件和证据。
        jdbc.sql("""
                UPDATE documents d SET library_id = (
                  SELECT MIN(a.library_id) FROM document_topic_assignments a
                  WHERE a.document_id = d.id AND a.library_id <> :id)
                WHERE d.library_id = :id AND EXISTS (
                  SELECT 1 FROM document_topic_assignments a WHERE a.document_id = d.id AND a.library_id <> :id)
                """).param("id", id).update();
        jdbc.sql("DELETE FROM knowledge_libraries WHERE id = :id").param("id", id).update();
        int deletedNodes = 0;
        if (!ownedNodes.isEmpty()) {
            deletedNodes = jdbc.sql("DELETE FROM knowledge_nodes WHERE id IN (:ids) AND NOT EXISTS (SELECT 1 FROM library_nodes ln WHERE ln.node_id = knowledge_nodes.id)")
                    .param("ids", ownedNodes).update();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("id", id);
        result.put("affectedNodes", detail.nodeCount());
        result.put("deletedNodes", deletedNodes);
        result.put("affectedDocuments", detail.documentCount());
        return result;
    }

    /**
     * 资料数。注意：旧库已有同名 documents 表（旧结构，course_id 列），
     * 新结构（library_id 列）只在全新库中生效，这里对两种结构都兼容。
     */
    private long countDocuments(long libraryId) {
        try {
            return jdbc.sql("SELECT COUNT(*) FROM documents d WHERE d.library_id = :id OR EXISTS (SELECT 1 FROM document_topic_assignments a WHERE a.document_id = d.id AND a.library_id = :id)")
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
                rs.getLong("edge_count"),
                aliasesOf(rs.getLong("id")));
    }

    /** 知识库别名（供 AI 自动整理按同义名称归库）。 */
    private java.util.List<String> aliasesOf(long libraryId) {
        return jdbc.sql("SELECT alias FROM library_aliases WHERE library_id = :id ORDER BY id")
                .param("id", libraryId).query((rs, i) -> rs.getString(1)).list();
    }

    /** 整体替换别名（幂等；最多 10 个，归一化去重）。 */
    @Transactional
    public LibraryDetail replaceAliases(long id, java.util.List<String> aliases) {
        getById(id);
        java.util.List<String> cleaned = new java.util.ArrayList<>();
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias == null) continue;
                String trimmed = alias.strip();
                if (trimmed.isEmpty()) continue;
                if (trimmed.length() > 200) throw ApiException.badRequest("别名不能超过 200 字");
                String normalized = trimmed.toLowerCase(java.util.Locale.ROOT);
                if (cleaned.stream().noneMatch(a -> a.equalsIgnoreCase(normalized))) cleaned.add(trimmed);
            }
        }
        if (cleaned.size() > 10) throw ApiException.badRequest("别名最多 10 个");
        jdbc.sql("DELETE FROM library_aliases WHERE library_id = :id").param("id", id).update();
        for (String alias : cleaned) {
            jdbc.sql("INSERT INTO library_aliases (library_id, alias, normalized_alias) VALUES (:id, :a, :n)")
                    .param("id", id).param("a", alias)
                    .param("n", alias.toLowerCase(java.util.Locale.ROOT)).update();
        }
        return getById(id);
    }
}
