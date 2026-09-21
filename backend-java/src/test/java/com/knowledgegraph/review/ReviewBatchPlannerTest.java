package com.knowledgegraph.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ReviewBatchPlannerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private AiReviewService.Item item(int id, Long... sources) {
        var ids = List.of(sources);
        var candidate = Map.<String, Object>of("id", "entity:" + id, "kind", "entity",
                "name", "知识" + id, "definition", "原文定义", "evidenceChunkIds", ids);
        return new AiReviewService.Item("entity:" + id, "entity", id, "key" + id, null, null, candidate, ids);
    }

    @Test void sharedEvidenceIsChargedOncePerBatchAndAllCandidatesRemain() throws Exception {
        var items = IntStream.range(0, 100).mapToObj(i -> item(i, 1L)).toList();
        var evidence = Map.of(1L, "完整原文".repeat(2000));
        var batches = ReviewBatchPlanner.plan(mapper, items, evidence);
        assertEquals(4, batches.size());
        assertPreserved(items, evidence, batches);
    }

    @Test void groupsDistantCandidatesSharingSourcesWithoutMixingTheirEvidence() throws Exception {
        var evidence = new LinkedHashMap<Long, String>();
        for (long i = 1; i <= 8; i++) evidence.put(i, "不同课程证据".repeat(1000) + i);
        var items = IntStream.range(0, 96).mapToObj(i -> item(i, i % 8 + 1L)).toList();
        var batches = ReviewBatchPlanner.plan(mapper, items, evidence);
        long forwardedSources = batches.stream().mapToLong(batch -> batch.stream()
                .flatMap(i -> i.evidenceIds().stream()).distinct().count()).sum();
        assertTrue(forwardedSources < 20, "相隔的共同证据应复用，不能重复发送 96 次");
        assertPreserved(items, evidence, batches);
        assertEquals(batches, ReviewBatchPlanner.plan(mapper, items, evidence));
    }

    @Test void respectsActualJsonSizeIncludingEscapesAndSharedSourceCommas() throws Exception {
        var evidence = Map.of(1L, "\"\\\n".repeat(4000), 2L, "\"\\\n".repeat(4000));
        var items = List.of(item(1, 1L), item(2, 2L), item(3, 1L), item(4, 2L));
        var batches = ReviewBatchPlanner.plan(mapper, items, evidence);
        for (var batch : batches)
            assertTrue(ReviewBatchPlanner.input(mapper, batch, evidence).length() <= ReviewBatchPlanner.MAX_INPUT_CHARS);
        assertPreserved(items, evidence, batches);
    }

    @Test void oversizedSingleCandidateKeepsEveryFullPassageAndRunsAlone() throws Exception {
        var evidence = Map.of(1L, "甲".repeat(20_000), 2L, "乙".repeat(20_000));
        var items = List.of(item(1, 1L, 2L), item(2, 1L));
        var batches = ReviewBatchPlanner.plan(mapper, items, evidence);
        assertEquals(2, batches.size());
        assertEquals(List.of(items.get(0)), batches.get(0));
        assertPreserved(items, evidence, batches);
    }

    @Test void rejectsMissingEvidenceBeforeAnyModelCall() {
        assertThrows(ApiException.class, () -> ReviewBatchPlanner.plan(mapper, List.of(item(1, 9L)), Map.of()));
        assertThrows(ApiException.class, () -> ReviewBatchPlanner.plan(mapper, List.of(item(1)), Map.of()));
        assertTrue(ReviewBatchPlanner.plan(mapper, List.of(), Map.of()).isEmpty());
    }

    @Test void repeatedIdsDoNotDoubleChargeButOriginalReferencesArePreserved() throws Exception {
        var items = List.of(item(1, 1L, 1L), item(2, 1L, 1L));
        var evidence = Map.of(1L, "内容".repeat(8500));
        var batches = ReviewBatchPlanner.plan(mapper, items, evidence);
        assertEquals(1, batches.size());
        assertPreserved(items, evidence, batches);
    }

    private void assertPreserved(List<AiReviewService.Item> items, Map<Long, String> evidence,
                                 List<List<AiReviewService.Item>> batches) throws Exception {
        var seen = new HashSet<String>();
        for (var batch : batches) {
            assertTrue(batch.size() <= ReviewBatchPlanner.MAX_ITEMS);
            var input = mapper.readTree(ReviewBatchPlanner.input(mapper, batch, evidence));
            for (int i = 0; i < batch.size(); i++) {
                var item = batch.get(i);
                assertTrue(seen.add(item.id()));
                assertEquals(mapper.readTree(mapper.writeValueAsString(item.candidate())), input.path("candidates").get(i));
                for (long source : item.evidenceIds())
                    assertEquals(evidence.get(source), input.path("evidence").path(String.valueOf(source)).asText());
            }
        }
        assertEquals(new HashSet<>(items.stream().map(AiReviewService.Item::id).toList()), seen);
    }
}
