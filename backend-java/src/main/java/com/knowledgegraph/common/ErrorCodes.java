package com.knowledgegraph.common;

/**
 * 统一错误码字面值（§12.1）。HTTP 状态码由 ApiException 携带。
 */
public final class ErrorCodes {

    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String DUPLICATE_RELATION = "DUPLICATE_RELATION";
    public static final String CONFLICT = "CONFLICT";
    public static final String NOT_IMPLEMENTED = "NOT_IMPLEMENTED";
    public static final String LLM_NOT_CONFIGURED = "LLM_NOT_CONFIGURED";
    public static final String LLM_AUTH_FAILED = "LLM_AUTH_FAILED";
    public static final String LLM_UNREACHABLE = "LLM_UNREACHABLE";
    public static final String LLM_BAD_RESPONSE = "LLM_BAD_RESPONSE";
    public static final String DOCUMENT_PARSE_FAILED = "DOCUMENT_PARSE_FAILED";
    public static final String DOCUMENT_NO_EXTRACTABLE_TEXT = "DOCUMENT_NO_EXTRACTABLE_TEXT";
    public static final String DOCUMENT_OCR_REQUIRED = "DOCUMENT_OCR_REQUIRED";
    public static final String DOCUMENT_OCR_FAILED = "DOCUMENT_OCR_FAILED";
    public static final String DB_UNAVAILABLE = "DB_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}
