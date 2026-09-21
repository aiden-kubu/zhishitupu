package com.knowledgegraph.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.*;
import com.knowledgegraph.chat.LlmClient;
import com.knowledgegraph.settings.LlmProfileService;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class LibraryOrganizationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void doesNotRejectThirteenValidRoutingGroups() throws Exception {
        List<Map<String,Object>> groups=new ArrayList<>();Set<String> keys=new HashSet<>();
        for(int i=0;i<13;i++) {keys.add("e"+i);groups.add(Map.of("name","独立课程"+i,"entityKeys",List.of("e"+i)));}
        var parsed=LibraryOrganizationService.parse(mapper,mapper.writeValueAsString(Map.of("groups",groups)),keys,Set.of());
        assertEquals(13,parsed.size());
        assertEquals(13,parsed.stream().flatMap(g->g.entityKeys().stream()).distinct().count());
    }
    @Test void classifiesMoreThanFourHundredEntitiesWithoutOmissionOrDuplicateTopics() throws Exception {
        var llm = mock(LlmClient.class);
        var service = new LibraryOrganizationService(null, llm, mapper, null);
        var candidates = new ArrayList<ExtractionService.DedupedEntity>();
        for (int i = 0; i < 451; i++) candidates.add(new ExtractionService.DedupedEntity(
                new ExtractionPayloadParser.NormalizedEntity("e" + i, "知识" + i, null, List.of(), "concept", "定义", 0.9, List.of(1L))));
        when(llm.complete(any(), anyList())).thenAnswer(call -> {
            List<LlmClient.LlmMessage> messages = call.getArgument(1);
            var input = mapper.readTree(messages.get(1).content());
            assertTrue(input.path("knowledge").size() <= 100);
            List<String> keys = new ArrayList<>();
            input.path("knowledge").forEach(e -> keys.add(e.path("key").asText()));
            return mapper.writeValueAsString(Map.of("groups", List.of(Map.of("name", "数据结构", "description", "基础知识", "entityKeys", keys))));
        });
        var groups = service.planBatches(new ExtractionService.DeduplicatedCandidates(candidates, List.of()),
                new LlmProfileService.DefaultModel(1, "http://localhost", "test", "secret", 120, 8192, 0.1), List.of(), Set.of());
        assertEquals(1, groups.size());
        assertEquals(451, new HashSet<>(groups.get(0).entityKeys()).size());
        verify(llm, times(5)).complete(any(), anyList());
    }
    @Test void acceptsSplitAndExistingLibraryWithSharedKnowledge() {
        var groups = LibraryOrganizationService.parse(mapper, """
            {"groups":[
              {"existingLibraryId":7,"name":"网络","description":"已有主题","entityKeys":["a"]},
              {"existingLibraryId":null,"name":"数据结构","description":"新主题","entityKeys":["a","b"]}
            ]}
            """, Set.of("a", "b"), Set.of(7L));
        assertEquals(2, groups.size());
        assertEquals(7L, groups.get(0).existingLibraryId());
        assertNull(groups.get(1).existingLibraryId());
    }
    @Test void rejectsUnknownLibraryAndUnknownEntityAndMissingEntity() {
        for (String json : new String[]{
            "{\"groups\":[{\"existingLibraryId\":99,\"name\":\"网络\",\"entityKeys\":[\"a\",\"b\"]}]}",
            "{\"groups\":[{\"name\":\"网络\",\"entityKeys\":[\"fake\"]}]}",
            "{\"groups\":[{\"name\":\"网络\",\"entityKeys\":[\"a\"]}]}"
        }) assertThrows(ApiException.class, () -> LibraryOrganizationService.parse(mapper, json, Set.of("a", "b"), Set.of(7L)));
    }
    @Test void rejectsMalformedEmptyAndMarkupOutput() {
        for (String json : new String[]{"bad", "{}", "{\"groups\":[]}",
            "{\"groups\":[{\"name\":\"<script>\",\"entityKeys\":[\"a\"]}]}"})
            assertThrows(ApiException.class, () -> LibraryOrganizationService.parse(mapper, json, Set.of("a"), Set.of()));
    }
}
