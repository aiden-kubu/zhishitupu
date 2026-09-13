package com.knowledgegraph.chat;

import com.knowledgegraph.graph.dto.NodeDetail;
import com.knowledgegraph.graph.dto.SubgraphData;
import com.knowledgegraph.graph.dto.SubgraphEdge;
import com.knowledgegraph.graph.dto.SubgraphNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatService 静态逻辑测试：知识上下文构建、系统提示词防注入声明、证据不足判定。
 */
class ChatServiceContextTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 10, 0, 0);

    private static NodeDetail tcpNode() {
        return new NodeDetail(13, "TCP", "Transmission Control Protocol", "knowledge",
                "面向连接的、可靠的传输层协议", null, "active", NOW, NOW,
                List.of("传输控制协议"), 2, 0, List.of());
    }

    private static SubgraphData tcpNeighborhood() {
        List<SubgraphNode> nodes = List.of(
                new SubgraphNode(13, "TCP", "knowledge", "面向连接的、可靠的传输层协议", 2, 0),
                new SubgraphNode(14, "UDP", "knowledge", "无连接的传输层协议", 1, 0),
                new SubgraphNode(7, "HTTP", "knowledge", "基于 TCP 的应用层协议", 1, 0));
        List<SubgraphEdge> edges = List.of(
                new SubgraphEdge(501, 13, 14, "对比", 1.0),
                new SubgraphEdge(502, 7, 13, "依赖", 1.0),
                // 与 TCP 不直接相连的边（邻居之间）不应进入上下文
                new SubgraphEdge(503, 14, 7, "无关边", 1.0));
        return new SubgraphData(13, nodes, edges, false, 3, 3);
    }

    @Test
    void knowledgeContextContainsNodeAndDirectRelations() {
        String context = ChatService.buildKnowledgeContext(tcpNode(), tcpNeighborhood());
        assertTrue(context.contains("当前节点：TCP"));
        assertTrue(context.contains("Transmission Control Protocol"));
        assertTrue(context.contains("类型：knowledge"));
        assertTrue(context.contains("别名：传输控制协议"));
        assertTrue(context.contains("定义：面向连接的、可靠的传输层协议"));
        assertTrue(context.contains("关系「对比」→ UDP"));
        assertTrue(context.contains("无连接的传输层协议"));
        assertTrue(context.contains("关系「依赖」→ HTTP"));
        assertFalse(context.contains("无关边"), "不直接相邻的边不应进入上下文");
    }

    @Test
    void systemPromptContainsContextUntrustedDataRuleAndFixedSentence() {
        String prompt = ChatService.buildSystemPrompt(tcpNode(), tcpNeighborhood());
        assertTrue(prompt.contains("<knowledge_context>"));
        assertTrue(prompt.contains("当前节点：TCP"));
        assertTrue(prompt.contains("不可信"), "必须把知识上下文声明为不可信资料（§14.2）");
        assertTrue(prompt.contains(ChatService.INSUFFICIENT_EVIDENCE_SENTENCE));
    }

    @Test
    void nodeWithoutDefinitionAndEdgesStillProducesContext() {
        NodeDetail bare = new NodeDetail(99, "孤立节点", null, "concept", null, null, "active",
                NOW, NOW, List.of(), 0, 0, List.of());
        SubgraphData empty = new SubgraphData(99, List.of(new SubgraphNode(99, "孤立节点", "concept", null, 0, 0)),
                List.of(), false, 1, 0);
        String context = ChatService.buildKnowledgeContext(bare, empty);
        assertTrue(context.contains("当前节点：孤立节点"));
        assertFalse(context.contains("定义："));
        assertFalse(context.contains("直接关联："));
        // 空上下文仍生成合法提示词，由模型按系统规则返回固定句子
        String prompt = ChatService.buildSystemPrompt(bare, empty);
        assertTrue(prompt.contains(ChatService.INSUFFICIENT_EVIDENCE_SENTENCE));
    }

    @Test
    void insufficientEvidenceDetectedFromFixedSentence() {
        assertTrue(ChatService.isInsufficientEvidence(ChatService.INSUFFICIENT_EVIDENCE_SENTENCE));
        assertTrue(ChatService.isInsufficientEvidence("当前知识库中没有足够资料支持该问题"));
        assertTrue(ChatService.isInsufficientEvidence("  " + ChatService.INSUFFICIENT_EVIDENCE_SENTENCE + "  "));
        assertFalse(ChatService.isInsufficientEvidence("TCP 是面向连接的可靠传输层协议。"));
        assertFalse(ChatService.isInsufficientEvidence(null));
        assertFalse(ChatService.isInsufficientEvidence(""));
    }

    @Test
    void fixedSentenceMatchesDocumentContract() {
        assertEquals("当前知识库中没有足够资料支持该问题。", ChatService.INSUFFICIENT_EVIDENCE_SENTENCE);
    }
}
