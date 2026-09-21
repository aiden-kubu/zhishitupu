package com.knowledgegraph.library.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 更新知识库请求（全部字段可选）。
 */
public record LibraryUpdateRequest(
        String name,
        @Pattern(regexp = "course|book|topic", message = "type 必须是 course,book,topic 之一")
        String type,
        String description,
        @Pattern(regexp = "active|inactive", message = "status 必须是 active 或 inactive")
        String status) {
}
