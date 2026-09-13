package com.knowledgegraph.insight;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "spring.sql.init.mode=never")
@Transactional // All fixtures, including deliberately orphaned candidates, roll back after this test.
class InsightPendingReviewIntegrationTest {
    @Autowired JdbcClient jdbc;
    @Autowired InsightService service;
    long insert(String sql, Map<String, ?> params) {
        var keys = new GeneratedKeyHolder(); jdbc.sql(sql).params(params).update(keys); return keys.getKey().longValue();
    }
    @Test void countsOnlyPendingCandidatesWithExistingResumableDocumentJobs() {
        long baseline = service.summary().pendingReviewCount();
        String suffix = Long.toString(System.nanoTime());
        long library = insert("INSERT INTO knowledge_libraries(name,type,status) VALUES(:n,'topic','active')", Map.of("n","统计测试"+suffix));
        long document = insert("INSERT INTO documents(library_id,original_name,stored_name,extension,size_bytes,sha256,storage_path,status) VALUES(:l,'test.pdf',:s,'pdf',1,:s,'test','AWAITING_REVIEW')",Map.of("l",library,"s",suffix));
        long job = insert("INSERT INTO ingestion_jobs(document_id,stage,status) VALUES(:d,'AWAITING_REVIEW','AWAITING_REVIEW')",Map.of("d",document));
        insert("INSERT INTO entity_candidates(job_id,name,review_status) VALUES(:j,'test','PENDING')",Map.of("j",job));
        insert("INSERT INTO relation_candidates(job_id,relation_type,review_status) VALUES(:j,'test','PENDING')",Map.of("j",job));
        insert("INSERT INTO entity_candidates(job_id,name,review_status) VALUES(:j,'accepted','ACCEPTED')",Map.of("j",job));
        for (String status : new String[]{"AWAITING_REVIEW","AI_REVIEWING","FAILED","CANCELLED"}) {
            jdbc.sql("UPDATE ingestion_jobs SET status=:s WHERE id=:j").param("s",status).param("j",job).update();
            assertEquals(baseline+2,service.summary().pendingReviewCount(),status);
        }
        for (String status : new String[]{"COMPLETED","IMPORTING","AI_EXTRACTING"}) {
            jdbc.sql("UPDATE ingestion_jobs SET status=:s WHERE id=:j").param("s",status).param("j",job).update();
            assertEquals(baseline,service.summary().pendingReviewCount(),status);
        }
        jdbc.sql("DELETE FROM ingestion_jobs WHERE id=:j").param("j",job).update();
        assertEquals(2,jdbc.sql("SELECT COUNT(*) FROM entity_candidates WHERE job_id=:j").param("j",job).query(Integer.class).single());
        assertEquals(1,jdbc.sql("SELECT COUNT(*) FROM relation_candidates WHERE job_id=:j").param("j",job).query(Integer.class).single());
        assertEquals(baseline,service.summary().pendingReviewCount(),"Orphan history is retained but excluded");
    }
}
