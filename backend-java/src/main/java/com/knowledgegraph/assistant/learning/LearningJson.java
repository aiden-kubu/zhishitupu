package com.knowledgegraph.assistant.learning;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LearningJson {
    private LearningJson() {}
    static void fields(JsonNode value, String... names) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("INVALID_OBJECT");
        Set<String> allowed = Set.of(names);
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
    static int integer(JsonNode value, String key, int min, int max, int fallback) {
        JsonNode item = value.get(key);
        if (item == null) {
            if (fallback < min) throw new IllegalArgumentException("MISSING_INTEGER");
            return fallback;
        }
        if (!item.isIntegralNumber() || !item.canConvertToInt()
                || item.intValue() < min || item.intValue() > max)
            throw new IllegalArgumentException("INVALID_INTEGER");
        return item.intValue();
    }
    static String text(JsonNode value, String key, int min, int max) {
        JsonNode item = value.get(key);
        if (item == null || !item.isTextual() || item.textValue().length() < min
                || item.textValue().length() > max || item.textValue().isBlank())
            throw new IllegalArgumentException("INVALID_TEXT");
        return item.textValue();
    }
    static List<String> strings(JsonNode value, String key, int maxCount, int maxLength) {
        JsonNode items = value.get(key);
        if (items == null) return List.of();
        if (!items.isArray() || items.size() > maxCount) throw new IllegalArgumentException("INVALID_LIST");
        List<String> result = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > maxLength)
                throw new IllegalArgumentException("INVALID_LIST_ITEM");
            result.add(item.textValue());
        }
        if (new HashSet<>(result).size() != result.size()) throw new IllegalArgumentException("DUPLICATE_LIST_ITEM");
        return result;
    }
}
