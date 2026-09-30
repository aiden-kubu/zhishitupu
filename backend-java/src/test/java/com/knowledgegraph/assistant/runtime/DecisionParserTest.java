package com.knowledgegraph.assistant.runtime;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecisionParserTest {
    private final DecisionParser parser = new DecisionParser();

    @Test void parsesFiniteToolAndCompletionDecisions() {
        var tool = assertInstanceOf(DecisionParser.CallTool.class,
                parser.parse("{\"type\":\"call_tool\",\"name\":\"source.read\",\"arguments\":{\"id\":1}}"));
        assertEquals("source.read", tool.name());
        assertEquals(1, tool.arguments().get("id").asInt());
        var result = assertInstanceOf(DecisionParser.Finish.class,
                parser.parse("{\"type\":\"needs_input\",\"summary\":\"请选择资料\",\"data\":{}}"));
        assertEquals(Outcome.NEEDS_INPUT, result.outcome());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "```json\n{}\n```", "{}{}", "{\"type\":\"complete\",\"type\":\"needs_input\"}", "[]", "null",
            "{\"type\":\"execute\"}", "{\"type\":\"call_tool\",\"name\":\"read\",\"arguments\":[],\"extra\":true}",
            "{\"type\":\"complete\",\"outcome\":\"completed\",\"summary\":\"ok\",\"data\":{},\"executionReceipt\":true}",
            "{\"type\":\"needs_input\",\"outcome\":\"completed\",\"summary\":\"ok\",\"data\":{}}",
            "{\"type\":\"complete\",\"outcome\":\"completed\",\"summary\":\"ok\",\"data\":{},\"evidenceIds\":[\"x\",\"x\"]}"
    })
    void rejectsAmbiguousOrUnrecognizedProtocol(String raw) {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(raw));
    }

    @Test void proposalsCannotEnableExecution() {
        String raw = """
                {"type":"complete","outcome":"completed","summary":"草案","data":{},
                 "actionProposals":[{"actionType":"ingestion.process","targets":[{"kind":"document","id":"1"}],
                  "parameters":{},"impact":"后续流程可能自动入库","preconditions":["人工确认"]}]}
                """;
        var finish = (DecisionParser.Finish) parser.parse(raw);
        assertFalse(finish.proposals().get(0).executionAllowed());
        assertThrows(IllegalArgumentException.class, () -> parser.parse(raw.replace("\"parameters\":{}",
                "\"executionAllowed\":true,\"parameters\":{}")));
    }

    @Test void boundsPayloadAndNesting() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(" ".repeat(32001)));
        assertThrows(IllegalArgumentException.class, () -> parser.json("[".repeat(70) + "0" + "]".repeat(70), 1000));
    }

    @Test void wireArtifactSeparatesAuthorizationObservationAndExecution() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Artifact artifact = new Artifact("artifact", "task", Scope.project(), List.of(), Outcome.COMPLETED,
                "draft", "{}", List.of(), List.of(new ActionProposal("inspect", List.of(), "{}", "read", List.of())),
                List.of(), Instant.parse("2026-09-23T00:00:00Z"));
        var json = mapper.readTree(mapper.writeValueAsString(artifact));
        assertEquals("completed", json.get("outcome").asText());
        assertEquals("project", json.get("authorizedScope").get("mode").asText());
        assertTrue(json.get("observedScopes").isEmpty());
        assertFalse(json.has("effectiveScope"));
        assertFalse(json.get("scopeMatched").asBoolean());
        assertFalse(json.get("executionReceipt").asBoolean());
        assertFalse(json.get("actionProposals").get(0).get("executionAllowed").asBoolean());
    }

    @Test void toolFailureCannotCarryEvidenceOrContradictorySuccessStatus() {
        assertThrows(IllegalArgumentException.class, () -> new ToolResult(ToolStatus.OK, Scope.project(), "{}",
                List.of(), List.of(), "FAILED"));
        assertThrows(IllegalArgumentException.class, () -> new ToolResult(ToolStatus.ERROR, Scope.project(), "{}",
                List.of(), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new ToolResult(ToolStatus.ERROR, Scope.project(), "{}",
                List.of(new EvidenceRef("src", EvidenceKind.SOURCE, new ObjectRef("document", "1", null), Scope.project(), null, null)),
                List.of(), "FAILED"));
    }
}
