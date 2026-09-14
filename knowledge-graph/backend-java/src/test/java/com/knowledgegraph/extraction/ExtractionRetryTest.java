package com.knowledgegraph.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.chat.LlmClient;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.settings.LlmProfileService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtractionRetryTest {
    final LlmClient client = mock(LlmClient.class);
    final ExtractionService service = new ExtractionService(null, null, client, new ObjectMapper(), null, null, null, null, ExtractionService.DEFAULT_MAX_BATCHES);
    final LlmProfileService.DefaultModel model = new LlmProfileService.DefaultModel(1, "http://localhost", "test", "secret", 120, 8192, 0.1);
    final String valid = """
            {"entities":[{"tempKey":"e1","name":"栈","definition":"后进先出","evidenceChunkIds":[1]}],"relations":[]}
            """;

    @Test void retriesMalformedJsonThenReturnsOnlyValidatedResult() {
        when(client.complete(any(), anyList())).thenReturn("{\"entities\":[", valid);
        assertEquals(1, service.extractBatch(model, "source", Set.of(1L), () -> false, 25, 53).entities().size());
        verify(client, times(2)).complete(any(), anyList());
    }
    @Test void rejectsForgedEvidenceAfterBoundedRetries() {
        when(client.complete(any(), anyList())).thenReturn(valid.replace("[1]", "[999]"));
        var ex = assertThrows(ApiException.class, () -> service.extractBatch(model, "source", Set.of(1L), () -> false, 25, 53));
        assertTrue(ex.getMessage().contains("25/53"));
        verify(client, times(3)).complete(any(), anyList());
    }
    @Test void doesNotRetryAuthenticationFailure() {
        when(client.complete(any(), anyList())).thenThrow(new ApiException(502, ErrorCodes.LLM_AUTH_FAILED, "auth"));
        assertThrows(ApiException.class, () -> service.extractBatch(model, "source", Set.of(1L), () -> false, 1, 1));
        verify(client).complete(any(), anyList());
    }
    @Test void checksCancellationBetweenAttempts() {
        when(client.complete(any(), anyList())).thenReturn("invalid");
        var checks = new AtomicInteger();
        var ex = assertThrows(ApiException.class, () -> service.extractBatch(model, "source", Set.of(1L), () -> checks.incrementAndGet() > 1, 1, 1));
        assertEquals(ErrorCodes.CONFLICT, ex.getCode());
        verify(client).complete(any(), anyList());
    }
    @Test void rejectsLengthLimitedResponseEvenWhenContentLooksValid() {
        var ex = assertThrows(ApiException.class, () -> LlmClient.extractContent(Map.of("choices", List.of(
                Map.of("finish_reason", "length", "message", Map.of("content", valid))))));
        assertTrue(ex.getMessage().contains("长度上限"));
    }
}
