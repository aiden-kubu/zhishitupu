package com.knowledgegraph.assistant.assessment;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.learning.LearningReadRepository;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.knowledgegraph.assistant.assessment.AssessmentAgentToolProviderTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssessmentDraftValidatorTest {
    private static final String ITEM = """
            {"localId":"draft:q1","prompt":"What is A?","topic":"A","options":[{"id":"a","text":"One"},{"id":"b","text":"Two"}],"referenceOptionId":"a","explanation":"Candidate explanation","sourceIds":%s}
            """;
    private static String draft(String sourceIds) {
        return "{\"pathId\":91,\"stepId\":101,\"expectedSnapshotFingerprint\":\"" + FP
                + "\",\"items\":[" + ITEM.formatted(sourceIds) + "]}";
    }
    private static LearningReadRepository learning() {
        var repo = mock(LearningReadRepository.class);
        when(repo.currentPath(SCOPE)).thenReturn(path(FP, "current"));
        when(repo.node(SCOPE, 1)).thenReturn(Optional.of(node()));
        return repo;
    }
    private static EvidenceRef source(Scope scope) {
        String id = "research:chunk:11:101:h:scope:" + AssessmentEvidence.scopeFingerprint(scope);
        return new EvidenceRef(id, EvidenceKind.SOURCE, new ObjectRef("document_chunk", "101", "h"),
                scope, "document=11;unit=1;chunk=101", "source");
    }
    private static AssessmentContextReader host(EvidenceRef ref) {
        Artifact artifact = new Artifact("original", "research", SCOPE, List.of(SCOPE), Outcome.PARTIAL,
                "source", "{}", List.of(ref), List.of(), List.of("UPSTREAM_PARTIAL"), Instant.now());
        var snap = new AssessmentContextReader.Snapshot(Set.of("research"),
                Map.of("research", Role.GRAPH_RESEARCH), List.of(artifact), List.of());
        return (run, task) -> Optional.of(snap);
    }

    @Test void sourceLessCandidateIsExplicitlyUnexecutedAndPartialWithNoWrite() throws Exception {
        var learning = learning();
        var history = mock(AssessmentReadRepository.class);
        var provider = new AssessmentAgentToolProvider(learning, history,
                mock(ResearchReadRepository.class), mock(SourceLocator.class), (run, task) -> Optional.empty());
        var result = tool(provider, "assessment.prepare_draft").execute(ctx(Role.ASSESSMENT_ANALYSIS), parse(draft("[]")));
        assertEquals(ToolStatus.PARTIAL, result.status());
        var body = data(result);
        assertEquals("unexecuted_draft", body.path("status").asText());
        assertEquals(1, body.path("questionCount").asInt());
        assertEquals("source_missing", body.path("items").get(0).path("evidenceGap").asText());
        assertFalse(body.path("saved").asBoolean());
        assertFalse(body.path("executionAllowed").asBoolean());
        assertFalse(body.path("progressChanged").asBoolean());
        assertFalse(body.path("formalAssessmentAvailable").asBoolean());
        assertTrue(body.path("assessmentId").isNull());
        verifyNoInteractions(history);
    }

    @Test void originalDirectSourceIsRereadByExactTripleButForgedIdRejects() throws Exception {
        var learning = learning();
        when(learning.nodeSourceLinked(SCOPE, 1, 11, 101)).thenReturn(true);
        var research = mock(ResearchReadRepository.class);
        var meta = new ResearchReadRepository.SourceMeta(11, 101, 1, 0, "PDF", "page1", "h",
                "COMPLETED", "active", "READY");
        when(research.sourceMeta(SCOPE, 11, 101)).thenReturn(Optional.of(meta));
        var locator = mock(SourceLocator.class);
        when(locator.chunk(SCOPE, 11, 101, 4000)).thenReturn(Optional.of(
                new SourceLocator.Chunk(101, 1, 0, "h", 0, 4, "text", false)));
        EvidenceRef ref = source(SCOPE);
        var provider = new AssessmentAgentToolProvider(learning, mock(AssessmentReadRepository.class),
                research, locator, host(ref));
        var result = tool(provider, "assessment.prepare_draft").execute(ctx(Role.ASSESSMENT_ANALYSIS),
                parse(draft("[\"" + ref.sourceId() + "\"]")));
        assertEquals(ToolStatus.PARTIAL, result.status());
        assertEquals(2, result.evidence().size());
        assertEquals(ref, result.evidence().get(1));
        assertEquals("not_evaluated", data(result).path("items").get(0).path("semanticSupport").asText());
        verify(locator).chunk(SCOPE, 11, 101, 4000);
        assertEquals("UNTRUSTED_SOURCE_ID", tool(provider, "assessment.prepare_draft")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse(draft("[\"research:forged\"]"))).errorCode());
    }

    @Test void staleSourceNodeScopeAndPathBaselineCannotBecomeEvidence() throws Exception {
        var learning = learning();
        when(learning.nodeSourceLinked(SCOPE, 1, 11, 101)).thenReturn(true);
        var research = mock(ResearchReadRepository.class);
        when(research.sourceMeta(SCOPE, 11, 101)).thenReturn(Optional.of(new ResearchReadRepository.SourceMeta(
                11, 101, 1, 0, "PDF", "page1", "changed", "COMPLETED", "active", "READY")));
        var locator = mock(SourceLocator.class);
        EvidenceRef ref = source(SCOPE);
        var provider = new AssessmentAgentToolProvider(learning, mock(AssessmentReadRepository.class),
                research, locator, host(ref));
        assertEquals("STALE_OR_UNAVAILABLE_SOURCE", tool(provider, "assessment.prepare_draft")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse(draft("[\"" + ref.sourceId() + "\"]"))).errorCode());
        reset(learning);
        when(learning.currentPath(SCOPE)).thenReturn(path("b".repeat(64), "completed"));
        assertEquals("STALE_PATH_BASELINE", tool(provider, "assessment.prepare_draft")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse(draft("[]"))).errorCode());
        Scope nodes = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of(1L));
        when(learning.currentPath(nodes)).thenReturn(path(FP, "current"));
        when(learning.node(nodes, 1)).thenReturn(Optional.of(node()));
        var withNodes = new AssistantTool.Context("run", "assess", Role.ASSESSMENT_ANALYSIS, nodes,
                Instant.now().plusSeconds(30), () -> false);
        var nodesProvider = new AssessmentAgentToolProvider(learning, mock(AssessmentReadRepository.class),
                research, locator, (run, task) -> Optional.empty());
        assertEquals("NODE_SCOPE_UNSUPPORTED", tool(nodesProvider, "assessment.prepare_draft")
                .execute(withNodes, parse(draft("[\"" + ref.sourceId() + "\"]"))).errorCode());
    }

    @Test void malformedQuestionAndFormalFieldsRejectBeforeReads() throws Exception {
        var learning = mock(LearningReadRepository.class);
        var provider = provider(learning, mock(AssessmentReadRepository.class));
        assertEquals("UNKNOWN_FIELD", tool(provider, "assessment.prepare_draft")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse(draft("[]").replace("\"items\"", "\"score\":100,\"items\""))).errorCode());
        assertEquals("REFERENCE_OPTION_MISSING", tool(provider, "assessment.prepare_draft")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse(draft("[]").replace("\"referenceOptionId\":\"a\"",
                        "\"referenceOptionId\":\"c\""))).errorCode());
        verifyNoInteractions(learning);
    }

    @Test void draftRejectsStepChangeAndUnlinkedOriginalSource() throws Exception {
        var learning = learning();
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"), path("b".repeat(64), "completed"));
        var provider = new AssessmentAgentToolProvider(learning, mock(AssessmentReadRepository.class),
                mock(ResearchReadRepository.class), mock(SourceLocator.class), (run, task) -> Optional.empty());
        assertEquals("STALE_PATH_BASELINE", tool(provider, "assessment.prepare_draft")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse(draft("[]"))).errorCode());
        reset(learning);
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"));
        when(learning.node(SCOPE, 1)).thenReturn(Optional.of(node()));
        EvidenceRef ref = source(SCOPE);
        provider = new AssessmentAgentToolProvider(learning, mock(AssessmentReadRepository.class),
                mock(ResearchReadRepository.class), mock(SourceLocator.class), host(ref));
        assertEquals("NODE_SOURCE_NOT_LINKED", tool(provider, "assessment.prepare_draft")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse(draft("[\"" + ref.sourceId() + "\"]"))).errorCode());
    }
}
