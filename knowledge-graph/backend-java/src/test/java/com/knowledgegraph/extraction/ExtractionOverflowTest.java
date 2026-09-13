package com.knowledgegraph.extraction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ExtractionOverflowTest {
    @Test void keepsEntitiesBeyondThirtyBeforeValidatingTheirRelations() throws Exception {
        var mapper = new ObjectMapper();
        List<Object> entities = new ArrayList<>();
        for (int i = 1; i <= 31; i++) entities.add(Map.of("tempKey", "entity_" + i, "name", "节点" + i,
                "definition", "定义", "nodeType", "concept", "evidenceChunkIds", List.of(1)));
        String json = mapper.writeValueAsString(Map.of("entities", entities, "relations", List.of(Map.of(
                "sourceTempKey", "entity_31", "targetTempKey", "entity_29", "relationType", "相关", "evidenceChunkIds", List.of(1)))));
        var result = ExtractionPayloadParser.parse(mapper, json, Set.of(1L));
        assertEquals(31, result.entities().size());
        assertEquals("entity_31", result.relations().get(0).sourceTempKey());
    }
}
