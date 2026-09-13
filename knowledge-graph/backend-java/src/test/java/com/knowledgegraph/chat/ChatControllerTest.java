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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatController 路由与响应包络测试（MockMvc standalone + Mockito，不依赖数据库）。
 * 回归点：chat 路由必须命中 Controller，不再由静态资源兜底抛 NoResourceFoundException →「资源不存在」。
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 10, 0, 0);

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
    void listSessionsRouteExistsAndReturnsEnvelope() throws Exception {
        when(chatService.listSessions(13)).thenReturn(List.of(
                new ChatService.ChatSessionView(1, 13, "TCP", "knowledge_only", NOW, NOW)));
        mockMvc.perform(get("/api/nodes/13/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].nodeId").value(13))
                .andExpect(jsonPath("$.data[0].title").value("TCP"));
    }

    @Test
    void createSessionRouteExists() throws Exception {
        when(chatService.createSession(13)).thenReturn(
                new ChatService.ChatSessionView(9, 13, "TCP", "knowledge_only", NOW, NOW));
        mockMvc.perform(post("/api/nodes/13/chat/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.nodeId").value(13));
    }

    @Test
    void listMessagesRouteExists() throws Exception {
        when(chatService.listMessages(9)).thenReturn(List.of(
                new ChatService.ChatMessageView(1, "user", "HTTP是什么", List.of(), false, NOW),
                new ChatService.ChatMessageView(2, "assistant", "HTTP 是应用层协议。", List.of(), false, NOW)));
        mockMvc.perform(get("/api/chat/sessions/9/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].content").value("HTTP是什么"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"))
                .andExpect(jsonPath("$.data[1].citations.length()").value(0));
    }

    @Test
    void sendMessageRouteExistsAndReturnsAssistantMessage() throws Exception {
        when(chatService.sendMessage(eq(9L), any())).thenReturn(
                new ChatService.ChatMessageView(2, "assistant", "HTTP 基于 TCP。", List.of(), false, NOW));
        mockMvc.perform(post("/api/chat/sessions/9/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"HTTP是什么\",\"mode\":\"knowledge_only\",\"depth\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.role").value("assistant"))
                .andExpect(jsonPath("$.data.insufficientEvidence").value(false));
    }

    @Test
    void insufficientEvidenceFlagIsExposed() throws Exception {
        when(chatService.sendMessage(eq(9L), any())).thenReturn(
                new ChatService.ChatMessageView(3, "assistant",
                        ChatService.INSUFFICIENT_EVIDENCE_SENTENCE, List.of(), true, NOW));
        mockMvc.perform(post("/api/chat/sessions/9/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"PHP是什么\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insufficientEvidence").value(true))
                .andExpect(jsonPath("$.data.content").value(ChatService.INSUFFICIENT_EVIDENCE_SENTENCE));
    }

    @Test
    void deleteSessionRouteExists() throws Exception {
        when(chatService.deleteSession(9)).thenReturn(Map.of("deleted", true));
        mockMvc.perform(delete("/api/chat/sessions/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void unknownSessionReturnsNotFoundBusinessCodeInsteadOfResourceMissing() throws Exception {
        when(chatService.listMessages(999)).thenThrow(ApiException.notFound("会话不存在: 999"));
        mockMvc.perform(get("/api/chat/sessions/999/messages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.NOT_FOUND))
                .andExpect(jsonPath("$.message").value("会话不存在: 999"));
    }

    @Test
    void sendMessageOnUnknownSessionReturnsNotFoundBusinessCode() throws Exception {
        when(chatService.sendMessage(eq(999L), any()))
                .thenThrow(new ApiException(404, ErrorCodes.NOT_FOUND, "会话不存在: 999"));
        mockMvc.perform(post("/api/chat/sessions/999/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"HTTP是什么\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.NOT_FOUND));
    }

    @Test
    void blankContentReturns400() throws Exception {
        mockMvc.perform(post("/api/chat/sessions/9/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_ARGUMENT));
    }

    @Test
    void missingBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/chat/sessions/9/messages")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_ARGUMENT));
    }

    @Test
    void llmNotConfiguredSurfacesAs503BusinessCode() throws Exception {
        when(chatService.sendMessage(eq(9L), any())).thenThrow(new ApiException(503,
                ErrorCodes.LLM_NOT_CONFIGURED, "尚未配置默认模型"));
        mockMvc.perform(post("/api/chat/sessions/9/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"HTTP是什么\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCodes.LLM_NOT_CONFIGURED));
    }
}
