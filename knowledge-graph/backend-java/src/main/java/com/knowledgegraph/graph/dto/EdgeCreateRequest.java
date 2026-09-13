package com.knowledgegraph.graph.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 创建关系请求。兼容 sourceId/sourceNodeId、relation/relationType 两种命名。
 */
public record EdgeCreateRequest(
        @NotNull(message = "sourceId 不能为空") @JsonAlias({"sourceNodeId"}) Long sourceId,
        @NotNull(message = "targetId 不能为空") @JsonAlias({"targetNodeId"}) Long targetId,
        @NotBlank(message = "relation 不能为空") @JsonAlias({"relationType"}) String relation,
        Double weight,
        Map<String, Object> properties) {
}
