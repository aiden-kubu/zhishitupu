package com.knowledgegraph.graph.dto;

import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

/**
 * 更新节点请求（全部字段可选，仅更新提供的字段；aliases 提供时整体替换）。
 */
public record NodeUpdateRequest(
        String name,
        String nameEn,
        @Pattern(regexp = "course|chapter|knowledge|concept|method|application|other",
                message = "type 必须是 course,chapter,knowledge,concept,method,application,other 之一")
        String type,
        String definition,
        Map<String, Object> properties,
        List<String> aliases,
        @Pattern(regexp = "active|inactive", message = "status 必须是 active 或 inactive")
        String status) {
}
