package com.knowledgegraph.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联想排序规则单元测试（§6.2）：完全命中 &gt; 前缀 &gt; 别名 &gt; 名称包含 &gt; 定义包含。
 */
class SearchRankingTest {

    @Test
    void exactNameMatchIsTier1() {
        assertEquals(SearchRanking.TIER_EXACT, SearchRanking.rank("TCP", "TCP", null, List.of(), null));
    }

    @Test
    void exactNameEnMatchIsTier1() {
        assertEquals(SearchRanking.TIER_EXACT,
                SearchRanking.rank("transmission control protocol", "传输控制协议", "Transmission Control Protocol", List.of(), null));
    }

    @Test
    void prefixMatchIsTier2() {
        assertEquals(SearchRanking.TIER_PREFIX, SearchRanking.rank("TC", "TCP", null, List.of(), null));
    }

    @Test
    void aliasMatchIsTier3() {
        assertEquals(SearchRanking.TIER_ALIAS,
                SearchRanking.rank("传输控制", "TCP", "Transmission Control Protocol", List.of("传输控制协议"), null));
    }

    @Test
    void aliasExactMatchBeatsNameContains() {
        // 别名(3) 应排在 名称包含(4) 之前
        int aliasTier = SearchRanking.rank("传输控制协议", "某协议", null, List.of("传输控制协议"), null);
        int containsTier = SearchRanking.rank("协议", "某协议", null, List.of(), null);
        assertTrue(aliasTier < containsTier);
    }

    @Test
    void nameContainsIsTier4() {
        assertEquals(SearchRanking.TIER_NAME_CONTAINS, SearchRanking.rank("协", "传输协议", null, List.of(), null));
    }

    @Test
    void definitionMatchIsTier5() {
        assertEquals(SearchRanking.TIER_DEFINITION,
                SearchRanking.rank("可靠", "TCP", null, List.of(), "面向连接的可靠传输协议"));
    }

    @Test
    void rankingIsMonotonicAcrossTiers() {
        String q = "传输";
        int exact = SearchRanking.rank(q, "传输", null, List.of(), null);
        int prefix = SearchRanking.rank(q, "传输层", null, List.of(), null);
        int alias = SearchRanking.rank(q, "TCP", null, List.of("传输控制协议"), null);
        int contains = SearchRanking.rank(q, "数据传输", null, List.of(), null);
        int definition = SearchRanking.rank(q, "TCP", null, List.of(), "负责数据传输");

        assertTrue(exact < prefix);
        assertTrue(prefix < alias);
        assertTrue(alias < contains);
        assertTrue(contains < definition);
    }

    @Test
    void matchIsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals(SearchRanking.TIER_EXACT, SearchRanking.rank("  tcp ", "TCP", null, List.of(), null));
        assertEquals(SearchRanking.TIER_EXACT, SearchRanking.rank("TCP", "  tcp  ", null, List.of(), null));
    }

    @Test
    void blankQueryNeverMatches() {
        assertEquals(SearchRanking.NO_MATCH, SearchRanking.rank("", "TCP", null, List.of(), "定义"));
        assertEquals(SearchRanking.NO_MATCH, SearchRanking.rank("   ", "TCP", null, List.of(), "定义"));
    }

    @Test
    void unrelatedCandidateDoesNotMatch() {
        assertEquals(SearchRanking.NO_MATCH, SearchRanking.rank("TCP", "UDP", null, List.of("用户数据报"), "无连接协议"));
    }
}
