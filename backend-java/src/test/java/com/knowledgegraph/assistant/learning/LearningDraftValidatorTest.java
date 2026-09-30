package com.knowledgegraph.assistant.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LearningDraftValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Scope SCOPE = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(), Set.of());
    private final LearningReadRepository repo = mock(LearningReadRepository.class);
    private final ResearchReadRepository research = mock(ResearchReadRepository.class);
    private final SourceLocator source = mock(SourceLocator.class);
    private static JsonNode parse(String value) throws Exception { return JSON.readTree(value); }
    private static final String STEPS = """
            [{"localId":"draft:one","nodeId":1,"objective":"理解1","reason":"从基础开始","reasonKind":"inference","estimatedMinutes":20},
             {"localId":"draft:two","nodeId":2,"objective":"理解2","reason":"再学关联","reasonKind":"inference","estimatedMinutes":25}]
            """;
    private static String path(String mode, String base) {
        return "{\"targetLibraryId\":1,\"mode\":\"" + mode + "\",\"goal\":\"理解概念\",\"difficulty\":\"beginner\","
                + base + "\"steps\":" + STEPS + "}";
    }
    private void setupNodes() {
        when(repo.libraryVisible(SCOPE, 1)).thenReturn(true);
        when(repo.node(any(), eq(1L))).thenReturn(Optional.of(LearningAgentToolProviderTest.node(1)));
        when(repo.node(any(), eq(2L))).thenReturn(Optional.of(LearningAgentToolProviderTest.node(2)));
    }

    @Test void newDraftHasRealNodesOnlyLocalIdsAndNoWriteReceipt() throws Exception {
        setupNodes();
        when(repo.currentPath(SCOPE)).thenReturn(LearningReadRepository.Path.none());
        var draft = LearningDraftValidator.path(parse(path("new", "")), SCOPE, repo, research, source, null);
        assertEquals(false, draft.data().get("saved"));
        assertEquals(false, draft.data().get("executionAllowed"));
        assertEquals(false, draft.data().get("progressChanged"));
        assertEquals(null, draft.data().get("formalRevision"));
        assertEquals(45, draft.data().get("estimatedTotalMinutes"));
        assertEquals(2, ((List<?>) draft.data().get("steps")).size());
        verifyNoInteractions(source);
    }

    @Test void adjustRequiresFreshBaselineAndDeterministicCompletionImpact() throws Exception {
        setupNodes();
        var old = new LearningReadRepository.Path(LearningReadRepository.PathState.AVAILABLE,
                91L, 1L, "旧目标", "beginner", "active", "u", "a".repeat(64),
                List.of(new LearningReadRepository.Step(101, "old1", 2, 0, "节点2", 20, "completed", "v", 99, "u"),
                        new LearningReadRepository.Step(102, "old2", 1, 1, "节点1", 30, "locked", null, null, "u")));
        when(repo.currentPath(SCOPE)).thenReturn(old);
        assertEquals("STALE_PATH_BASELINE", assertThrows(IllegalArgumentException.class,
                () -> LearningDraftValidator.path(parse(path("adjust", "\"basePathId\":91,\"baseSnapshotFingerprint\":\"" + "b".repeat(64) + "\",")),
                        SCOPE, repo, research, source, null)).getMessage());
        var draft = LearningDraftValidator.path(parse(path("adjust", "\"basePathId\":91,\"baseSnapshotFingerprint\":\"" + "a".repeat(64) + "\",")),
                SCOPE, repo, research, source, null);
        var diff = (Map<?, ?>) draft.data().get("diff");
        var oldSteps = (List<?>) diff.get("existingSteps");
        assertEquals("reordered", ((Map<?, ?>) oldSteps.get(0)).get("change"));
        assertEquals("requires_formal_review", ((Map<?, ?>) oldSteps.get(0)).get("completedStepImpact"));
        assertEquals(91L, ((Map<?, ?>) draft.data().get("basePathRef")).get("pathId"));
    }

    @Test void rejectsOutOfScopeInactiveAndInventedFormalFields() throws Exception {
        setupNodes();
        when(repo.currentPath(SCOPE)).thenReturn(LearningReadRepository.Path.none());
        assertEquals("UNKNOWN_FIELD", assertThrows(IllegalArgumentException.class,
                () -> LearningDraftValidator.pathShape(parse(path("new", "\"assessmentId\":3,")))).getMessage());
        when(repo.node(any(), eq(2L))).thenReturn(Optional.empty());
        assertEquals("NODE_OUT_OF_SCOPE", assertThrows(IllegalArgumentException.class,
                () -> LearningDraftValidator.path(parse(path("new", "")), SCOPE, repo, research, source, null)).getMessage());
    }

    @Test void layeredExplanationAndTwoInformalQuestionsCannotClaimGrades() throws Exception {
        setupNodes();
        var explanation = LearningDraftValidator.explanation(parse("""
                {"nodeId":1,"blocks":[{"kind":"system_fact","text":"节点存在"},
                 {"kind":"model_explanation","text":"一种理解方式"}]}
                """), SCOPE, repo, research, source, null);
        assertEquals("layered_explanation", explanation.data().get("kind"));
        assertTrue(explanation.limitations().contains("MODEL_TEXT_REQUIRES_HUMAN_REVIEW"));
        var practice = LearningDraftValidator.practice(parse("""
                {"nodeId":1,"items":[{"localId":"draft:q1","prompt":"问题一","answer":"答案一","explanation":"解释一"},
                {"localId":"draft:q2","prompt":"问题二","answer":"答案二","explanation":"解释二"}]}
                """), SCOPE, repo, research, source, null);
        assertEquals("informal_practice", practice.data().get("mode"));
        assertEquals(false, practice.data().get("progressChanged"));
        assertEquals(2, ((List<?>) practice.data().get("items")).size());
        assertEquals("UNKNOWN_FIELD", assertThrows(IllegalArgumentException.class,
                () -> LearningDraftValidator.practiceShape(parse("""
                        {"nodeId":1,"items":[{"localId":"draft:q1","prompt":"问题一","answer":"答案一","explanation":"解释一","score":100}]}
                        """))).getMessage());
    }

    @Test void inactiveNodeCannotBeUsedForExplanationOrPractice() throws Exception {
        var inactive = new ResearchReadRepository.Node(1, "旧节点", null, "concept", "定义", "inactive",
                "u", "manual", "pending", List.of(1L), List.of(), null, false);
        when(repo.node(SCOPE, 1)).thenReturn(Optional.of(inactive));
        assertEquals("INACTIVE_NODE", assertThrows(IllegalArgumentException.class,
                () -> LearningDraftValidator.explanation(parse("""
                        {"nodeId":1,"blocks":[{"kind":"recommendation","text":"看看旧节点"}]}
                        """), SCOPE, repo, research, source, null)).getMessage());
        assertEquals("INACTIVE_NODE", assertThrows(IllegalArgumentException.class,
                () -> LearningDraftValidator.practice(parse("""
                        {"nodeId":1,"items":[{"localId":"draft:q1","prompt":"问题","answer":"答案","explanation":"解释"}]}
                        """), SCOPE, repo, research, source, null)).getMessage());
    }
}
