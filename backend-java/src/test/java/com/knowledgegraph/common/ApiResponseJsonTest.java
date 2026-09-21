package com.knowledgegraph.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ApiResponse JSON 形状测试（§12.1，Jackson 序列化）。
 */
class ApiResponseJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void successShapeHasNumericZeroCodeMessageDataRequestId() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.ok(Map.of("hello", "world")));

        JsonNode node = mapper.readTree(json);
        assertTrue(node.get("code").isInt(), "成功 code 应为数字");
        assertEquals(0, node.get("code").asInt());
        assertEquals("ok", node.get("message").asText());
        assertEquals("world", node.get("data").get("hello").asText());
        assertFalse(node.has("details"), "成功响应不应出现 details 字段");
        assertTrue(node.hasNonNull("requestId"), "requestId 必须存在");
    }

    @Test
    void successWithoutDataOmitsDataField() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.ok());
        JsonNode node = mapper.readTree(json);
        assertFalse(node.has("data"), "无数据时 data 字段应被省略");
        assertEquals(0, node.get("code").asInt());
    }

    @Test
    void errorShapeHasStringCodeMessageDetailsRequestId() throws Exception {
        String json = mapper.writeValueAsString(
                ApiResponse.error("DOCUMENT_PARSE_FAILED", "第 18 页解析失败", Map.of("page", 18)));

        JsonNode node = mapper.readTree(json);
        assertTrue(node.get("code").isTextual(), "失败 code 应为字符串");
        assertEquals("DOCUMENT_PARSE_FAILED", node.get("code").asText());
        assertEquals("第 18 页解析失败", node.get("message").asText());
        assertEquals(18, node.get("details").get("page").asInt());
        assertFalse(node.has("data"), "失败响应不应出现 data 字段");
        assertTrue(node.hasNonNull("requestId"));
    }

    @Test
    void errorWithoutDetailsOmitsDetailsField() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.error("NOT_FOUND", "节点不存在"));
        JsonNode node = mapper.readTree(json);
        assertEquals("NOT_FOUND", node.get("code").asText());
        assertFalse(node.has("details"), "无 details 时字段应被省略");
    }
}
