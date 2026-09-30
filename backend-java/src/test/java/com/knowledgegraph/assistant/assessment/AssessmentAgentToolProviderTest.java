package com.knowledgegraph.assistant.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.learning.LearningReadRepository;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssessmentAgentToolProviderTest {
    static final Scope SCOPE = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of());
    static final String FP = "a".repeat(64);
    static final ObjectMapper JSON = new ObjectMapper();
    static LearningReadRepository.Path path(String fingerprint, String status) {
        return new LearningReadRepository.Path(LearningReadRepository.PathState.AVAILABLE, 91L, 1L,
                "goal", "beginner", "active", "2026-09-26", fingerprint,
                List.of(new LearningReadRepository.Step(101, "step-a", 1, 0, "node", 15,
                        status, null, 99, "2026-09-26")));
    }
    static LearningReadRepository.Path largePath(long id, String fingerprint) {
        return new LearningReadRepository.Path(LearningReadRepository.PathState.AVAILABLE,
                Long.valueOf(id), 1L, "goal", "beginner", "active", "2026-09-26", fingerprint,
                List.of(new LearningReadRepository.Step(1001, "step-large", 1, 0, "node", 15,
                        "current", null, 99, "2026-09-26")));
    }
    static ResearchReadRepository.Node node() {
        return new ResearchReadRepository.Node(1, "node", null, "concept", null,
                "active", "2026-09-26", "manual", "approved", List.of(1L), List.of(), null);
    }
    static AssistantTool.Context ctx(Role role) {
        return new AssistantTool.Context("run", "assess", role, SCOPE, Instant.now().plusSeconds(30), () -> false);
    }
    static JsonNode parse(String raw) {
        try { return JSON.readTree(raw); } catch (Exception ex) { throw new AssertionError(ex); }
    }
    static JsonNode data(ToolResult result) { return parse(result.dataJson()); }
    static AssistantTool tool(AssessmentAgentToolProvider provider, String name) {
        return provider.tools().stream().filter(t -> t.descriptor().name().equals(name)).findFirst().orElseThrow();
    }
    static AssessmentAgentToolProvider provider(LearningReadRepository learning, AssessmentReadRepository history) {
        return new AssessmentAgentToolProvider(learning, history, mock(ResearchReadRepository.class),
                mock(SourceLocator.class), (run, task) -> Optional.empty());
    }

    @Test void fourClosedRoleBoundToolsRejectFormalFieldsAndWrongRole() throws Exception {
        var learning = mock(LearningReadRepository.class);
        var provider = provider(learning, mock(AssessmentReadRepository.class));
        assertEquals(List.of("assessment.read_context", "assessment.read_legacy_history",
                "assessment.read_valid_results", "assessment.prepare_draft"),
                provider.tools().stream().map(t -> t.descriptor().name()).toList());
        assertEquals(List.of(ToolEffect.READ_ONLY, ToolEffect.READ_ONLY, ToolEffect.READ_ONLY, ToolEffect.DRAFT_ONLY),
                provider.tools().stream().map(t -> t.descriptor().effect()).toList());
        assertTrue(provider.tools().stream().allMatch(t -> t.descriptor().roles().equals(Set.of(Role.ASSESSMENT_ANALYSIS))));
        assertTrue(provider.tools().stream().allMatch(t -> parse(t.descriptor().argumentsSchemaJson())
                .path("additionalProperties").isBoolean() && !parse(t.descriptor().argumentsSchemaJson())
                .path("additionalProperties").booleanValue()));
        assertEquals("UNKNOWN_FIELD", tool(provider, "assessment.read_valid_results")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"score\":100}")).errorCode());
        assertEquals("ROLE_NOT_ALLOWED", tool(provider, "assessment.read_context")
                .execute(ctx(Role.LEARNING_COACH), parse("{}")).errorCode());
        verifyNoInteractions(learning);
    }

    @Test void currentStageIsRealButLegacyScoreCannotPassAndNoCurrentIsSeparate() throws Exception {
        var learning = mock(LearningReadRepository.class);
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"));
        when(learning.node(SCOPE, 1)).thenReturn(Optional.of(node()));
        var provider = provider(learning, mock(AssessmentReadRepository.class));
        var result = tool(provider, "assessment.read_context").execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{}"));
        assertEquals(ToolStatus.PARTIAL, result.status(), result.errorCode());
        assertEquals(101, data(result).path("currentStepId").asInt());
        assertEquals("step-a", data(result).path("currentStepKey").asText());
        assertEquals("legacy_unverified", data(result).path("steps").get(0).path("scoreValidity").asText());
        assertFalse(data(result).path("formalAvailable").asBoolean());
        assertTrue(data(result).path("draftReady").asBoolean());
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "completed"));
        result = tool(provider, "assessment.read_context").execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{}"));
        assertEquals("no_current_step", data(result).path("state").asText());
        assertFalse(data(result).path("draftReady").asBoolean());
        when(learning.currentPath(SCOPE)).thenReturn(LearningReadRepository.Path.unavailable());
        result = tool(provider, "assessment.read_context").execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{}"));
        assertEquals("unavailable", data(result).path("state").asText());
        when(learning.currentPath(SCOPE)).thenReturn(LearningReadRepository.Path.none());
        result = tool(provider, "assessment.read_context").execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{}"));
        assertEquals("none", data(result).path("state").asText());
    }

    @Test void staleBaselineAndStepOwnershipRejectWithoutRebinding() throws Exception {
        var learning = mock(LearningReadRepository.class);
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"));
        when(learning.node(SCOPE, 1)).thenReturn(Optional.of(node()));
        var provider = provider(learning, mock(AssessmentReadRepository.class));
        assertEquals("STALE_PATH_BASELINE", tool(provider, "assessment.read_context")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":91,\"snapshotFingerprint\":\""
                        + "b".repeat(64) + "\"}")).errorCode());
        assertEquals("STEP_NOT_IN_PATH", tool(provider, "assessment.read_valid_results")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":91,\"stepId\":999}")).errorCode());
        var result = tool(provider, "assessment.read_valid_results")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":91,\"stepId\":101}"));
        assertEquals(ToolStatus.PARTIAL, result.status());
        assertFalse(data(result).path("formalAvailable").asBoolean());
        assertTrue(data(result).path("validResults").isEmpty());
        assertTrue(data(result).path("weaknessItems").isEmpty());
        assertTrue(data(result).path("mastery").isNull());
        assertEquals("formal_scoring_unavailable", data(result).path("reason").asText());
    }

    @Test void historyIsPagedLegacyOnlyAndUnknownStepRemainsUnknown() throws Exception {
        var learning = mock(LearningReadRepository.class);
        var history = mock(AssessmentReadRepository.class);
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"));
        when(history.legacyAttempts(SCOPE, 91L, null, 1, null)).thenReturn(new AssessmentReadRepository.Page(
                List.of(new AssessmentReadRepository.LegacyAttempt(55, 91, null, null, 100, 2, 2,
                        "[\"client weak\"]", Instant.parse("2026-09-25T00:00:00Z"))), true));
        var provider = provider(learning, history);
        var result = tool(provider, "assessment.read_legacy_history").execute(ctx(Role.ASSESSMENT_ANALYSIS),
                parse("{\"pathId\":91,\"limit\":1}"));
        assertEquals(ToolStatus.PARTIAL, result.status(), result.errorCode());
        assertEquals("legacy_unverified", data(result).path("records").get(0).path("validity").asText());
        assertEquals("unknown", data(result).path("records").get(0).path("stageAttribution").asText());
        assertTrue(data(result).path("records").get(0).path("stepId").isNull());
        assertEquals(55, data(result).path("nextCursor").asInt());
        assertFalse(data(result).path("completeCoverage").asBoolean());
        when(history.legacyAttempts(SCOPE, 91L, 101L, 10, 55L)).thenReturn(new AssessmentReadRepository.Page(List.of(), false));
        result = tool(provider, "assessment.read_legacy_history").execute(ctx(Role.ASSESSMENT_ANALYSIS),
                parse("{\"pathId\":91,\"stepId\":101,\"beforeAttemptId\":55}"));
        assertEquals("filtered_step", data(result).path("coverageAppliesTo").asText());
        assertFalse(data(result).path("completeCoverage").asBoolean());
    }

    @Test void staleDuringHistoryAndForeignStepReject() throws Exception {
        var learning = mock(LearningReadRepository.class);
        var history = mock(AssessmentReadRepository.class);
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"), path("b".repeat(64), "completed"));
        when(history.legacyAttempts(SCOPE, 91L, null, 10, null))
                .thenReturn(new AssessmentReadRepository.Page(List.of(), false));
        var provider = provider(learning, history);
        assertEquals("STALE_PATH_BASELINE", tool(provider, "assessment.read_legacy_history")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":91}")).errorCode());
        reset(learning);
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"));
        when(history.legacyAttempts(SCOPE, 91L, null, 10, null)).thenReturn(new AssessmentReadRepository.Page(
                List.of(new AssessmentReadRepository.LegacyAttempt(55, 91, 101L, 92L, 88, 1, 1,
                        "[]", Instant.now())), false));
        assertEquals("LEGACY_STEP_OWNERSHIP_MISMATCH", tool(provider, "assessment.read_legacy_history")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":91}")).errorCode());
    }

    @Test void multipleCurrentInvalidStatusAndMidReadChangeAreNotDraftReady() throws Exception {
        var learning = mock(LearningReadRepository.class);
        var provider = provider(learning, mock(AssessmentReadRepository.class));
        var two = new LearningReadRepository.Path(LearningReadRepository.PathState.AVAILABLE, 91L, 1L,
                "goal", "beginner", "active", "now", FP, List.of(
                new LearningReadRepository.Step(101, "one", 1, 0, "A", 10, "current", null, 99, "now"),
                new LearningReadRepository.Step(102, "two", 2, 1, "B", 10, "current", null, 99, "now")));
        when(learning.currentPath(SCOPE)).thenReturn(two);
        var result = tool(provider, "assessment.read_context").execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{}"));
        assertEquals("multiple_current_steps", data(result).path("state").asText());
        assertFalse(data(result).path("draftReady").asBoolean());
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "unexpected"));
        result = tool(provider, "assessment.read_context").execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{}"));
        assertEquals("invalid_step_status", data(result).path("state").asText());
        reset(learning);
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"), path("b".repeat(64), "completed"));
        when(learning.node(SCOPE, 1)).thenReturn(Optional.of(node()));
        assertEquals("STALE_PATH_BASELINE", tool(provider, "assessment.read_context")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{}")).errorCode());
    }

    @Test void malformedLegacyTotalsStayUnverifiedAndCannotCreateFormalWeakness() throws Exception {
        var learning = mock(LearningReadRepository.class);
        var history = mock(AssessmentReadRepository.class);
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"));
        when(history.legacyAttempts(SCOPE, 91, null, 10, null)).thenReturn(new AssessmentReadRepository.Page(
                List.of(new AssessmentReadRepository.LegacyAttempt(55, 91, null, null, 200, 5, 2,
                        "[\"client guess\"]", Instant.now())), false));
        var provider = provider(learning, history);
        var result = tool(provider, "assessment.read_legacy_history")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":91}"));
        assertEquals(ToolStatus.PARTIAL, result.status());
        assertEquals("legacy_unverified", data(result).path("records").get(0).path("validity").asText());
        assertEquals("INVALID_CLIENT_TOTALS", data(result).path("records").get(0).path("anomalies").get(0).asText());
        assertTrue(data(result).path("records").get(0).path("legacyClientWeakTopics").isArray());
        assertFalse(data(result).has("weaknessItems"));
        assertFalse(data(result).path("formalAvailable").asBoolean());
    }

    @Test void largeIdEmptyHistoryAcceptsIndependentEqualPathSnapshots() throws Exception {
        var first = largePath(1000, FP);
        var second = largePath(1000, FP);
        assertNotSame(first, second);
        assertNotSame(first.pathId(), second.pathId());
        var learning = mock(LearningReadRepository.class);
        var history = mock(AssessmentReadRepository.class);
        when(learning.currentPath(SCOPE)).thenReturn(first, second);
        when(history.legacyAttempts(SCOPE, 1000L, null, 10, null))
                .thenReturn(new AssessmentReadRepository.Page(List.of(), false));
        var result = tool(provider(learning, history), "assessment.read_legacy_history")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":1000}"));
        assertEquals(ToolStatus.OK, result.status(), result.errorCode());
        assertTrue(data(result).path("records").isEmpty());
        assertTrue(data(result).path("completeCoverage").asBoolean());
        verify(learning, times(2)).currentPath(SCOPE);
    }

    @Test void largeIdRealStepOwnershipAcceptsIndependentEqualNumericIds() throws Exception {
        var first = largePath(1000, FP);
        var second = largePath(1000, FP);
        Long actualOwner = Long.valueOf(1000L);
        assertNotSame(first.pathId(), second.pathId());
        assertNotSame(first.pathId(), actualOwner);
        var learning = mock(LearningReadRepository.class);
        var history = mock(AssessmentReadRepository.class);
        when(learning.currentPath(SCOPE)).thenReturn(first, second);
        when(history.legacyAttempts(SCOPE, 1000L, 1001L, 10, null))
                .thenReturn(new AssessmentReadRepository.Page(List.of(
                        new AssessmentReadRepository.LegacyAttempt(2000, 1000, 1001L, actualOwner,
                                80, 4, 5, "[]", Instant.parse("2026-09-26T00:00:00Z"))), false));
        var result = tool(provider(learning, history), "assessment.read_legacy_history")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":1000,\"stepId\":1001}"));
        assertEquals(ToolStatus.OK, result.status(), result.errorCode());
        assertEquals(1001, data(result).path("records").get(0).path("stepId").asInt());
        assertEquals("recorded_step", data(result).path("records").get(0).path("stageAttribution").asText());
        assertEquals("legacy_unverified", data(result).path("records").get(0).path("validity").asText());
    }

    @Test void largeIdStillRejectsDifferentPathFingerprintAndForeignOrMissingStepOwner() throws Exception {
        var learning = mock(LearningReadRepository.class);
        var history = mock(AssessmentReadRepository.class);
        var provider = provider(learning, history);
        when(history.legacyAttempts(SCOPE, 1000L, null, 10, null))
                .thenReturn(new AssessmentReadRepository.Page(List.of(), false));
        when(learning.currentPath(SCOPE)).thenReturn(largePath(1000, FP), largePath(1002, FP));
        assertEquals("STALE_PATH_BASELINE", tool(provider, "assessment.read_legacy_history")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":1000}")).errorCode());
        reset(learning);
        when(learning.currentPath(SCOPE)).thenReturn(largePath(1000, FP), largePath(1000, "b".repeat(64)));
        assertEquals("STALE_PATH_BASELINE", tool(provider, "assessment.read_legacy_history")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":1000}")).errorCode());
        reset(learning);
        when(learning.currentPath(SCOPE)).thenReturn(largePath(1000, FP));
        when(history.legacyAttempts(SCOPE, 1000L, null, 10, null)).thenReturn(new AssessmentReadRepository.Page(
                List.of(new AssessmentReadRepository.LegacyAttempt(2000, 1000, 1001L, 1002L,
                        80, 4, 5, "[]", Instant.now())), false));
        assertEquals("LEGACY_STEP_OWNERSHIP_MISMATCH", tool(provider, "assessment.read_legacy_history")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":1000}")).errorCode());
        when(history.legacyAttempts(SCOPE, 1000L, null, 10, null)).thenReturn(new AssessmentReadRepository.Page(
                List.of(new AssessmentReadRepository.LegacyAttempt(2000, 1000, 1001L, null,
                        80, 4, 5, "[]", Instant.now())), false));
        assertEquals("LEGACY_STEP_OWNERSHIP_MISMATCH", tool(provider, "assessment.read_legacy_history")
                .execute(ctx(Role.ASSESSMENT_ANALYSIS), parse("{\"pathId\":1000}")).errorCode());
    }
}
