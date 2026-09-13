package com.knowledgegraph.search;

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
 * 模型返回内容的解析与校验（任务约定 §14）。
 * 模型输出一律按不可信输入处理：只接受合法 JSON 对象（容忍 Markdown 围栏与前后缀文字），
 * 节点类型做白名单校验，超长文本截断，单行字段去除控制字符；解析失败抛 LLM_BAD_RESPONSE，绝不写库。
 */
public final class AiExpandPayloadParser {

    public static final int MAX_RELATIONS = 8;
    public static final int MAX_NAME_CHARS = 200;
    public static final int MAX_RELATION_TYPE_CHARS = 100;
    public static final int MAX_DEFINITION_CHARS = 1000;
    public static final int MAX_ALIASES = 10;

    public static final Set<String> NODE_TYPE_WHITELIST =
            Set.of("course", "chapter", "knowledge", "concept", "method", "application", "other");

    /** 单行字段（名称/关系名/别名）去除的控制字符，含换行与制表符 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}+");

    /** 用户问题中常见的前后缀（如「什么是CDN」），用于主题一致性判断，不参与写库 */
    private static final List<String> QUESTION_PREFIXES = List.of("什么是", "介绍一下", "请介绍", "介绍下", "介绍", "解释");
    private static final List<String> QUESTION_SUFFIXES =
            List.of("是什么意思", "是什么", "什么意思", "的定义", "的介绍", "介绍");

    public record NormalizedNode(String name, String nameEn, List<String> aliases, String type, String definition) {
    }

    public record NormalizedRelation(String targetName, String targetNameEn, String targetType,
                                     String targetDefinition, String relationType) {
    }

    public record NormalizedPayload(NormalizedNode node, List<NormalizedRelation> relations) {
    }

    private AiExpandPayloadParser() {
    }

    /** 解析并校验模型输出；query 为用户原始搜索词，用于「主题必须对应查询」校验。 */
    public static NormalizedPayload parse(ObjectMapper objectMapper, String raw, String query) {
        String json = extractJsonObject(raw);
        if (json == null) {
            throw badResponse();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw badResponse();
        }
        JsonNode nodeNode = root.path("node");
        if (!nodeNode.isObject()) {
            throw badResponse();
        }

        String name = truncate(singleLine(textOrNull(nodeNode.path("canonicalName"))), MAX_NAME_CHARS);
        if (name == null) {
            throw badResponse();
        }
        String nameEn = truncate(singleLine(textOrNull(nodeNode.path("nameEn"))), MAX_NAME_CHARS);
        List<String> aliases = parseAliases(nodeNode.path("aliases"));
        String type = whitelistType(textOrNull(nodeNode.path("type")));
        String definition = multiline(textOrNull(nodeNode.path("definition")));
        if (definition == null) {
            throw badResponse();
        }
        if (!topicMatchesQuery(query, name, nameEn, aliases)) {
            throw new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE,
                    "模型返回的主题与搜索关键词不一致，未能形成知识节点");
        }

        NormalizedNode node = new NormalizedNode(name, nameEn, aliases, type, definition);
        return new NormalizedPayload(node, parseRelations(root.path("relations"), name));
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

    /** 关系列表：跳过缺目标名/关系名的条目与自环，第一版最多接受 8 条（§14.3、验收 10）。 */
    private static List<NormalizedRelation> parseRelations(JsonNode relationsNode, String mainName) {
        List<NormalizedRelation> relations = new ArrayList<>();
        if (!relationsNode.isArray()) {
            return relations;
        }
        for (JsonNode item : relationsNode) {
            if (relations.size() >= MAX_RELATIONS) {
                break;
            }
            if (!item.isObject()) {
                continue;
            }
            String targetName = truncate(singleLine(textOrNull(item.path("targetName"))), MAX_NAME_CHARS);
            String relationType = truncate(singleLine(textOrNull(item.path("relationType"))), MAX_RELATION_TYPE_CHARS);
            if (targetName == null || relationType == null) {
                continue;
            }
            if (targetName.equalsIgnoreCase(mainName)) {
                continue; // 指向主节点自身的自环关系不入库
            }
            String targetNameEn = truncate(singleLine(textOrNull(item.path("targetNameEn"))), MAX_NAME_CHARS);
            String targetType = whitelistType(textOrNull(item.path("targetType")));
            String targetDefinition = multiline(textOrNull(item.path("targetDefinition")));
            relations.add(new NormalizedRelation(targetName, targetNameEn, targetType, targetDefinition, relationType));
        }
        return relations;
    }

    /** 别名：允许模型用「、」等分隔符把多个别名放进一个字符串；按大小写不敏感去重并限量。 */
    private static List<String> parseAliases(JsonNode aliasesNode) {
        List<String> candidates = new ArrayList<>();
        if (aliasesNode.isArray()) {
            for (JsonNode item : aliasesNode) {
                String text = textOrNull(item);
                if (text == null) {
                    continue;
                }
                for (String part : text.split("[、，,;；]")) {
                    String alias = singleLine(part);
                    if (alias != null) {
                        candidates.add(alias);
                    }
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

    /** 节点类型白名单校验：合法值原样（小写）采用，非法值归入 other，绝不让白名单外的值入库（§14.8）。 */
    static String whitelistType(String rawType) {
        if (rawType == null) {
            return "other";
        }
        String type = rawType.trim().toLowerCase(Locale.ROOT);
        return NODE_TYPE_WHITELIST.contains(type) ? type : "other";
    }

    /**
     * 主题一致性（§14.1 主节点名称必须对应用户查询）：
     * 查询词（含去除「是什么/介绍」等问句前后缀后的核心词）必须出现在名称、英文名或别名中，
     * 或名称（≥2 字）出现在查询词中；否则视为模型擅自换题，拒绝入库。
     */
    static boolean topicMatchesQuery(String query, String name, String nameEn, List<String> aliases) {
        String rawQuery = query.trim().toLowerCase(Locale.ROOT);
        String coreQuery = stripQuestionWrapper(rawQuery);
        List<String> nameCandidates = new ArrayList<>();
        nameCandidates.add(name);
        if (nameEn != null) {
            nameCandidates.add(nameEn);
        }
        nameCandidates.addAll(aliases);
        for (String candidateName : nameCandidates) {
            String candidate = candidateName.trim().toLowerCase(Locale.ROOT);
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.contains(rawQuery) || candidate.contains(coreQuery)) {
                return true;
            }
            if (candidate.length() >= 2 && rawQuery.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String stripQuestionWrapper(String query) {
        String result = query;
        for (int i = 0; i < 3; i++) {
            boolean changed = false;
            for (String prefix : QUESTION_PREFIXES) {
                if (result.startsWith(prefix) && result.length() > prefix.length()) {
                    result = result.substring(prefix.length());
                    changed = true;
                }
            }
            for (String suffix : QUESTION_SUFFIXES) {
                if (result.endsWith(suffix) && result.length() > suffix.length()) {
                    result = result.substring(0, result.length() - suffix.length());
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return result.trim();
    }

    /** NullNode/MissingNode 与文本统一处理：非文本或空白返回 null。 */
    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asText();
    }

    /** 名称/关系名等单行字段：去除控制字符与换行；空白返回 null。 */
    private static String singleLine(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = CONTROL_CHARS.matcher(text).replaceAll(" ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** 定义类字段：保留换行、去除行尾空白；空白返回 null。 */
    private static String multiline(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("\r\n", "\n").replace("\r", "\n").strip();
        return cleaned.isEmpty() ? null : truncate(cleaned, MAX_DEFINITION_CHARS);
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private static ApiException badResponse() {
        return new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE,
                "模型返回内容无法形成知识节点（JSON 解析或校验失败）");
    }
}
