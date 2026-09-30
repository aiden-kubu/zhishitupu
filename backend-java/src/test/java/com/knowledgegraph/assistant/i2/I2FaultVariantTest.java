package com.knowledgegraph.assistant.i2;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="KG_I2_AUTHORIZED",matches="8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2FaultVariantTest extends I2MySqlSupport {
    @Test void selectedDatabaseVariant() throws Exception {
        switch(System.getenv("KG_I2_CASE")) {
            case "HASH_CHANGED" -> {
                assertNotEquals(hash(7401),source.chunk(A,7201,7401,4000).orElseThrow().contentHash());
                assertEquals("STALE_SOURCE",researchTool("read_source",A,"{\"documentId\":7201,\"chunkId\":7401,\"expectedContentHash\":\""+hash(7401)+"\"}").errorCode());
            }
            case "ARCHIVE_A" -> {
                assertTrue(research.candidates(A,7201,List.of("节点"),10).items().isEmpty());
                assertEquals(List.of(7202L),research.matchingDocuments(AB,List.of("验收连接"),10).items());
                assertEquals("SOURCE_UNAVAILABLE",researchTool("read_source",A,"{\"documentId\":7201,\"chunkId\":7401,\"expectedContentHash\":\""+hash(7401)+"\"}").errorCode());
            }
            case "LONG_A" -> {
                var r=researchTool("retrieve_chunks",AB,"{\"query\":\"验收连接\",\"maxSources\":2,\"maxChars\":200}");
                assertEquals(ToolStatus.PARTIAL,r.status());
                var data=JSON.readTree(r.dataJson()); Set<Long> ids=new HashSet<>();data.path("items").forEach(i->ids.add(i.path("documentId").asLong()));
                assertEquals(Set.of(7201L,7202L),ids); assertTrue(data.path("usedChars").asInt()<=200);
                var limited=researchTool("retrieve_chunks",AB,"{\"query\":\"验收连接\",\"maxSources\":1,\"maxChars\":200}");
                assertEquals(ToolStatus.PARTIAL,limited.status()); assertFalse(JSON.readTree(limited.dataJson()).path("uncoveredRequestedDocumentIds").isEmpty());
            }
            case "MULTI_CURRENT" -> {
                var data=JSON.readTree(assessmentTool("read_context","{}").dataJson());
                assertEquals("multiple_current_steps",data.path("state").asText()); assertFalse(data.path("draftReady").asBoolean());
            }
            case "INACTIVE_CURRENT" -> {
                var data=JSON.readTree(assessmentTool("read_context","{}").dataJson());
                assertEquals("current_node_unavailable",data.path("state").asText()); assertFalse(data.path("draftReady").asBoolean());
            }
            case "NO_PATH" -> {
                var data=JSON.readTree(assessmentTool("read_context","{}").dataJson());
                assertEquals("none",data.path("state").asText()); assertFalse(data.path("draftReady").asBoolean());
            }
            case "ACCEPTED_STATE" -> {
                assertEquals("ACCEPTED",quality.candidate(A,7501,"entity",7601).orElseThrow().reviewStatus());
                assertEquals(5,ingestion.documents(LIB,null,1,10).total());
            }
            default -> fail("Unapproved variant name");
        }
    }
}
