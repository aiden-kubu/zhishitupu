package com.knowledgegraph.chat;

import com.knowledgegraph.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 节点 AI 问答接口（§12.6）。
 */
@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/api/nodes/{nodeId}/chat/sessions")
    public ApiResponse<List<ChatService.ChatSessionView>> listSessions(@PathVariable long nodeId) {
        return ApiResponse.ok(chatService.listSessions(nodeId));
    }

    @PostMapping("/api/nodes/{nodeId}/chat/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatService.ChatSessionView> createSession(@PathVariable long nodeId) {
        return ApiResponse.ok(chatService.createSession(nodeId));
    }

    @GetMapping("/api/chat/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatService.ChatMessageView>> listMessages(@PathVariable long sessionId) {
        return ApiResponse.ok(chatService.listMessages(sessionId));
    }

    @PostMapping("/api/chat/sessions/{sessionId}/messages")
    public ApiResponse<ChatService.ChatMessageView> sendMessage(
            @PathVariable long sessionId, @Valid @RequestBody ChatService.SendMessageRequest request) {
        return ApiResponse.ok(chatService.sendMessage(sessionId, request));
    }

    @DeleteMapping("/api/chat/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> deleteSession(@PathVariable long sessionId) {
        return ApiResponse.ok(chatService.deleteSession(sessionId));
    }
}
