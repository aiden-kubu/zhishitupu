package com.knowledgegraph.extraction;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.knowledgegraph.extraction.ExtractionPayloadParser.*;

class ExtractionEvidencePlanTest {
    @Test void deduplicatesInputButKeepsAllVersionEvidenceAndExcludesInvalidText() {
        var all=List.of(new ExtractionService.ChunkRow(1,"栈"),new ExtractionService.ChunkRow(2,"栈"),
                new ExtractionService.ChunkRow(3,"队列"),new ExtractionService.ChunkRow(4,"OCR解释"));
        var plan=ExtractionEvidencePlan.plan(all,Set.of(1L,2L,3L),false);
        assertEquals(List.of(1L,3L),plan.input().stream().map(ExtractionService.ChunkRow::id).toList());
        var projected=ExtractionEvidencePlan.project(new NormalizedExtraction(List.of(
                new NormalizedEntity("a","栈",null,List.of(),"concept","后进先出",0.9,List.of(1L)),
                new NormalizedEntity("b","伪知识",null,List.of(),"concept","解释",0.9,List.of(4L))),
                List.of(new NormalizedRelation("a","b","关联",0.9,List.of(1L)))),plan.copies());
        assertEquals(1,projected.entities().size());assertEquals(List.of(1L,2L),projected.entities().get(0).evidenceChunkIds());
        assertTrue(projected.relations().isEmpty());
    }
    @Test void legacyPlanPreservesOriginalBoundariesWhileExcludingBadEvidence() {
        var all=List.of(new ExtractionService.ChunkRow(1,"正文"),new ExtractionService.ChunkRow(2,"OCR解释"));
        var plan=ExtractionEvidencePlan.plan(all,Set.of(1L),true);
        assertEquals(all,plan.input());assertTrue(plan.copies().get(2L).isEmpty());
    }
}
