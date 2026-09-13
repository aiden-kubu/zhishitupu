package com.knowledgegraph.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 通用分页响应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(List<T> items, int page, int pageSize, long total, int totalPages) {

    public static <T> PageResponse<T> empty(int page, int pageSize) {
        return new PageResponse<>(List.of(), page, pageSize, 0, 0);
    }
}
