package com.knowledgegraph.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.chat.LlmClient;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.settings.LlmProfileService;
import com.knowledgegraph.ingestion.ProcessingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties="spring.sql.init.mode=never")
@Transactional
class CourseOrganizationIntegrationTest {
    @Autowired JdbcClient jdbc;
    @Autowired DocumentUnderstandingService understanding;
    @Autowired LibraryOrganizationService organization;
    @Autowired ProcessingService processing;
    @Autowired ObjectMapper mapper;
    @MockitoBean LlmClient llm;
    long document,job,chunk;
    final LlmProfileService.DefaultModel model=new LlmProfileService.DefaultModel(1,"http://mock.local","mock","secret",120,8192,0.1);
    long insert(String sql,Map<String,?> args) { var key=new GeneratedKeyHolder();jdbc.sql(sql).params(args).update(key);return key.getKey().longValue(); }
    @BeforeEach void fixture() throws Exception {
        long library=insert("INSERT INTO knowledge_libraries(name,type,status) VALUES(:n,'topic','pending')",Map.of("n","识别测试"+System.nanoTime()));
        document=insert("INSERT INTO documents(library_id,original_name,stored_name,extension,sha256,storage_path) VALUES(:l,'abc.zip','x','zip',:s,'x')",Map.of("l",library,"s",UUID.randomUUID().toString()));
        long unit=insert("INSERT INTO document_units(document_id,unit_type,unit_index,source_locator,status) VALUES(:d,'page',1,'第一版 第 1 页','READY')",Map.of("d",document));
        chunk=insert("INSERT INTO document_chunks(unit_id,chunk_index,content,content_hash) VALUES(:u,0,'数据结构 C++ 教材','abc')",Map.of("u",unit));
        job=insert("INSERT INTO ingestion_jobs(document_id,status,stage) VALUES(:d,'AI_EXTRACTING','AI_ORGANIZING')",Map.of("d",document));
        when(llm.complete(any(),anyList())).thenAnswer(call -> mapper.writeValueAsString(new DocumentUnderstandingService.Overview("数据结构与算法",
                List.of(new DocumentUnderstandingService.Course("c1","数据结构与算法","数据结构课程",List.of("数据结构"),List.of("第一版"),List.of(chunk))))));
    }
    @Test void titleComesFromModelAndIsReturnedInJobWhileSourceNameIsPreserved() {
        understanding.ensure(document,model); understanding.ensure(document,model);
        assertEquals("数据结构与算法",processing.get(job).documentTitle());
        assertEquals("abc.zip",processing.get(job).documentName());
        verify(llm,times(1)).complete(any(),anyList());
        jdbc.sql("UPDATE document_metadata SET title='用户自定义' WHERE document_id=:d").param("d",document).update();
        jdbc.sql("UPDATE document_chunks SET content='新的正文' WHERE id=:c").param("c",chunk).update();
        understanding.ensure(document,model);
        assertEquals("用户自定义",processing.get(job).documentTitle());
        verify(llm,times(2)).complete(any(),anyList());
    }
    @Test void resumesClassificationAfterFailureWithoutRepeatingCompletedBatch() throws Exception {
        var initial=understanding.ensure(document,model);clearInvocations(llm);
        var overview=new DocumentUnderstandingService.Overview(initial.title(),List.of(initial.courses().get(0),
                new DocumentUnderstandingService.Course("c2","物理","独立物理课程",List.of(),List.of("第二份资料"),List.of(chunk))));
        var entities=new ArrayList<ExtractionService.DedupedEntity>();
        for(int i=0;i<201;i++) entities.add(new ExtractionService.DedupedEntity(new ExtractionPayloadParser.NormalizedEntity("e"+i,"知识"+i,null,List.of(),"concept","正文",0.9,List.of(chunk))));
        var candidates=new ExtractionService.DeduplicatedCandidates(entities,List.of());
        AtomicInteger calls=new AtomicInteger(); AtomicBoolean fail=new AtomicBoolean(true);
        when(llm.complete(any(),anyList())).thenAnswer(call->{
            assertEquals(201,processing.get(job).totalUnits(),"Show the known workload before waiting for the first model response");
            int n=calls.incrementAndGet(); if(n==2 && fail.get()) throw new ApiException(502,"LLM_UNREACHABLE","模拟中断");
            List<LlmClient.LlmMessage> messages=call.getArgument(1);
            var keys=new ArrayList<String>();mapper.readTree(messages.get(1).content()).path("knowledge").forEach(e->keys.add(e.path("key").asText()));
            return mapper.writeValueAsString(Map.of("groups",List.of(Map.of("name",overview.title(),"description","课程","entityKeys",keys))));
        });
        assertThrows(ApiException.class,()->organization.planBatches(candidates,model,List.of(),Set.of(),overview,job));
        assertEquals(100,jdbc.sql("SELECT next_offset FROM organization_checkpoints WHERE job_id=:id").param("id",job).query(Integer.class).single());
        fail.set(false);calls.set(0);
        var groups=organization.planBatches(candidates,model,List.of(),Set.of(),overview,job);
        assertEquals(2,calls.get()); assertEquals(1,groups.size());assertEquals(201,groups.get(0).entityKeys().size());
        calls.set(0);organization.planBatches(candidates,model,List.of(),Set.of(),overview,job);assertEquals(0,calls.get());
    }

    @Test void oneCourseAcrossVersionsUsesOneModelRoutingDecisionAndPreservesAllKnowledge() throws Exception {
        var overview=understanding.ensure(document,model);clearInvocations(llm);
        var entities=new ArrayList<ExtractionService.DedupedEntity>();
        for(int i=0;i<301;i++) entities.add(new ExtractionService.DedupedEntity(new ExtractionPayloadParser.NormalizedEntity("e"+i,"知识"+i,null,List.of(),"concept","正文",0.9,List.of(chunk))));
        var candidates=new ExtractionService.DeduplicatedCandidates(entities,List.of());
        when(llm.complete(any(),anyList())).thenAnswer(call->{
            List<LlmClient.LlmMessage> messages=call.getArgument(1);
            assertEquals(1,mapper.readTree(messages.get(1).content()).path("knowledge").size());
            return mapper.writeValueAsString(Map.of("groups",List.of(Map.of("name",overview.title(),"description","课程","entityKeys",List.of("course_identity")))));
        });
        Map<String,Object> libraryOrderA=new LinkedHashMap<>();libraryOrderA.put("id",123L);libraryOrderA.put("name","现有课程");
        Map<String,Object> libraryOrderB=new LinkedHashMap<>();libraryOrderB.put("name","现有课程");libraryOrderB.put("id",123L);
        var groups=organization.planBatches(candidates,model,List.of(libraryOrderA),Set.of(123L),overview,job);
        assertEquals(1,groups.size());assertEquals(301,groups.get(0).entityKeys().size());
        assertEquals(301,processing.get(job).processedUnits());
        assertEquals(groups,organization.planBatches(candidates,model,List.of(libraryOrderB),Set.of(123L),overview,job),
                "The same library data in a different map iteration order must reuse the checkpoint");
        verify(llm,times(1)).complete(any(),anyList());
    }
}
