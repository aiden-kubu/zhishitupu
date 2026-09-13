package com.knowledgegraph.library.dto;

import java.time.LocalDateTime;

/**
 * 知识库（含计数，供删除影响提示，§9.1）。
 */
public record LibraryDetail(
        long id,
        String name,
        String type,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long documentCount,
        long nodeCount,
        long edgeCount,
        java.util.List<String> aliases) {
}
