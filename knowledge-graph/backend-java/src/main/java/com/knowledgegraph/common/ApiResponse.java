package com.knowledgegraph.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * §12.1 统一响应包络。
 * 成功：code 为数字 0；失败：code 为字符串错误码。
 * requestId 由 RequestIdFilter 写入 MDC，每个请求生成一次。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(Object code, String message, T data, Object details, String requestId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, null, RequestIdFilter.currentRequestId());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(Object code, String message) {
        return error(code, message, null);
    }

    public static <T> ApiResponse<T> error(Object code, String message, Object details) {
        return new ApiResponse<>(code, message, null, details, RequestIdFilter.currentRequestId());
    }
}
