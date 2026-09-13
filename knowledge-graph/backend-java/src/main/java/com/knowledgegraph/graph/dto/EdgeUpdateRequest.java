package com.knowledgegraph.graph.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * 更新关系请求（全部字段可选）。
 */
public record EdgeUpdateRequest(
        @JsonAlias({"relationType"}) String relation,
        Double weight,
        @Pattern(regexp = "active|inactive", message = "status 必须是 active 或 inactive") String status,
        Map<String, Object> properties) {
}
