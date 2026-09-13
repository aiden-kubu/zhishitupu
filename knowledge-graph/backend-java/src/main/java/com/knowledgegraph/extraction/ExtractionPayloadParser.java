package com.knowledgegraph.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文档抽取模型输出的解析与校验（§8.7/§8.8）。
 * 模型输出一律按不可信输入处理：只接受合法 JSON 对象（容忍 Markdown 围栏与前后缀文字）；
 * 节点类型白名单校验、confidence 截断到 0~1、字段限长；
 * evidenceChunkIds 必须属于当前批次/当前文档的真实 chunkId，伪造一律拒绝整批（不得留下半成品候选）。
 */
public final class ExtractionPayloadParser {

    public static final int MAX_ENTITIES_PER_BATCH = 30;
    public static final int MAX_RELATIONS_PER_BATCH = 20;
    public static final int MAX_NAME_CHARS = 200;
    public static final int MAX_RELATION_TYPE_CHARS = 100;
    public static final int MAX_DEFINITION_CHARS = 1000;
    public static final int MAX_ALIASES = 10;
    public static final int MAX_TEMP_KEY_CHARS = 50;
    public static final int MAX_EVIDENCE_PER_ITEM = 20;
    public static final double DEFAULT_CONFIDENCE = 0.5;

    public static final Set<String> NODE_TYPE_WHITELIST =
            Set.of("course", "chapter", "knowledge", "concept", "method", "application", "other");

    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}+");

    public record NormalizedEntity(String tempKey, String name, String nameEn, List<String> aliases,
                                   String nodeType, String definition, double confidence,
                                   List<Long> evidenceChunkIds) {
    }

    public record NormalizedRelation(String sourceTempKey, String targetTempKey, String relationType,
                                     double confidence, List<Long> evidenceChunkIds) {
    }

    public record NormalizedExtraction(List<NormalizedEntity> entities, List<NormalizedRelation> relations) {
    }

    private ExtractionPayloadParser() {
    }

    /**
     * 解析并校验一个批次的模型输出。
     *
     * @param validChunkIds 当前批次携带的真实 chunkId（同时必属当前文档）
     */
    public static NormalizedExtraction parse(ObjectMapper objectMapper, String raw, Set<Long> validChunkIds) {
        String json = extractJsonObject(raw);
        JsonNode root;
        try {
            root = objectMapper.readTree(json == null ? "" : json);
        } catch (Exception ex) {
            throw badResponse("模型输出不是合法 JSON");
        }
        if (root == null || !root.isObject()) {
            throw badResponse("模型输出不是 JSON 对象");
        }

        List<NormalizedEntity> entities = parseEntities(root.path("entities"), validChunkIds);
        if (entities.isEmpty()) {
            throw badResponse("模型未返回任何实体候选");
        }
        Set<String> knownTempKeys = new LinkedHashSet<>();
        for (NormalizedEntity entity : entities) {
            knownTempKeys.add(entity.tempKey());
        }
        List<NormalizedRelation> relations = parseRelations(root.path("relations"), knownTempKeys, validChunkIds);
        return new NormalizedExtraction(entities, relations);
    }

    /** 提取 JSON 对象文本：容忍 Markdown 围栏与前后缀解释文字。 */
    static String extractJsonObject(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        if (text.startsWith("```")) {
            int firstLineBreak = text.indexOf('\n');
            text = firstLineBreak >= 0 ? text.substring(firstLineBreak + 1) : "";
            String stripped = text.strip();
            if (stripped.endsWith("```")) {
                stripped = stripped.substring(0, stripped.length() - 3);
            }
            text = stripped.strip();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private static List<NormalizedEntity> parseEntities(JsonNode entitiesNode, Set<Long> validChunkIds) {
        List<NormalizedEntity> entities = new ArrayList<>();
        if (!entitiesNode.isArray()) {
            return entities;
        }
        Set<String> tempKeys = new LinkedHashSet<>();
        for (JsonNode item : entitiesNode) {
            if (entities.size() >= MAX_ENTITIES_PER_BATCH) {
                break;
            }
            if (!item.isObject()) {
                continue;
            }
            String tempKey = singleLine(textOrNull(item.path("tempKey")));
            String name = singleLine(textOrNull(item.path("name")));
            if (tempKey == null || tempKey.length() > MAX_TEMP_KEY_CHARS || name == null) {
                throw badResponse("实体候选缺少 tempKey 或 name");
            }
            if (!tempKeys.add(tempKey)) {
                throw badResponse("实体 tempKey 重复: " + tempKey);
            }
            String nameEn = singleLine(textOrNull(item.path("nameEn")));
            if (nameEn != null && nameEn.length() > MAX_NAME_CHARS) {
                nameEn = nameEn.substring(0, MAX_NAME_CHARS);
            }
            if (name.length() > MAX_NAME_CHARS) {
                name = name.substring(0, MAX_NAME_CHARS);
            }
            String definition = multiline(textOrNull(item.path("definition")));
            if (definition == null) {
                throw badResponse("实体「" + name + "」缺少 definition");
            }
            List<Long> evidence = parseEvidence(item.path("evidenceChunkIds"), validChunkIds);
            entities.add(new NormalizedEntity(tempKey, name, nameEn, parseAliases(item.path("aliases")),
                    whitelistType(textOrNull(item.path("nodeType"))), definition,
                    clampConfidence(item.path("confidence")), evidence));
        }
        return entities;
    }

    private static List<NormalizedRelation> parseRelations(JsonNode relationsNode, Set<String> knownTempKeys,
                                                           Set<Long> validChunkIds) {
        List<NormalizedRelation> relations = new ArrayList<>();
        if (!relationsNode.isArray()) {
            return relations;
        }
        for (JsonNode item : relationsNode) {
            if (relations.size() >= MAX_RELATIONS_PER_BATCH) {
                break;
            }
            if (!item.isObject()) {
                continue;
            }
            String source = singleLine(textOrNull(item.path("sourceTempKey")));
            String target = singleLine(textOrNull(item.path("targetTempKey")));
            String relationType = singleLine(textOrNull(item.path("relationType")));
            if (source == null || target == null || relationType == null) {
                throw badResponse("关系候选缺少 sourceTempKey/targetTempKey/relationType");
            }
            // 不存在的 tempKey 关系不得入库（§三校验要求）
            if (!knownTempKeys.contains(source) || !knownTempKeys.contains(target)) {
                throw badResponse("关系引用了未定义的 tempKey: " + source + " -> " + target);
            }
            if (relationType.length() > MAX_RELATION_TYPE_CHARS) {
                relationType = relationType.substring(0, MAX_RELATION_TYPE_CHARS);
            }
            List<Long> evidence = parseEvidence(item.path("evidenceChunkIds"), validChunkIds);
            relations.add(new NormalizedRelation(source, target, relationType,
                    clampConfidence(item.path("confidence")), evidence));
        }
        return relations;
    }

    /** 证据 chunkId：必须为当前批次/当前文档的真实 chunkId，伪造即整批拒绝。 */
    private static List<Long> parseEvidence(JsonNode evidenceNode, Set<Long> validChunkIds) {
        if (!evidenceNode.isArray() || evidenceNode.isEmpty()) {
            throw badResponse("候选缺少有效的 evidenceChunkIds（禁止伪造证据）");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (JsonNode item : evidenceNode) {
            if (!item.canConvertToLong()) {
                throw badResponse("evidenceChunkIds 含非数字项");
            }
            long id = item.asLong();
            if (!validChunkIds.contains(id)) {
                throw badResponse("evidenceChunkIds 引用了不属于当前文档/批次的 chunkId: " + id);
            }
            ids.add(id);
            if (ids.size() >= MAX_EVIDENCE_PER_ITEM) {
                break;
            }
        }
        return new ArrayList<>(ids);
    }

    /** 别名：按大小写不敏感去重并限量。 */
    private static List<String> parseAliases(JsonNode aliasesNode) {
        List<String> candidates = new ArrayList<>();
        if (aliasesNode.isArray()) {
            for (JsonNode item : aliasesNode) {
                String text = singleLine(textOrNull(item));
                if (text != null) {
                    candidates.add(text.length() > MAX_NAME_CHARS ? text.substring(0, MAX_NAME_CHARS) : text);
                }
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> aliases = new ArrayList<>();
        for (String candidate : candidates) {
            if (seen.add(candidate.toLowerCase(Locale.ROOT))) {
                aliases.add(candidate);
                if (aliases.size() >= MAX_ALIASES) {
                    break;
                }
            }
        }
        return aliases;
    }

    /** 节点类型白名单：合法值原样采用，白名单外归入 other（§14.8 同规则）。 */
    static String whitelistType(String rawType) {
        if (rawType == null) {
            return "other";
        }
        String type = rawType.trim().toLowerCase(Locale.ROOT);
        return NODE_TYPE_WHITELIST.contains(type) ? type : "other";
    }

    private static double clampConfidence(JsonNode confidenceNode) {
        if (confidenceNode == null || confidenceNode.isNull() || !confidenceNode.isNumber()) {
            return DEFAULT_CONFIDENCE;
        }
        double value = confidenceNode.asDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return DEFAULT_CONFIDENCE;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asText();
    }

    private static String singleLine(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = CONTROL_CHARS.matcher(text).replaceAll(" ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String multiline(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("\r\n", "\n").replace("\r", "\n").strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() <= MAX_DEFINITION_CHARS ? cleaned : cleaned.substring(0, MAX_DEFINITION_CHARS);
    }

    private static ApiException badResponse(String reason) {
        return new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE, "抽取结果校验失败：" + reason);
    }
}
