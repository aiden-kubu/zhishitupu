package com.knowledgegraph.chat;

import com.knowledgegraph.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 节点 AI 知识助手接口（无历史临时模式，2026-09-14 用户决策）：
 * 只有一条无状态问答路由；旧会话式路由（chat/sessions 系列）已移除，
 * 产品不再存在会话持久化入口。
 */
@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 无状态提问：图谱上下文 + 本次打开期间的临时历史 + 当前问题，不读写任何会话数据。 */
    @PostMapping("/api/nodes/{nodeId}/chat")
    public ApiResponse<ChatService.ChatMessageView> ask(
            @PathVariable long nodeId, @Valid @RequestBody ChatService.AskRequest request) {
        return ApiResponse.ok(chatService.ask(nodeId, request));
    }
}
