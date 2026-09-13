package com.knowledgegraph.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.search.AiExpandPayloadParser.NormalizedPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型输出解析与校验（任务约定 §14、验收 9/10）：
 * 模型输出按不可信输入处理 —— 非法 JSON 拒绝、类型白名单、超长截断、自环与残缺关系剔除。
 */
class AiExpandPayloadParserTest {

    private static final String VALID_CDN_JSON = """
            {
              "node": {
                "canonicalName": "CDN",
                "nameEn": "Content Delivery Network",
                "aliases": ["内容分发网络", "CDN 加速"],
                "type": "concept",
                "definition": "内容分发网络（CDN）是分布在不同地理位置的服务器网络，用于就近向用户提供内容，降低访问延迟并减轻源站压力。"
              },
              "relations": [
                {"targetName": "缓存", "targetNameEn": "Cache", "targetType": "concept",
                 "targetDefinition": "暂存数据以加速后续访问。", "relationType": "依赖"},
                {"targetName": "HTTP", "targetType": "concept",
                 "targetDefinition": "超文本传输协议。", "relationType": "相关"}
              ]
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NormalizedPayload parse(String raw, String query) {
        return AiExpandPayloadParser.parse(objectMapper, raw, query);
    }

    @Test
    void parsesBareJson() {
        NormalizedPayload payload = parse(VALID_CDN_JSON, "CDN");
        assertEquals("CDN", payload.node().name());
        assertEquals("Content Delivery Network", payload.node().nameEn());
        assertEquals("concept", payload.node().type());
        assertTrue(payload.node().definition().contains("内容分发网络"));
        assertTrue(payload.node().aliases().contains("内容分发网络"));
        assertEquals(2, payload.relations().size());
        assertEquals("缓存", payload.relations().get(0).targetName());
        assertEquals("依赖", payload.relations().get(0).relationType());
    }

    @Test
    void stripsMarkdownFencesAndProse() {
        String raw = "好的，以下是您要的 JSON：\n```json\n" + VALID_CDN_JSON.strip() + "\n```\n希望有帮助。";
        NormalizedPayload payload = parse(raw, "CDN");
        assertEquals("CDN", payload.node().name());
    }

    @Test
    void invalidJsonThrowsLlmBadResponse() {
        ApiException ex = assertThrows(ApiException.class, () -> parse("抱歉，我无法生成该内容", "CDN"));
        assertEquals("LLM_BAD_RESPONSE", ex.getCode());
        ex = assertThrows(ApiException.class, () -> parse("{ \"node\": { broken", "CDN"));
        assertEquals("LLM_BAD_RESPONSE", ex.getCode());
    }

    @Test
    void missingNodeObjectThrows() {
        assertThrows(ApiException.class, () -> parse("{\"relations\": []}", "CDN"));
    }

    @Test
    void blankDefinitionThrows() {
        String raw = VALID_CDN_JSON.replace(
                "内容分发网络（CDN）是分布在不同地理位置的服务器网络，用于就近向用户提供内容，降低访问延迟并减轻源站压力。",
                "   ");
        assertThrows(ApiException.class, () -> parse(raw, "CDN"));
    }

    @Test
    void nodeTypeWhitelistEnforced() {
        String lowered = VALID_CDN_JSON.replace("\"type\": \"concept\"", "\"type\": \" Concept \"");
        assertEquals("concept", parse(lowered, "CDN").node().type());
        String bogus = VALID_CDN_JSON.replace("\"type\": \"concept\"", "\"type\": \"机器人\"");
        assertEquals("other", parse(bogus, "CDN").node().type());
        String missing = VALID_CDN_JSON.replace("\"type\": \"concept\",\n", "");
        assertEquals("other", parse(missing, "CDN").node().type());
    }

    @Test
    void oversizedFieldsAreTruncated() {
        String longName = "长".repeat(250);
        String longDefinition = "义".repeat(1200);
        String longRelationType = "关".repeat(150);
        String raw = """
                {"node": {"canonicalName": "%s", "nameEn": null, "aliases": [], "type": "concept",
                 "definition": "%s"},
                 "relations": [{"targetName": "缓存", "targetType": "concept",
                  "targetDefinition": "暂存数据。", "relationType": "%s"}]}
                """.formatted(longName, longDefinition, longRelationType);
        NormalizedPayload payload = parse(raw, "长");
        assertEquals(AiExpandPayloadParser.MAX_NAME_CHARS, payload.node().name().length());
        assertEquals(AiExpandPayloadParser.MAX_DEFINITION_CHARS, payload.node().definition().length());
        assertEquals(AiExpandPayloadParser.MAX_RELATION_TYPE_CHARS, payload.relations().get(0).relationType().length());
    }

    @Test
    void relationsCappedAtEight() {
        StringBuilder relations = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            if (i > 1) {
                relations.append(',');
            }
            relations.append("{\"targetName\": \"节点").append(i)
                    .append("\", \"targetType\": \"concept\", \"relationType\": \"相关\"}");
        }
        String raw = """
                {"node": {"canonicalName": "CDN", "nameEn": null, "aliases": [], "type": "concept",
                 "definition": "内容分发网络定义。"},
                 "relations": [%s]}
                """.formatted(relations);
        NormalizedPayload payload = parse(raw, "CDN");
        assertEquals(AiExpandPayloadParser.MAX_RELATIONS, payload.relations().size());
        assertEquals("节点8", payload.relations().get(7).targetName());
    }

    @Test
    void skipsBlankSelfAndIncompleteRelations() {
        String raw = """
                {"node": {"canonicalName": "CDN", "nameEn": null, "aliases": [], "type": "concept",
                 "definition": "内容分发网络定义。"},
                 "relations": [
                   {"targetName": "", "relationType": "相关"},
                   {"targetName": "cdn", "relationType": "自环"},
                   {"targetName": "缓存", "relationType": "  "},
                   {"targetType": "concept", "relationType": "缺目标名"},
                   {"targetName": "缓存", "targetType": "concept", "targetDefinition": "暂存数据。", "relationType": "依赖"}
                 ]}
                """;
        NormalizedPayload payload = parse(raw, "CDN");
        assertEquals(1, payload.relations().size());
        assertEquals("缓存", payload.relations().get(0).targetName());
        assertEquals("依赖", payload.relations().get(0).relationType());
    }

    @Test
    void topicMismatchThrowsLlmBadResponse() {
        String raw = """
                {"node": {"canonicalName": "HTTP 协议", "nameEn": "Hypertext Transfer Protocol",
                 "aliases": ["超文本传输协议"], "type": "concept", "definition": "超文本传输协议的定义。"},
                 "relations": []}
                """;
        ApiException ex = assertThrows(ApiException.class, () -> parse(raw, "CDN"));
        assertEquals("LLM_BAD_RESPONSE", ex.getCode());
    }

    @Test
    void topicMatchViaAliasOrQuestionWrapper() {
        NormalizedPayload payload = parse(VALID_CDN_JSON, "什么是CDN");
        assertEquals("CDN", payload.node().name());
        assertEquals("CDN", parse(VALID_CDN_JSON, "内容分发网络是什么").node().name());
    }

    @Test
    void queryWithSuffixMatchesByName() {
        assertEquals("CDN", parse(VALID_CDN_JSON, "CDN技术").node().name());
    }

    @Test
    void aliasesSplitDedupedAndCapped() {
        String raw = """
                {"node": {"canonicalName": "CDN", "nameEn": null,
                 "aliases": ["内容分发网络、CDN", "cdn", "边缘缓存"], "type": "concept",
                 "definition": "内容分发网络定义。"}, "relations": []}
                """;
        NormalizedPayload payload = parse(raw, "CDN");
        assertEquals(3, payload.node().aliases().size());
        assertEquals("内容分发网络", payload.node().aliases().get(0));
        assertEquals("CDN", payload.node().aliases().get(1));
    }

    @Test
    void controlCharsRemovedFromSingleLineFields() {
        String raw = """
                {"node": {"canonicalName": "CDN\\n技术", "nameEn": "Content\\tDelivery Network",
                 "aliases": ["内容分发\\r\\n网络"], "type": "concept", "definition": "内容分发网络定义。"},
                 "relations": []}
                """;
        NormalizedPayload payload = parse(raw, "CDN");
        assertEquals("CDN 技术", payload.node().name());
        assertEquals("Content Delivery Network", payload.node().nameEn());
        assertEquals("内容分发 网络", payload.node().aliases().get(0));
    }
}
