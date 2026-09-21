package com.knowledgegraph.common;

/**
 * 业务异常：携带 HTTP 状态码、错误码与用户可读消息。
 */
public class ApiException extends RuntimeException {

    private final int httpStatus;
    private final String code;
    private final transient Object details;

    public ApiException(int httpStatus, String code, String message) {
        this(httpStatus, code, message, null);
    }

    public ApiException(int httpStatus, String code, String message, Object details) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.details = details;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, ErrorCodes.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, ErrorCodes.INVALID_ARGUMENT, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, ErrorCodes.CONFLICT, message);
    }
}
