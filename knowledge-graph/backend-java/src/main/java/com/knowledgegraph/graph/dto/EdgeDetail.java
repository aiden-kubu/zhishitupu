package com.knowledgegraph.graph.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 关系详情响应。
 */
public record EdgeDetail(
        long id,
        long source,
        long target,
        String relation,
        double weight,
        String status,
        Map<String, Object> properties,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
