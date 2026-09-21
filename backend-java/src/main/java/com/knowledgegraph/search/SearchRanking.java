package com.knowledgegraph.search;

import java.util.List;
import java.util.Locale;

/**
 * 搜索联想排序的纯逻辑（§6.2）：
 * 完全命中(1) &gt; 前缀(2) &gt; 别名(3) &gt; 名称包含(4) &gt; 定义包含(5)。
 * 与 SearchService 中的 SQL tier 计算语义保持一致，便于单元测试。
 */
public final class SearchRanking {

    public static final int TIER_EXACT = 1;
    public static final int TIER_PREFIX = 2;
    public static final int TIER_ALIAS = 3;
    public static final int TIER_NAME_CONTAINS = 4;
    public static final int TIER_DEFINITION = 5;
    public static final int NO_MATCH = Integer.MAX_VALUE;

    private SearchRanking() {
    }

    /** 归一化：去首尾空白 + 小写。 */
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 返回候选节点对查询的最佳命中层级；未命中返回 {@link #NO_MATCH}。
     */
    public static int rank(String query, String name, String nameEn, List<String> aliases, String definition) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return NO_MATCH;
        }
        String n = normalize(name);
        String en = normalize(nameEn);

        if (n.equals(q) || en.equals(q)) {
            return TIER_EXACT;
        }
        if (n.startsWith(q) || en.startsWith(q)) {
            return TIER_PREFIX;
        }
        if (aliases != null) {
            for (String alias : aliases) {
                String na = normalize(alias);
                if (na.equals(q) || na.contains(q)) {
                    return TIER_ALIAS;
                }
            }
        }
        if (n.contains(q) || en.contains(q)) {
            return TIER_NAME_CONTAINS;
        }
        if (normalize(definition).contains(q)) {
            return TIER_DEFINITION;
        }
        return NO_MATCH;
    }
}
