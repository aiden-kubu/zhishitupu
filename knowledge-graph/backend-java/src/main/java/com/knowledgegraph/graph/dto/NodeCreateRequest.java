package com.knowledgegraph.graph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

/**
 * 创建节点请求。node_type 取值范围见 §11.1。
 */
public record NodeCreateRequest(
        @NotBlank(message = "name 不能为空") String name,
        String nameEn,
        @NotBlank(message = "type 不能为空")
        @Pattern(regexp = "course|chapter|knowledge|concept|method|application|other",
                message = "type 必须是 course,chapter,knowledge,concept,method,application,other 之一")
        String type,
        String definition,
        Map<String, Object> properties,
        List<String> aliases,
        Long libraryId) {
}
