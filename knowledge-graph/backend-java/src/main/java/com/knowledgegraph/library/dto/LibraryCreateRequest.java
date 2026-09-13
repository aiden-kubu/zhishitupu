package com.knowledgegraph.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 创建知识库请求。
 */
public record LibraryCreateRequest(
        @NotBlank(message = "name 不能为空") String name,
        @NotBlank(message = "type 不能为空")
        @Pattern(regexp = "course|book|topic", message = "type 必须是 course,book,topic 之一")
        String type,
        String description) {
}
