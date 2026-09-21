package com.knowledgegraph.search.dto;

/**
 * POST /api/search/ai-expand 请求体（任务约定 §13）。
 * libraryId 可选：指定写入的知识库；缺省时由后端落入第一个知识库。
 */
public record AiExpandRequest(String query, Long libraryId) {
}
