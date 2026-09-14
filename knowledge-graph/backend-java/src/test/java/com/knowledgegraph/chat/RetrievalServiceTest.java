package com.knowledgegraph.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalServiceTest {

    @Test
    void extractsSpecificTermsInsteadOfQuestionWrappers() {
        assertEquals(List.of("java"), RetrievalService.extractTerms("Java 是什么？"));
        assertEquals(List.of("二进制"), RetrievalService.extractTerms("请解释二进制是什么"));
    }

    @Test
    void keepsMixedLanguageTechnicalTerms() {
        List<String> terms = RetrievalService.extractTerms("TCP 和 HTTP 有什么关系？");
        assertTrue(terms.contains("tcp"));
        assertTrue(terms.contains("http"));
    }

    @Test
    void pronounQuestionHasNoExplicitTermAndFallsBackToNodeEvidence() {
        assertTrue(RetrievalService.extractTerms("它是什么？").isEmpty());
    }

    @Test
    void quickQuestionsAreTreatedAsCurrentNodeQuestions() {
        assertTrue(RetrievalService.extractTerms("通俗解释").isEmpty());
        assertTrue(RetrievalService.extractTerms("与相邻节点对比").isEmpty());
        assertTrue(RetrievalService.extractTerms("生成练习题").isEmpty());
    }

    @Test
    void fallsBackToChineseFragmentsOnlyWhenTheExactPhraseMisses() {
        assertEquals(List.of("二进", "进制", "制补", "补码"),
                RetrievalService.expandChineseTerms(List.of("二进制补码")));
        assertEquals(List.of("tcp"), RetrievalService.expandChineseTerms(List.of("tcp")));
    }
}
