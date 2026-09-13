package com.knowledgegraph.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.chat.LlmClient;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.graph.NodeService;
import com.knowledgegraph.graph.dto.NodeDetail;
import com.knowledgegraph.search.dto.AiExpandResult;
import com.knowledgegraph.settings.LlmProfileService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AiExpandService 编排逻辑（验收 1/3/8/9）：
 * 本地命中不调模型；同词并发只执行一次模型生成（single-flight）；
 * 模型返回非法内容时不触库。
 */
class AiExpandServiceTest {

    private static final String VALID_LLM_ANSWER = """
            {"node": {"canonicalName": "CDN", "nameEn": "Content Delivery Network",
              "aliases": ["内容分发网络"], "type": "concept",
              "definition": "内容分发网络（CDN）是分布在不同地理位置的服务器网络，用于就近向用户提供内容。"},
             "relations": [
               {"targetName": "缓存", "targetType": "concept", "targetDefinition": "暂存数据以加速访问。", "relationType": "依赖"},
               {"targetName": "HTTP", "targetType": "concept", "targetDefinition": "超文本传输协议。", "relationType": "相关"},
               {"targetName": "边缘计算", "targetType": "concept", "targetDefinition": "在靠近数据源侧进行计算。", "relationType": "相关"}
             ]}
            """;

    private final AiExpandStore store = mock(AiExpandStore.class);
    private final NodeService nodeService = mock(NodeService.class);
    private final LlmProfileService profiles = mock(LlmProfileService.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final AiExpandService service =
            new AiExpandService(store, nodeService, profiles, llmClient, new ObjectMapper());

    private static NodeDetail nodeDetail(long id, String name) {
        return new NodeDetail(id, name, "Content Delivery Network", "concept", "定义",
                null, "active", LocalDateTime.now(), LocalDateTime.now(),
                List.of("内容分发网络"), 2, 0, List.of());
    }

    private static LlmProfileService.DefaultModel defaultModel() {
        return new LlmProfileService.DefaultModel(1L, "http://mock-llm", "mock-model", "key", 5, 512, 0.3);
    }

    @Test
    void existingNodeReturnsImmediatelyWithoutModelCall() {
        when(store.findExistingNodeId("TCP")).thenReturn(7L);
        when(nodeService.getById(7L)).thenReturn(nodeDetail(7L, "TCP"));

        AiExpandResult result = service.expand("TCP", null);

        assertFalse(result.created());
        assertEquals(0, result.relationsCreated());
        assertEquals(7L, result.node().id());
        assertEquals("内容分发网络", result.node().aliases().get(0));
        verifyNoInteractions(llmClient, profiles);
    }

    @Test
    void notConfiguredPropagatesWithoutModelCall() {
        when(store.findExistingNodeId("CDN")).thenReturn(null);
        when(profiles.requireDefaultEnabledProfile()).thenThrow(
                new ApiException(503, ErrorCodes.LLM_NOT_CONFIGURED, "尚未配置默认模型"));

        ApiException ex = assertThrows(ApiException.class, () -> service.expand("CDN", null));

        assertEquals("LLM_NOT_CONFIGURED", ex.getCode());
        verifyNoInteractions(llmClient);
        verify(store, never()).persist(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidModelJsonNeverTouchesStore() {
        when(store.findExistingNodeId("CDN")).thenReturn(null);
        when(profiles.requireDefaultEnabledProfile()).thenReturn(defaultModel());
        when(llmClient.complete(any(), anyList())).thenReturn("抱歉，我无法以 JSON 形式回答");

        ApiException ex = assertThrows(ApiException.class, () -> service.expand("CDN", null));

        assertEquals("LLM_BAD_RESPONSE", ex.getCode());
        verify(store, never()).persist(any(), any(), any(), any(), any(), any());
    }

    @Test
    void concurrentSameKeywordCallsModelOnce() throws Exception {
        // findExistingNodeId：persist 完成前视为不存在，之后返回已建节点 ID
        AtomicLong createdNodeId = new AtomicLong(0);
        when(store.findExistingNodeId(anyString())).thenAnswer(invocation -> {
            long id = createdNodeId.get();
            return id > 0 ? id : null;
        });
        when(profiles.requireDefaultEnabledProfile()).thenReturn(defaultModel());
        AtomicInteger modelCalls = new AtomicInteger();
        when(llmClient.complete(any(), anyList())).thenAnswer(invocation -> {
            modelCalls.incrementAndGet();
            Thread.sleep(300); // 拉长模型耗时窗口，让并发请求全部进入 single-flight
            return VALID_LLM_ANSWER;
        });
        AtomicInteger persistCalls = new AtomicInteger();
        when(store.persist(anyString(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            persistCalls.incrementAndGet();
            createdNodeId.set(99L);
            return new AiExpandStore.PersistOutcome(99L, true, 3);
        });
        when(nodeService.getById(99L)).thenReturn(nodeDetail(99L, "CDN"));

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<AiExpandResult>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return service.expand("CDN", null);
            }));
        }
        for (Future<AiExpandResult> future : futures) {
            AiExpandResult result = future.get(10, TimeUnit.SECONDS);
            assertEquals(99L, result.node().id()); // 无论等待者还是执行者，都拿到同一个节点
        }
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(1, modelCalls.get());      // 只调用一次模型（验收 3）
        assertEquals(1, persistCalls.get());    // 只入库一次，不产生重复节点（验收 8）
        verify(store, times(1)).persist(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void blankQueryRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.expand("   ", null));
        assertEquals("INVALID_ARGUMENT", ex.getCode());
    }
}
