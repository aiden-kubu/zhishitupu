package com.knowledgegraph.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.chat.LlmClient;
import com.knowledgegraph.settings.LlmProfileService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BookComparisonTest {
    final LlmProfileService.DefaultModel model = new LlmProfileService.DefaultModel(1,"http://mock","mock","key",120,4096,0.1);
    DocumentUnderstandingService.Course book(String key) {
        return new DocumentUnderstandingService.Course(key,"教材"+key,"版本"+key,List.of(),List.of(key),List.of(1L));
    }
    @Test void samplesMatchingChapterOnceAndKeepsInputBounded() {
        var llm = mock(LlmClient.class);
        when(llm.complete(any(),anyList())).thenReturn("{\"verdict\":\"similar\"}");
        var rows = new ArrayList<DocumentUnderstandingService.Fragment>();
        long id=1;
        for(String source:List.of("a","b")) for(String title:List.of("第一章 线性表","第二章 树"))
            rows.add(new DocumentUnderstandingService.Fragment(id++,source,title+"\n"+"正文讲解".repeat(1000)));
        var overview = new DocumentUnderstandingService.Overview("教材",List.of(book("a"),book("b")));
        var first = BookComparison.compare(overview,rows,llm,model,new ObjectMapper());
        assertEquals("similar",first.get(0).verdict());
        assertEquals(2,first.get(0).evidenceChunkIds().size());
        verify(llm).complete(any(),argThat(messages -> messages.get(1).content().length()<4500));
        assertEquals(first,BookComparison.compare(overview,rows,llm,model,new ObjectMapper()));
    }
    @Test void contentsAloneNeverProvesDuplicateBooks() {
        var llm = mock(LlmClient.class);
        var rows=List.of(new DocumentUnderstandingService.Fragment(1,"a","目录\n第一章 线性表\n第二章 树"),
                new DocumentUnderstandingService.Fragment(2,"b","目录\n第一章 线性表\n第二章 树"));
        var result=BookComparison.compare(new DocumentUnderstandingService.Overview("教材",List.of(book("a"),book("b"))),rows,llm,model,new ObjectMapper());
        assertEquals("insufficient_evidence",result.get(0).verdict());
        verifyNoInteractions(llm);
    }
    @Test void sharedConceptBelongsToBothSourceBooksWithoutSubjectRouting() {
        var entity=new ExtractionService.DedupedEntity(new ExtractionPayloadParser.NormalizedEntity(
                "e","树",null,List.of(),"concept","定义",1.0,List.of(1L,2L)));
        var groups=LibraryOrganizationService.routeBooks(new ExtractionService.DeduplicatedCandidates(List.of(entity),List.of()),
                new DocumentUnderstandingService.Overview("教材",List.of(book("a"),book("b"))),
                List.of(Map.of("id",9L,"name","数据结构","description","课程")),Map.of(1L,"a",2L,"b"));
        assertEquals(2,groups.size());
        assertTrue(groups.stream().allMatch(g -> g.existingLibraryId()==null && g.entityKeys().equals(List.of("e"))));
    }
}
