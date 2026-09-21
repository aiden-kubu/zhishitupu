package com.knowledgegraph.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;

import java.util.*;

/** Groups candidates sharing source passages without shortening or dropping their evidence. */
final class ReviewBatchPlanner {
    static final int MAX_ITEMS = 25;
    static final int MAX_INPUT_CHARS = 36_000;
    private record Prepared(AiReviewService.Item item, int candidateChars, List<Long> sources) {}

    private ReviewBatchPlanner() {}

    static List<List<AiReviewService.Item>> plan(ObjectMapper mapper, List<AiReviewService.Item> items,
                                                Map<Long, String> evidence) {
        var sourceSizes = new HashMap<Long, Integer>();
        var prepared = new ArrayList<Prepared>();
        for (var item : items) {
            validate(item, evidence);
            var ids = new ArrayList<>(new LinkedHashSet<>(item.evidenceIds()));
            for (long id : ids) sourceSizes.computeIfAbsent(id,
                    key -> json(mapper, Map.of(String.valueOf(key), evidence.get(key))).length() - 2);
            prepared.add(new Prepared(item, json(mapper, item.candidate()).length(), ids));
        }
        var grouped = pack(mapper, prepared, sourceSizes, true);
        var sequential = pack(mapper, prepared, sourceSizes, false);
        // Grouping is a heuristic; retain sequential packing if it sends less total text.
        return cost(mapper, grouped, evidence) < cost(mapper, sequential, evidence) ? grouped : sequential;
    }

    private static List<List<AiReviewService.Item>> pack(ObjectMapper mapper, List<Prepared> input,
                                                         Map<Long, Integer> sourceSizes, boolean group) {
        var remaining = new ArrayList<>(input);
        var result = new ArrayList<List<AiReviewService.Item>>();
        int envelope = json(mapper, Map.of("candidates", List.of(), "evidence", Map.of())).length();
        while (!remaining.isEmpty()) {
            var batch = new ArrayList<AiReviewService.Item>();
            var sources = new HashSet<Long>();
            long chars = envelope;
            while (!remaining.isEmpty() && batch.size() < MAX_ITEMS) {
                int selected = -1;
                long bestOverlap = -1;
                long bestExtra = Long.MAX_VALUE;
                for (int index = 0; index < remaining.size(); index++) {
                    var candidate = remaining.get(index);
                    long extra = candidate.candidateChars() + (batch.isEmpty() ? 0 : 1);
                    long overlap = 0;
                    int sourceCount = sources.size();
                    for (long id : candidate.sources()) {
                        if (sources.contains(id)) overlap += sourceSizes.get(id);
                        else extra += sourceSizes.get(id) + (sourceCount++ == 0 ? 0 : 1);
                    }
                    // An oversized single candidate keeps every source and occupies its own batch.
                    if (!batch.isEmpty() && chars + extra > MAX_INPUT_CHARS) {
                        if (!group) break;
                        continue;
                    }
                    if (selected == -1 || overlap > bestOverlap || (overlap == bestOverlap && extra < bestExtra)) {
                        selected = index;
                        bestOverlap = overlap;
                        bestExtra = extra;
                    }
                    if (!group || batch.isEmpty()) break;
                }
                if (selected < 0) break;
                var selectedItem = remaining.remove(selected);
                batch.add(selectedItem.item());
                sources.addAll(selectedItem.sources());
                chars += bestExtra;
            }
            result.add(List.copyOf(batch));
        }
        return List.copyOf(result);
    }

    static String input(ObjectMapper mapper, List<AiReviewService.Item> batch, Map<Long, String> evidence) {
        var sources = new TreeMap<String, String>();
        for (var item : batch) {
            validate(item, evidence);
            for (long id : item.evidenceIds()) sources.put(String.valueOf(id), evidence.get(id));
        }
        // Stable ordering also avoids needless differences in otherwise identical requests.
        var body = new LinkedHashMap<String, Object>();
        body.put("evidence", sources);
        body.put("candidates", batch.stream().map(AiReviewService.Item::candidate).toList());
        return json(mapper, body);
    }

    static long cost(ObjectMapper mapper, List<List<AiReviewService.Item>> batches, Map<Long, String> evidence) {
        return batches.stream().mapToLong(batch -> input(mapper, batch, evidence).length()
                + AiReviewService.PROMPT.length()).sum();
    }

    private static void validate(AiReviewService.Item item, Map<Long, String> evidence) {
        if (item.evidenceIds().isEmpty() || item.evidenceIds().stream().anyMatch(id -> evidence.get(id) == null))
            throw new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE, "候选缺少当前文档的真实证据，停止复审");
    }

    private static String json(ObjectMapper mapper, Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("无法构建 AI 复审请求", ex); }
    }
}
