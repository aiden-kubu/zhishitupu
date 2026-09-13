package com.knowledgegraph.graph.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 节点详情响应。
 */
public record NodeDetail(
        long id,
        String name,
        String nameEn,
        String type,
        String definition,
        Map<String, Object> properties,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> aliases,
        int degree,
        int sourceCount,
        List<LibraryRef> libraries) {

    public record LibraryRef(long id, String name) {
    }
}
