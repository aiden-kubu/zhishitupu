package com.knowledgegraph.search;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.graph.GraphService;
import com.knowledgegraph.graph.dto.SubgraphData;
import com.knowledgegraph.search.dto.SuggestionItem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索服务（§13 SearchService）：
 * 联想按「完全命中 &gt; 前缀 &gt; 别名 &gt; 名称包含 &gt; 定义包含」排序，
 * 关键词搜索返回以最佳命中节点为中心的子图。
 */
@Service
public class SearchService {

    public static final int DEFAULT_SUGGESTION_LIMIT = 10;
    public static final int MAX_SUGGESTION_LIMIT = 50;

    private final JdbcClient jdbc;
    private final GraphService graphService;

    public SearchService(JdbcClient jdbc, GraphService graphService) {
        this.jdbc = jdbc;
        this.graphService = graphService;
    }

    /**
     * 联想。tier 计算与 {@link SearchRanking} 语义一致。
     */
    public List<SuggestionItem> suggestions(String q, Long libraryId, Integer limit) {
        String query = SearchRanking.normalize(q);
        if (query.isEmpty()) {
            return List.of();
        }
        int maxItems = GraphService.clamp(limit == null ? DEFAULT_SUGGESTION_LIMIT : limit, 1, MAX_SUGGESTION_LIMIT);
        String join = libraryId != null
                ? "JOIN library_nodes ln ON ln.node_id = kn.id AND ln.library_id = :libraryId"
                : "";

        String sql = """
                SELECT t.id, t.name, t.nameEn, t.type, t.degree, t.degree AS sourceCount, t.tier
                FROM (
                  SELECT kn.id,
                         kn.canonical_name AS name,
                         kn.name_en        AS nameEn,
                         kn.node_type      AS type,
                         (SELECT COUNT(*) FROM knowledge_edges e
                           WHERE e.status = 'active'
                             AND (e.source_node_id = kn.id OR e.target_node_id = kn.id)) AS degree,
                         LEAST(
                           CASE WHEN LOWER(TRIM(kn.canonical_name)) = :q
                                     OR LOWER(TRIM(COALESCE(kn.name_en, ''))) = :q THEN 1 ELSE 99 END,
                           CASE WHEN LOWER(TRIM(kn.canonical_name)) LIKE :prefix
                                     OR LOWER(TRIM(COALESCE(kn.name_en, ''))) LIKE :prefix THEN 2 ELSE 99 END,
                           CASE WHEN EXISTS (SELECT 1 FROM node_aliases na
                                              WHERE na.node_id = kn.id AND na.normalized_alias = :q) THEN 3
                                WHEN EXISTS (SELECT 1 FROM node_aliases na
                                              WHERE na.node_id = kn.id
                                                AND na.normalized_alias LIKE CONCAT('%', :q, '%')) THEN 3
                                ELSE 99 END,
                           CASE WHEN LOWER(kn.canonical_name) LIKE CONCAT('%', :q, '%')
                                     OR LOWER(TRIM(COALESCE(kn.name_en, ''))) LIKE CONCAT('%', :q, '%') THEN 4 ELSE 99 END,
                           CASE WHEN LOWER(COALESCE(kn.definition, '')) LIKE CONCAT('%', :q, '%') THEN 5 ELSE 99 END
                         ) AS tier
                  FROM knowledge_nodes kn
                  __LIBRARY_JOIN__
                ) t
                WHERE t.tier < 99
                ORDER BY t.tier ASC, t.degree DESC, t.id ASC
                LIMIT :limit
                """.replace("__LIBRARY_JOIN__", join);

        List<SuggestionItem> items = jdbc.sql(sql)
                .param("q", query)
                .param("prefix", query + "%")
                .param("libraryId", libraryId)
                .param("limit", maxItems)
                .query((rs, i) -> new SuggestionItem(
                        rs.getLong("id"), rs.getString("name"), rs.getString("nameEn"),
                        List.of(), rs.getString("type"),
                        rs.getInt("sourceCount"), rs.getInt("degree"), rs.getInt("tier")))
                .list();

        return attachAliases(items);
    }

    /**
     * 关键词搜索：以最佳命中节点为中心返回子图。无命中时返回空子图。
     */
    public SubgraphData search(String q, Long libraryId, Integer depth, Integer limit) {
        List<SuggestionItem> best = suggestions(q, libraryId, 1);
        if (best.isEmpty()) {
            return new SubgraphData(0L, List.of(), List.of(), false, 0, 0);
        }
        return graphService.neighbors(best.get(0).id(), depth, limit);
    }

    private List<SuggestionItem> attachAliases(List<SuggestionItem> items) {
        if (items.isEmpty()) {
            return items;
        }
        List<Long> ids = items.stream().map(SuggestionItem::id).toList();
        Map<Long, List<String>> aliasMap = new HashMap<>();
        jdbc.sql("SELECT node_id, alias FROM node_aliases WHERE node_id IN (:ids) ORDER BY node_id, id")
                .param("ids", ids)
                .query((rs, i) -> {
                    aliasMap.computeIfAbsent(rs.getLong("node_id"), k -> new ArrayList<>())
                            .add(rs.getString("alias"));
                    return 1;
                })
                .list();
        List<SuggestionItem> result = new ArrayList<>(items.size());
        for (SuggestionItem item : items) {
            result.add(new SuggestionItem(item.id(), item.name(), item.nameEn(),
                    aliasMap.getOrDefault(item.id(), List.of()), item.type(),
                    item.sourceCount(), item.degree(), item.tier()));
        }
        return result;
    }
}
