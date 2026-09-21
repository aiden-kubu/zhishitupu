package com.knowledgegraph.chat;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.common.GlobalExceptionHandler;
import com.knowledgegraph.common.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 无历史问答接口的路由与包络测试（MockMvc standalone + Mockito，不依赖数据库）。
 * 覆盖：新接口正常返回、空问题 400、LLM 错误码透传、证据不足透传，
 * 以及旧会话式路由（chat/sessions 系列）已全部移除。
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void askReturnsUnifiedEnvelope() throws Exception {
        when(chatService.ask(eq(13L), any())).thenReturn(
                new ChatService.ChatMessageView("assistant", "HTTP 是基于 TCP 的应用层协议。", List.of(), false));
        mockMvc.perform(post("/api/nodes/13/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"HTTP是什么\",\"depth\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.role").value("assistant"))
                .andExpect(jsonPath("$.data.content").value("HTTP 是基于 TCP 的应用层协议。"))
                .andExpect(jsonPath("$.data.citations.length()").value(0))
                .andExpect(jsonPath("$.data.insufficientEvidence").value(false))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void blankContentReturns400BeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/nodes/13/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_ARGUMENT));
    }

    @Test
    void missingBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/nodes/13/chat")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void insufficientEvidenceFlagIsPassedThrough() throws Exception {
        when(chatService.ask(eq(13L), any())).thenReturn(new ChatService.ChatMessageView(
                "assistant", ChatService.INSUFFICIENT_EVIDENCE_SENTENCE, List.of(), true));
        mockMvc.perform(post("/api/nodes/13/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"PHP是什么\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insufficientEvidence").value(true))
                .andExpect(jsonPath("$.data.content").value(ChatService.INSUFFICIENT_EVIDENCE_SENTENCE));
    }

    @Test
    void llmNotConfiguredSurfacesAs503BusinessCode() throws Exception {
        when(chatService.ask(eq(13L), any())).thenThrow(
                new ApiException(503, ErrorCodes.LLM_NOT_CONFIGURED, "尚未配置默认模型"));
        mockMvc.perform(post("/api/nodes/13/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"HTTP是什么\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCodes.LLM_NOT_CONFIGURED));
    }

    @Test
    void llmUnreachableSurfacesAs502BusinessCode() throws Exception {
        when(chatService.ask(eq(13L), any())).thenThrow(
                new ApiException(502, ErrorCodes.LLM_UNREACHABLE, "无法连接模型服务"));
        mockMvc.perform(post("/api/nodes/13/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"HTTP是什么\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ErrorCodes.LLM_UNREACHABLE));
    }

    @Test
    void legacySessionRoutesAreGone() throws Exception {
        // 结构约束：控制器上不得再存在任何会话式映射（列表/创建/消息读写/删除）
        List<String> mappings = new ArrayList<>();
        for (java.lang.reflect.Method method : ChatController.class.getDeclaredMethods()) {
            org.springframework.web.bind.annotation.RequestMapping mapping =
                    org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                            method, org.springframework.web.bind.annotation.RequestMapping.class);
            if (mapping != null) {
                mappings.addAll(List.of(mapping.value()));
            }
        }
        assertEquals(List.of("/api/nodes/{nodeId}/chat"), mappings,
                "ChatController 应当只有一条无状态问答路由");
        assertTrue(mappings.stream().noneMatch(m -> m.contains("sessions")),
                "旧会话式路由不得保留");

        // 行为约束：旧入口不能再返回成功包络（真实应用中由 NoResourceFoundException 处理器给出 404「资源不存在」）
        assertNotSuccess(mockMvc.perform(get("/api/nodes/13/chat/sessions")).andReturn().getResponse());
        assertNotSuccess(mockMvc.perform(post("/api/nodes/13/chat/sessions")).andReturn().getResponse());
        assertNotSuccess(mockMvc.perform(get("/api/chat/sessions/9/messages")).andReturn().getResponse());
        assertNotSuccess(mockMvc.perform(post("/api/chat/sessions/9/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"HTTP是什么\"}")).andReturn().getResponse());
        assertNotSuccess(mockMvc.perform(delete("/api/chat/sessions/9")).andReturn().getResponse());
    }

    private static void assertNotSuccess(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
        assertNotEquals(200, response.getStatus(), "旧会话路由不得再返回 200");
        assertFalse(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("\"code\":0"),
                "旧会话路由不得再返回成功包络");
    }
}
