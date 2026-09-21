package com.knowledgegraph.extraction;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.knowledgegraph.extraction.ExtractionPayloadParser.*;

/** Reduce repeated model input while retaining every valid source location; preserve pre-upgrade batch boundaries. */
@Component
public class ExtractionEvidencePlan {
    private final JdbcClient jdbc;
    public ExtractionEvidencePlan(JdbcClient jdbc) { this.jdbc=jdbc; }
    record Plan(List<ExtractionService.ChunkRow> input,Map<Long,List<Long>> copies) {}

    Plan prepare(long jobId,long documentId,List<ExtractionService.ChunkRow> all) {
        Set<Long> valid=new HashSet<>(jdbc.sql("""
            SELECT c.id FROM document_chunks c JOIN document_units u ON u.id=c.unit_id
            WHERE u.document_id=:id AND u.status IN ('READY','OCR_OK')
            """).param("id",documentId).query(Long.class).list());
        String strategy=jdbc.sql("SELECT strategy FROM extraction_plan_versions WHERE job_id=:id")
                .param("id",jobId).query(String.class).optional().orElse(null);
        if(strategy==null) {
            int staged=jdbc.sql("SELECT COUNT(*) FROM extraction_batch_results WHERE job_id=:id").param("id",jobId).query(Integer.class).single();
            strategy=staged>0 ? "legacy-v1" : "deduplicated-v1";
            jdbc.sql("INSERT INTO extraction_plan_versions(job_id,strategy) VALUES(:id,:s)")
                    .param("id",jobId).param("s",strategy).update();
        }
        return plan(all,valid,"legacy-v1".equals(strategy));
    }

    static Plan plan(List<ExtractionService.ChunkRow> all,Set<Long> valid,boolean legacy) {
        Map<String,List<Long>> byText=new LinkedHashMap<>();
        Map<String,ExtractionService.ChunkRow> unique=new LinkedHashMap<>();
        for(var c:all) if(valid.contains(c.id())) {
            byText.computeIfAbsent(c.content(),k->new ArrayList<>()).add(c.id());unique.putIfAbsent(c.content(),c);
        }
        Map<Long,List<Long>> copies=new HashMap<>();
        for(var c:all) copies.put(c.id(),valid.contains(c.id()) ? List.copyOf(byText.get(c.content())) : List.of());
        return new Plan(legacy ? all : List.copyOf(unique.values()),copies);
    }

    static NormalizedExtraction project(NormalizedExtraction value,Map<Long,List<Long>> copies) {
        List<NormalizedEntity> entities=new ArrayList<>(); Set<String> keys=new HashSet<>();
        for(var e:value.entities()) {
            var evidence=expand(e.evidenceChunkIds(),copies);
            if(evidence.isEmpty()) continue;
            entities.add(new NormalizedEntity(e.tempKey(),e.name(),e.nameEn(),e.aliases(),e.nodeType(),e.definition(),e.confidence(),evidence));keys.add(e.tempKey());
        }
        List<NormalizedRelation> relations=new ArrayList<>();
        for(var r:value.relations()) {
            var evidence=expand(r.evidenceChunkIds(),copies);
            if(!evidence.isEmpty() && keys.contains(r.sourceTempKey()) && keys.contains(r.targetTempKey()))
                relations.add(new NormalizedRelation(r.sourceTempKey(),r.targetTempKey(),r.relationType(),r.confidence(),evidence));
        }
        return new NormalizedExtraction(entities,relations);
    }
    private static List<Long> expand(List<Long> ids,Map<Long,List<Long>> copies) {
        Set<Long> result=new LinkedHashSet<>();ids.forEach(id->result.addAll(copies.getOrDefault(id,List.of())));return List.copyOf(result);
    }
}
