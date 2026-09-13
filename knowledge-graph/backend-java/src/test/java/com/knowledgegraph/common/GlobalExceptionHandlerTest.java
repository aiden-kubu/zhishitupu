package com.knowledgegraph.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 参数校验与异常 → HTTP 状态码 / 错误码 的映射测试（MockMvc standalone，不依赖数据库）。
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {

        public record CreateRequest(@NotBlank(message = "name 不能为空") String name,
                                    @NotNull(message = "type 不能为空") String type) {
        }

        @PostMapping("/api/probe/validate")
        public Object create(@Valid @RequestBody CreateRequest request) {
            return ApiResponse.ok(request);
        }

        @GetMapping("/api/probe/require")
        public Object require(@RequestParam("q") String q) {
            return ApiResponse.ok(q);
        }

        @GetMapping("/api/probe/notfound")
        public Object notFound() {
            throw ApiException.notFound("节点不存在: 999");
        }

        @GetMapping("/api/probe/conflict")
        public Object conflict() {
            throw new ApiException(409, ErrorCodes.DUPLICATE_RELATION, "相同关系已存在");
        }

        @GetMapping("/api/probe/unimplemented")
        public Object unimplemented() {
            throw new ApiException(501, ErrorCodes.NOT_IMPLEMENTED, "阶段 D 启用");
        }

        @GetMapping("/api/probe/config")
        public Object configMissing() {
            throw new ApiException(503, ErrorCodes.LLM_NOT_CONFIGURED, "LLM 尚未配置");
        }

        @GetMapping("/api/probe/boom")
        public Object boom() {
            throw new IllegalStateException("unexpected");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("参数校验失败"))
                .andExpect(jsonPath("$.details.name").value("name 不能为空"))
                .andExpect(jsonPath("$.details.type").value("type 不能为空"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void missingParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/probe/require"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void notFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/probe/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("节点不存在: 999"));
    }

    @Test
    void conflictReturns409() throws Exception {
        mockMvc.perform(get("/api/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RELATION"));
    }

    @Test
    void notImplementedReturns501() throws Exception {
        mockMvc.perform(get("/api/probe/unimplemented"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    @Test
    void configMissingReturns503() throws Exception {
        mockMvc.perform(get("/api/probe/config"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_NOT_CONFIGURED"));
    }

    @Test
    void unexpectedErrorReturns500WithoutStacktrace() throws Exception {
        mockMvc.perform(get("/api/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }

    @Test
    void everyResponseCarriesRequestIdHeaderAndBody() throws Exception {
        var result = mockMvc.perform(get("/api/probe/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        String header = result.getResponse().getHeader("X-Request-Id");
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(header, com.jayway.jsonpath.JsonPath.read(body, "$.requestId"),
                "响应体 requestId 必须与 X-Request-Id 头一致");
    }
}
