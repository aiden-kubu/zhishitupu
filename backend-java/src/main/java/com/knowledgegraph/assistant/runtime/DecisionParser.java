package com.knowledgegraph.assistant.runtime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict, bounded JSON only: no Markdown extraction, free-text commands, duplicate keys or trailing objects. */
public final class DecisionParser {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public DecisionParser() {
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).build());
    }

    public sealed interface Decision permits CallTool, Finish {}
    public record CallTool(String name, JsonNode arguments) implements Decision {}
    public record Finish(Outcome outcome, String summary, String dataJson, List<String> evidenceIds,
                         List<ActionProposal> proposals, List<String> limitations) implements Decision {}

    public Decision parse(String raw) {
        JsonNode value = json(raw, 32000);
        if (!value.isObject()) throw new IllegalArgumentException("Decision must be an object");
        String type = requiredText(value, "type", 40);
        if (type.equals("call_tool")) {
            fields(value, Set.of("type", "name", "arguments"));
            JsonNode arguments = value.get("arguments");
            if (arguments == null || !arguments.isObject()) throw new IllegalArgumentException("Missing tool arguments");
            return new CallTool(com.knowledgegraph.assistant.contract.AssistantContracts.identifier(
                    requiredText(value, "name", 128)), arguments.deepCopy());
        }
        if (!type.equals("complete") && !type.equals("needs_input")) throw new IllegalArgumentException("Unknown decision");
        fields(value, Set.of("type", "outcome", "summary", "data", "evidenceIds", "actionProposals", "limitations"));
        Outcome outcome = type.equals("needs_input") ? Outcome.NEEDS_INPUT
                : Outcome.valueOf(requiredText(value, "outcome", 40).toUpperCase(Locale.ROOT));
        if (type.equals("needs_input") && value.has("outcome") && !value.get("outcome").asText().equals("needs_input")) {
            throw new IllegalArgumentException("Contradictory outcome");
        }
        JsonNode data = value.get("data");
        if (data == null || !data.isObject()) throw new IllegalArgumentException("Missing result data");
        return new Finish(outcome, requiredText(value, "summary", 4000), jsonText(data, 16000),
                stringArray(value, "evidenceIds", 256), proposals(value.get("actionProposals")),
                stringArray(value, "limitations", 40));
    }

    public JsonNode json(String raw, int max) {
        if (raw == null || raw.isBlank() || raw.length() > max) throw new IllegalArgumentException("Invalid JSON size");
        try { return mapper.reader().readTree(raw); }
        catch (Exception ignored) { throw new IllegalArgumentException("Invalid JSON"); }
    }

    private List<ActionProposal> proposals(JsonNode values) {
        if (values == null) return List.of();
        if (!values.isArray() || values.size() > 10) throw new IllegalArgumentException("Invalid proposals");
        List<ActionProposal> result = new ArrayList<>();
        for (JsonNode value : values) {
            fields(value, Set.of("actionType", "targets", "parameters", "impact", "preconditions"));
            JsonNode targets = value.get("targets");
            if (targets == null || !targets.isArray() || targets.size() > 100) throw new IllegalArgumentException("Invalid targets");
            List<ObjectRef> refs = new ArrayList<>();
            for (JsonNode target : targets) {
                fields(target, Set.of("kind", "id", "version"));
                refs.add(new ObjectRef(requiredText(target, "kind", 80), requiredText(target, "id", 128),
                        target.hasNonNull("version") ? requiredText(target, "version", 256) : null));
            }
            JsonNode parameters = value.get("parameters");
            if (parameters == null || !parameters.isObject()) throw new IllegalArgumentException("Invalid parameters");
            result.add(new ActionProposal(requiredText(value, "actionType", 100), refs, jsonText(parameters, 8000),
                    requiredText(value, "impact", 2000), stringArray(value, "preconditions", 30)));
        }
        return List.copyOf(result);
    }

    private static void fields(JsonNode value, Set<String> allowed) {
        if (!value.isObject()) throw new IllegalArgumentException("Expected object");
        value.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("Unknown field");
        });
    }
    private static String requiredText(JsonNode value, String field, int max) {
        JsonNode node = value.get(field);
        if (node == null || !node.isTextual()) throw new IllegalArgumentException("Missing text field");
        String result = node.textValue();
        if (result.isBlank() || result.length() > max) throw new IllegalArgumentException("Invalid text field");
        return result;
    }
    private static List<String> stringArray(JsonNode value, String field, int max) {
        JsonNode array = value.get(field);
        if (array == null) return List.of();
        if (!array.isArray() || array.size() > max) throw new IllegalArgumentException("Invalid array");
        List<String> result = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > 1000) throw new IllegalArgumentException("Invalid array item");
            result.add(item.textValue());
        }
        if (new HashSet<>(result).size() != result.size()) throw new IllegalArgumentException("Duplicate array item");
        return List.copyOf(result);
    }
    private static String jsonText(JsonNode value, int max) {
        String text = value.toString();
        if (text.length() > max) throw new IllegalArgumentException("JSON payload too large");
        return text;
    }
}
