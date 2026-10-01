package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import static com.knowledgegraph.assistant.host.HostTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Original 06: MockMvc composition evidence only. No socket, JDBC or model. */
class AssistantWebsiteIntegrationTest {
    final ArrayNode samples = HostJson.JSON.createArrayNode();

    JsonNode capture(MockMvc mvc, String name, MockHttpServletRequestBuilder request,
                     int expectedStatus, String schema) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = HostJson.JSON.readTree(body);
        if (schema != null) new ApprovedSchema().check(schema, json);
        ObjectNode sample = samples.addObject();
        sample.put("name", name);
        sample.put("method", result.getRequest().getMethod());
        sample.put("path", result.getRequest().getRequestURI());
        sample.put("queryString", result.getRequest().getQueryString());
        byte[] requestBytes = result.getRequest().getContentAsByteArray();
        sample.put("requestBody", requestBytes == null ? null : new String(requestBytes, StandardCharsets.UTF_8));
        sample.set("requestParameters", HostJson.JSON.valueToTree(result.getRequest().getParameterMap()));
        sample.put("status", result.getResponse().getStatus());
        sample.put("responseBody", body);
        sample.put("contentType", result.getResponse().getContentType());
        sample.set("response", json);
        return json;
    }

    @Test void exportsActualHttpCompositionForOfflineWebsite() throws Exception {
        Path directory = Path.of(System.getProperty("website.fixtureDir"));
        assertFalse(Files.exists(directory.resolve("samples.json")), "Do not overwrite prior samples");
        try (HostTestFixture disabled = new HostTestFixture(
                new AssistantReadHostProperties(false, false, false, 8, 32,
                        Duration.ofSeconds(1800), 2L * 1024 * 1024))) {
            JsonNode caps = capture(disabled.mvc, "disabled-capabilities",
                    get("/api/assistant/capabilities"), 200, "CapabilitiesEnvelope");
            assertTrue(caps.at("/data/capabilities").findValuesAsText("status")
                    .stream().noneMatch("available"::equals));
            capture(disabled.mvc, "disabled-templates",
                    get("/api/assistant/templates"), 200, "TemplateListEnvelope");
            capture(disabled.mvc, "disabled-create",
                    post("/api/assistant/runs").contentType(MediaType.APPLICATION_JSON).content(create(A)),
                    503, "Error");
        }
        try (HostTestFixture f = new HostTestFixture()) {
            capture(f.mvc, "capabilities", get("/api/assistant/capabilities"), 200, "CapabilitiesEnvelope");
            capture(f.mvc, "templates", get("/api/assistant/templates"), 200, "TemplateListEnvelope");
            JsonNode libs = capture(f.mvc, "libraries", get("/api/assistant/scope/libraries")
                    .param("page", "1").param("pageSize", "20"), 200, "LibraryPageEnvelope");
            JsonNode docs = capture(f.mvc, "documents", get("/api/assistant/scope/documents")
                    .param("libraryIds", Long.toString(LIB)).param("page", "1").param("pageSize", "20"),
                    200, "DocumentPageEnvelope");
            assertTrue(libs.at("/data/items/0/libraryId").isTextual());
            assertEquals(Long.toString(A), docs.at("/data/items/0/documentId").asText());
            JsonNode created = capture(f.mvc, "create", post("/api/assistant/runs")
                    .contentType(MediaType.APPLICATION_JSON).content(create(A)), 202, "RunEnvelope");
            String runId = created.at("/data/runId").asText();
            var entry = f.host.store.get(runId);
            entry.handle.completion().toCompletableFuture().get(10, TimeUnit.SECONDS);
            JsonNode run = capture(f.mvc, "run", get("/api/assistant/runs/{r}", runId), 200, "RunEnvelope");
            assertEquals("partial", run.at("/data/status").asText());
            assertEquals(4, run.at("/data/tasks").size());
            assertEquals(0, run.at("/data/usage/modelCalls").asInt());
            for (int i = 0; i < entry.snapshot().tasks().size(); i++) {
                var artifact = entry.snapshot().tasks().get(i).artifact();
                assertNotNull(artifact);
                JsonNode actual = capture(f.mvc, "artifact-" + i,
                        get("/api/assistant/runs/{r}/artifacts/{a}", runId, artifact.artifactId()),
                        200, "ArtifactEnvelope");
                assertEquals(runId, actual.at("/data/runId").asText());
                if (i == 3) {
                    assertEquals("CURRENT", actual.at("/data/payload/checks/0/referenceStatus").asText());
                    assertEquals("not_evaluated", actual.at("/data/payload/semanticSupport").asText());
                }
            }
            var sourceArtifact = entry.snapshot().tasks().get(1).artifact();
            var researchArtifact = entry.snapshot().tasks().get(2).artifact();
            JsonNode graph = capture(f.mvc, "graph", get("/api/assistant/runs/{r}/artifacts/{a}/graph",
                    runId, researchArtifact.artifactId()), 200, "GraphEnvelope");
            assertEquals(Long.toString(N1), graph.at("/data/edges/0/sourceNodeId").asText());
            assertEquals(Long.toString(N2), graph.at("/data/edges/0/targetNodeId").asText());
            assertTrue(graph.at("/data/nodes/0/nodeId").isTextual());
            var pointer = sourceArtifact.evidence().stream()
                    .filter(e -> e.kind() == com.knowledgegraph.assistant.contract.AssistantContracts.EvidenceKind.SOURCE)
                    .findFirst().orElseThrow();
            String requestBody = HostJson.obj("sourceId", pointer.sourceId(),
                    "expectedContentHash", HASH, "maxChars", 4000).toString();
            JsonNode reread = capture(f.mvc, "reread",
                    post("/api/assistant/runs/{r}/artifacts/{a}/source-reread",
                            runId, sourceArtifact.artifactId())
                            .contentType(MediaType.APPLICATION_JSON).content(requestBody), 200, "RereadEnvelope");
            assertEquals(Long.toString(A), reread.at("/data/documentId").asText());
            assertEquals(Long.toString(A + 100), reread.at("/data/chunkId").asText());
            assertEquals(HASH, reread.at("/data/contentHash").asText());
            // Configure only the existing test fixture mock, preserving production providers/runtime.
            when(f.research.sourceMeta(any(), eq(A), eq(A + 100))).thenReturn(Optional.of(
                    new ResearchReadRepository.SourceMeta(A, A + 100, A + 200, 1, "text", "unit:1",
                            "b".repeat(64), "COMPLETED", "active", "READY")));
            JsonNode stale = capture(f.mvc, "reread-stale",
                    post("/api/assistant/runs/{r}/artifacts/{a}/source-reread",
                            runId, sourceArtifact.artifactId())
                            .contentType(MediaType.APPLICATION_JSON).content(requestBody), 409, "Error");
            assertFalse(stale.has("data"));
            assertFalse(stale.toString().contains("graph source quotation"));
            capture(f.mvc, "out-of-scope", get("/api/assistant/scope/documents")
                    .param("libraryIds", "7"), 403, "Error");
            ObjectNode metadata = HostJson.JSON.createObjectNode();
            metadata.put("runId", runId);
            metadata.put("sourceArtifactId", sourceArtifact.artifactId());
            metadata.put("researchArtifactId", researchArtifact.artifactId());
            metadata.put("citationArtifactId", entry.snapshot().tasks().get(3).artifact().artifactId());
            metadata.put("libraryId", Long.toString(LIB));
            metadata.put("documentId", Long.toString(A));
            metadata.put("sourceId", pointer.sourceId());
            metadata.put("chunkId", Long.toString(A + 100));
            metadata.put("contentHash", HASH);
            ObjectNode output = HostJson.JSON.createObjectNode();
            output.put("evidenceKind", "actual-main-project-MockMvc-output-test-only");
            output.put("sourceAuthor", "06");
            output.put("databaseOrModelConnections", 0);
            output.set("metadata", metadata);
            output.set("samples", samples);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("samples.json"),
                    HostJson.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(output) + "\n",
                    StandardCharsets.UTF_8);
            assertEquals(17, samples.size());
        }
    }
}
