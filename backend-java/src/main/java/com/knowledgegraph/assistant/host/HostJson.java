package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local wire conversion only; never changes the application's ObjectMapper. */
final class HostJson {
    static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static {
        JSON.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(16).maxStringLength(65536).build());
    }
    static ApiException error(int status, String code) { return new ApiException(status, code, code); }
    static JsonNode parse(String text, int bytes) {
        try {
            if (text == null || text.getBytes(StandardCharsets.UTF_8).length > bytes) throw new IllegalArgumentException();
            JsonNode n = JSON.readTree(text);
            if (n == null || !n.isObject()) throw new IllegalArgumentException();
            return n;
        } catch (Exception ignored) { throw error(400, "INVALID_ARGUMENT"); }
    }
    /** Core limits count String characters; trusted server JSON is not an HTTP byte payload. */
    static JsonNode internal(String text, int characters) {
        try {
            if (text == null || text.length() > characters) throw new IllegalArgumentException();
            JsonNode n = JSON.readTree(text);
            if (n == null || !n.isObject()) throw new IllegalArgumentException();
            return n;
        } catch (Exception ignored) { throw error(500, "INTERNAL_ERROR"); }
    }
    static void fields(JsonNode n, Set<String> allowed, Set<String> required) {
        if (n == null || !n.isObject()) throw error(400, "INVALID_ARGUMENT");
        n.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw error(400, "INVALID_ARGUMENT"); });
        for (String k : required) if (!n.has(k)) throw error(400, "INVALID_ARGUMENT");
    }
    static long id(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
            return Long.parseLong(value);
        } catch (RuntimeException ignored) { throw error(400, "INVALID_ARGUMENT"); }
    }
    static Set<Long> ids(JsonNode node, int max) {
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > max) throw error(400, "INVALID_ARGUMENT");
        Set<Long> result = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual() || !result.add(id(value.textValue()))) throw error(400, "INVALID_ARGUMENT");
        }
        return Collections.unmodifiableSet(result);
    }
    static String text(JsonNode n, String key, int min, int max) {
        JsonNode v = n.get(key);
        if (v == null || !v.isTextual()) throw error(400, "INVALID_ARGUMENT");
        String value = v.textValue().trim();
        if (value.length() < min || value.length() > max) throw error(400, "INVALID_ARGUMENT");
        return value;
    }
    static String identifier(String value) {
        try { return com.knowledgegraph.assistant.contract.AssistantContracts.identifier(value); }
        catch (RuntimeException ignored) { throw error(400, "INVALID_ARGUMENT"); }
    }
    static int integer(JsonNode n, String key, int min, int max, int fallback) {
        if (!n.has(key)) return fallback;
        JsonNode v = n.get(key);
        if (!v.isIntegralNumber() || !v.canConvertToInt() || v.intValue() < min || v.intValue() > max)
            throw error(400, "INVALID_ARGUMENT");
        return v.intValue();
    }
    static ObjectNode obj(Object... kv) {
        ObjectNode n = JSON.createObjectNode();
        for (int j=0; j<kv.length; j+=2) n.set((String)kv[j], JSON.valueToTree(kv[j+1]));
        return n;
    }
    static ArrayNode array(Collection<?> values) { return JSON.valueToTree(values); }
    static ObjectNode scope(Scope s) {
        return obj("mode", s.mode().wireValue(), "libraryIds", longIds(s.libraryIds()),
                "documentIds", longIds(s.documentIds()), "nodeIds", longIds(s.nodeIds()));
    }
    static List<String> longIds(Collection<Long> ids) { return ids.stream().sorted().map(String::valueOf).toList(); }
    static ObjectNode usage(Usage u) { return obj("decisionCalls",u.modelCalls(),"toolCalls",u.toolCalls(),
            "modelCalls",0,"accountingMode","runtime_model_counter_as_decision_calls"); }
    static ObjectNode coverage(boolean complete, boolean truncated, String kind, Collection<Long> uncovered,
                               Collection<String> limitations) {
        return obj("completeCoverage",complete,"truncated",truncated,"coverageKind",kind,
                "uncoveredDocumentIds",longIds(uncovered),"limitations",limitations);
    }
    static ObjectNode select(JsonNode n, String... names) {
        ObjectNode result = JSON.createObjectNode();
        for (String name : names) result.set(name, n.has(name) ? n.get(name).deepCopy() : NullNode.instance);
        return result;
    }
    static String json(JsonNode n) { return n.toString(); }
    static String instant(java.time.Instant value) { return value == null ? null : value.toString(); }
}
