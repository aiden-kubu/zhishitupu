package com.knowledgegraph.assistant.assessment;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AssessmentJson {
    private AssessmentJson() {}

    static void fields(JsonNode value, String... allowedNames) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("INVALID_OBJECT");
        Set<String> allowed = Set.of(allowedNames);
        value.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("UNKNOWN_FIELD");
        });
    }

    static long id(JsonNode value, String key) {
        JsonNode item = value.get(key);
        if (item == null || !item.isIntegralNumber() || !item.canConvertToLong() || item.longValue() <= 0)
            throw new IllegalArgumentException("INVALID_ID");
        return item.longValue();
    }

    static Long optionalId(JsonNode value, String key) {
        return value.has(key) ? id(value, key) : null;
    }

    static int integer(JsonNode value, String key, int minimum, int maximum, int fallback) {
        JsonNode item = value.get(key);
        if (item == null) return fallback;
        if (!item.isIntegralNumber() || !item.canConvertToInt()
                || item.intValue() < minimum || item.intValue() > maximum)
            throw new IllegalArgumentException("INVALID_INTEGER");
        return item.intValue();
    }

    static String text(JsonNode value, String key, int minimum, int maximum) {
        JsonNode item = value.get(key);
        if (item == null || !item.isTextual() || item.textValue().isBlank()
                || item.textValue().length() < minimum || item.textValue().length() > maximum)
            throw new IllegalArgumentException("INVALID_TEXT");
        return item.textValue();
    }

    static List<String> strings(JsonNode value, String key, int maximumCount, int maximumLength) {
        JsonNode items = value.get(key);
        if (items == null) return List.of();
        if (!items.isArray() || items.size() > maximumCount) throw new IllegalArgumentException("INVALID_LIST");
        List<String> result = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > maximumLength)
                throw new IllegalArgumentException("INVALID_LIST_ITEM");
            result.add(item.textValue());
        }
        if (new HashSet<>(result).size() != result.size()) throw new IllegalArgumentException("DUPLICATE_LIST_ITEM");
        return result;
    }
}
