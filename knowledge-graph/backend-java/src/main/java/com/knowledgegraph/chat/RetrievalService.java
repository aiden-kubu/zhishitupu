package com.knowledgegraph.chat;

import com.knowledgegraph.graph.dto.NodeDetail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 问答的只读检索层。知识图谱是导航骨架，已解析的 document_chunks 才是可复用原文语料。
 * 不写入、不重跑解析；只返回已完成且仍在用资料中的少量原文分段。
 */
@Service
public class RetrievalService {

    static final int MAX_SOURCES = 8;
    static final int MAX_CONTEXT_CHARS = 8_000;
    private static final Pattern LATIN_TERM = Pattern.compile("[A-Za-z][A-Za-z0-9_+#.-]*");
    private static final Pattern CHINESE_TERM = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final List<String> QUESTION_WORDS = List.of("与相邻节点对比", "相邻节点对比", "生成练习题", "通俗解释", "请介绍", "请解释", "什么是", "是什么", "介绍", "解释", "为什么", "如何", "怎么", "一下", "有关", "相关", "知识", "问题", "对比", "练习题", "生成", "通俗", "与");

    public record RetrievedChunk(long chunkId, long documentId, String documentName,
                                 String unitType, int unitIndex, String sourceLocator,
                                 String content, String contentHash, boolean directEvidence) {
    }

    private record Candidate(RetrievedChunk chunk, boolean sameLibrary) {
    }

    private final JdbcClient jdbc;

    public RetrievalService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RetrievedChunk> retrieve(long nodeId, NodeDetail node, String question) {
        List<String> terms = extractTerms(question);
        boolean explicitQuestion = !terms.isEmpty();
        if (!explicitQuestion) {
            terms = nodeTerms(node);
        }
        if (terms.isEmpty()) {
            return List.of();
        }

        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        for (Candidate candidate : findTermMatches(nodeId, terms)) {
            candidates.putIfAbsent(candidate.chunk().chunkId(), candidate);
        }
        // PDF 分段常在词组中插入空格、换行或标点。完整词组没有命中时，才回退到中文二字片段，
        // 避免常规精确查询被宽泛片段淹没，也让全文而非图谱候选成为事实来源。
        List<String> scoreTerms = terms;
        if (explicitQuestion && candidates.isEmpty()) {
            List<String> fallbackTerms = expandChineseTerms(terms);
            if (!fallbackTerms.equals(terms)) {
                scoreTerms = fallbackTerms;
                for (Candidate candidate : findTermMatches(nodeId, fallbackTerms)) {
                    candidates.putIfAbsent(candidate.chunk().chunkId(), candidate);
                }
            }
        }
        // 代词/承接式问题才可用当前节点的证据兜底，避免“Java 是什么”被“栈”的证据污染。
        if (!explicitQuestion) {
            for (Candidate candidate : findDirectEvidence(nodeId)) {
                candidates.putIfAbsent(candidate.chunk().chunkId(), candidate);
            }
        }

        List<Candidate> sorted = new ArrayList<>(candidates.values());
        List<String> finalScoreTerms = scoreTerms;
        sorted.sort(Comparator.comparingInt((Candidate candidate) -> score(candidate, finalScoreTerms, explicitQuestion)).reversed()
                .thenComparing(candidate -> candidate.chunk().chunkId()));

        Set<String> hashes = new HashSet<>();
        List<RetrievedChunk> result = new ArrayList<>();
        int chars = 0;
        for (Candidate candidate : sorted) {
            RetrievedChunk chunk = candidate.chunk();
            if (chunk.content() == null || chunk.content().isBlank()
                    || !hashes.add(chunk.contentHash() == null ? "id:" + chunk.chunkId() : chunk.contentHash())) {
                continue;
            }
            if (result.size() >= MAX_SOURCES || chars + chunk.content().length() > MAX_CONTEXT_CHARS) {
                continue;
            }
            result.add(chunk);
            chars += chunk.content().length();
        }
        return result;
    }

    private List<Candidate> findTermMatches(long nodeId, List<String> terms) {
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) where.append(" OR ");
            where.append("LOCATE(:term").append(i).append(", LOWER(c.content)) > 0");
        }
        JdbcClient.StatementSpec statement = jdbc.sql("""
                SELECT c.id AS chunk_id, d.id AS document_id, COALESCE(m.title, d.original_name) AS document_name,
                       u.unit_type, u.unit_index, u.source_locator, c.content, c.content_hash,
                       EXISTS (
                           SELECT 1 FROM library_nodes ln
                           WHERE ln.node_id = :nodeId AND (
                               ln.library_id = d.library_id OR EXISTS (
                                   SELECT 1 FROM document_topic_assignments dta
                                   WHERE dta.document_id = d.id AND dta.library_id = ln.library_id
                               )
                           )
                       ) AS same_library
                FROM document_chunks c
                JOIN document_units u ON u.id = c.unit_id
                JOIN documents d ON d.id = u.document_id
                LEFT JOIN document_metadata m ON m.document_id = d.id
                WHERE d.status = 'COMPLETED'
                  AND (m.lifecycle_status IS NULL OR m.lifecycle_status = 'active')
                  AND (%s)
                LIMIT 80
                """.formatted(where)).param("nodeId", nodeId);
        for (int i = 0; i < terms.size(); i++) {
            statement = statement.param("term" + i, terms.get(i));
        }
        return statement.query((rs, row) -> new Candidate(new RetrievedChunk(
                        rs.getLong("chunk_id"), rs.getLong("document_id"), rs.getString("document_name"),
                        rs.getString("unit_type"), rs.getInt("unit_index"), rs.getString("source_locator"),
                        rs.getString("content"), rs.getString("content_hash"), false),
                rs.getBoolean("same_library"))).list();
    }

    private List<Candidate> findDirectEvidence(long nodeId) {
        return jdbc.sql("""
                        SELECT c.id AS chunk_id, d.id AS document_id, COALESCE(m.title, d.original_name) AS document_name,
                               u.unit_type, u.unit_index, u.source_locator, c.content, c.content_hash
                        FROM node_evidence ne
                        JOIN document_chunks c ON c.id = ne.chunk_id
                        JOIN document_units u ON u.id = c.unit_id
                        JOIN documents d ON d.id = u.document_id
                        LEFT JOIN document_metadata m ON m.document_id = d.id
                        WHERE ne.node_id = :nodeId AND d.status = 'COMPLETED'
                          AND (m.lifecycle_status IS NULL OR m.lifecycle_status = 'active')
                        ORDER BY ne.confidence DESC, ne.id DESC
                        LIMIT 24
                        """)
                .param("nodeId", nodeId)
                .query((rs, row) -> new Candidate(new RetrievedChunk(
                        rs.getLong("chunk_id"), rs.getLong("document_id"), rs.getString("document_name"),
                        rs.getString("unit_type"), rs.getInt("unit_index"), rs.getString("source_locator"),
                        rs.getString("content"), rs.getString("content_hash"), true), true)).list();
    }

    private static int score(Candidate candidate, List<String> terms, boolean explicitQuestion) {
        String content = candidate.chunk().content().toLowerCase(Locale.ROOT);
        int score = candidate.sameLibrary() ? 20 : 0;
        if (candidate.chunk().directEvidence()) score += 80;
        for (String term : terms) {
            int from = 0;
            while ((from = content.indexOf(term, from)) >= 0) {
                score += explicitQuestion ? 100 : 30;
                from += term.length();
            }
        }
        return score;
    }

    static List<String> extractTerms(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        LinkedHashMap<String, Boolean> terms = new LinkedHashMap<>();
        Matcher latin = LATIN_TERM.matcher(normalized);
        while (latin.find()) addTerm(terms, latin.group());
        Matcher chinese = CHINESE_TERM.matcher(normalized);
        while (chinese.find()) {
            String value = chinese.group();
            for (String word : QUESTION_WORDS) value = value.replace(word, "");
            addTerm(terms, value);
        }
        return terms.keySet().stream().limit(4).toList();
    }

    static List<String> expandChineseTerms(List<String> terms) {
        LinkedHashMap<String, Boolean> expanded = new LinkedHashMap<>();
        for (String term : terms) {
            if (term.matches("[\\p{IsHan}]{3,}")) {
                for (int i = 0; i < term.length() - 1 && expanded.size() < 8; i++) {
                    expanded.putIfAbsent(term.substring(i, i + 2), Boolean.TRUE);
                }
            }
        }
        return expanded.isEmpty() ? terms : expanded.keySet().stream().toList();
    }

    private static List<String> nodeTerms(NodeDetail node) {
        LinkedHashMap<String, Boolean> terms = new LinkedHashMap<>();
        addNodeTerm(terms, node.name());
        if (node.nameEn() != null) addNodeTerm(terms, node.nameEn());
        if (node.aliases() != null) node.aliases().forEach(alias -> addNodeTerm(terms, alias));
        return terms.keySet().stream().limit(4).toList();
    }

    private static void addTerm(Map<String, Boolean> target, String value) {
        addTerm(target, value, false);
    }

    private static void addNodeTerm(Map<String, Boolean> target, String value) {
        addTerm(target, value, true);
    }

    private static void addTerm(Map<String, Boolean> target, String value, boolean allowSingleChinese) {
        if (value == null) return;
        String term = value.trim().toLowerCase(Locale.ROOT);
        if ((term.length() < 2 && !(allowSingleChinese && term.matches("\\p{IsHan}"))) || QUESTION_WORDS.contains(term)) return;
        target.putIfAbsent(term, Boolean.TRUE);
    }
}
