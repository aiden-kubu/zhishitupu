package com.knowledgegraph.assistant.host.demo;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgegraph.KnowledgeGraphApplication;
import com.knowledgegraph.assistant.host.*;
import com.knowledgegraph.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.sql.DriverManager;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Synthetic, in-memory website composition only. Does not start the business application or any JDBC server. */
class AssistantWebsiteReadConfigurationTest {
    static final ObjectMapper JSON=new ObjectMapper();
    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    @Conditional(AssistantWebsiteReadConfiguration.OrdinaryWebsite.class)
    static class MvcOnly {
        @Bean GlobalExceptionHandler commonErrors(){return new GlobalExceptionHandler();}
    }
    WebApplicationContextRunner website() {
        return new WebApplicationContextRunner().withUserConfiguration(MvcOnly.class,AssistantWebsiteReadConfiguration.class)
                .withInitializer(c->c.getBeanFactory().registerSingleton("syntheticOrdinaryWebsiteMarker",mock(KnowledgeGraphApplication.class)));
    }
    MockMvc mvc(org.springframework.boot.test.context.assertj.AssertableWebApplicationContext c) {
        var builder=MockMvcBuilders.webAppContextSetup(c.getSourceApplicationContext());
        var registration=c.getBean("websiteAssistantReadBoundary",org.springframework.boot.web.servlet.FilterRegistrationBean.class);
        builder.addFilter(registration.getFilter(),(String[])registration.getUrlPatterns().toArray(String[]::new));return builder.build();
    }
    JsonNode response(MockMvc mvc,org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,int status)throws Exception {
        var result=mvc.perform(request).andReturn();assertEquals(status,result.getResponse().getStatus());
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }
    static final class SyntheticBundle {
        final Path file;
        final ObjectNode config;
        String hash;
        SyntheticBundle(String account)throws Exception {
            Files.createDirectories(Path.of("target"));Path dir=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"website-synthetic-");file=dir.resolve("config.json");
            ObjectNode instance=JSON.createObjectNode().put("version","8.4.11").put("uuid","8c6cfcb3-ba49-11f1-bf64-005056c00001")
                    .put("address","127.0.0.1").put("port",33078).put("database","kg_assistant_i2_20260927_r01");
            ObjectNode grant=JSON.createObjectNode().put("mode","selected");grant.putArray("libraryIds").add("1");grant.putArray("documentIds");grant.putArray("nodeIds");
            String launch=UUID.randomUUID().toString();Instant now=Instant.now();
            ObjectNode proof=JSON.createObjectNode().put("schemaVersion","08-read-evidence-v1").put("launchId",launch).put("readerAccount",account);
            proof.set("instance",instance);proof.set("grant",grant);proof.putArray("selectTables").add("knowledge_libraries").add("documents");
            proof.putObject("verification").put("executor","06").put("result","PASS").put("identityMatch",true).put("selectOnly",true).put("noOtherPrivileges",true)
                    .put("verifiedAt",now.minusSeconds(10).toString()).put("validUntil",now.plusSeconds(300).toString());
            proof.putObject("review").put("reviewer","controller").put("result","APPROVED").put("reviewedAt",now.minusSeconds(5).toString()).put("reference","SYNTHETIC-NO-REAL-GRANTS");
            var raw=proof.putObject("rawEvidence");
            for(String name:List.of("instance","grants")) {
                byte[] bytes=("SYNTHETIC_OFFLINE_ONLY "+name+"; not a real observation").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                writePrivate(dir.resolve(name+".txt"),bytes);raw.putObject(name).put("file",name+".txt").put("sha256",sha(bytes));
            }
            byte[] evidence=JSON.writeValueAsBytes(proof);writePrivate(dir.resolve("proof.json"),evidence);
            config=JSON.createObjectNode().put("schemaVersion","08-read-acceptance-v1").put("enabled",true).put("launchId",launch).put("owner","synthetic-demo-owner")
                    .put("listenAddress","127.0.0.1").put("listenPort",18088);
            config.set("instance",instance);config.set("grant",grant);config.set("selectTables",proof.path("selectTables"));config.putNull("frontend");
            config.putObject("reader").put("account",account).put("password","SYNTHETIC_NOT_REAL_PASSWORD");
            config.putObject("evidence").put("file","proof.json").put("sha256",sha(evidence));save();
        }
        void save()throws Exception {byte[] bytes=JSON.writeValueAsBytes(config);writePrivate(file,bytes);hash=sha(bytes);}
        String[] properties(){return new String[]{AssistantWebsiteReadConfiguration.CONFIG+"="+file,AssistantWebsiteReadConfiguration.CONFIG_SHA+"="+hash};}
        static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
        static void writePrivate(Path path,byte[] bytes)throws Exception {
            Files.write(path,bytes);var acl=Files.getFileAttributeView(path,AclFileAttributeView.class);
            if(acl!=null)acl.setAcl(List.of(AclEntry.newBuilder().setPrincipal(acl.getOwner()).setType(AclEntryType.ALLOW).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            else Files.setPosixFilePermissions(path,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
        }
    }
    @Test void ordinaryWebsiteExplicitlyRegistersAllTenRoutes() {
        website().run(c->{assertNull(c.getStartupFailure());var mapping=c.getBean(RequestMappingHandlerMapping.class);
            assertEquals(10,mapping.getHandlerMethods().values().stream().filter(m->m.getBeanType()==AssistantReadHostController.class).count());
            assertEquals(Set.of("/api/assistant/*"),Set.copyOf(c.getBean("websiteAssistantReadBoundary",org.springframework.boot.web.servlet.FilterRegistrationBean.class).getUrlPatterns()));});
    }
    @Test void missingConfigurationReportsUnavailableInsteadOf404() {
        website().run(c->{MockMvc mvc=mvc(c);
            var cap=response(mvc,get("/api/assistant/capabilities").with(r->{r.setServerName("tupu.gmule.cn");r.setRemoteAddr("203.0.113.7");return r;}),200);
            assertEquals(0,cap.path("code").asInt());assertFalse(cap.at("/data/databaseIdentityVerified").asBoolean());
            assertEquals("disabled",cap.at("/data/capabilities/0/status").asText());assertEquals("ASSISTANT_DISABLED",cap.at("/data/capabilities/0/reasonCodes/0").asText());
            assertEquals("unavailable",cap.at("/data/capabilities/7/status").asText());
            assertEquals("ASSISTANT_DISABLED",response(mvc,get("/api/assistant/scope/libraries"),503).path("code").asText());});
    }
    @Test void syntheticReviewedReaderComposesWithoutLegacyDatasourceOrAnyConnection()throws Exception {
        SyntheticBundle b=new SyntheticBundle("assistant_reader");DataSource legacy=mock(DataSource.class);
        try(var driver=mockStatic(DriverManager.class)) {
            website().withPropertyValues(b.properties()).withBean(DataSource.class,()->legacy).run(c->{
                var cap=response(mvc(c),get("/api/assistant/capabilities"),200);
                assertTrue(cap.at("/data/databaseIdentityVerified").asBoolean());assertEquals("deterministic_tools",cap.at("/data/executionMode").asText());
                assertEquals("available",cap.at("/data/capabilities/0/status").asText());assertEquals("unavailable",cap.at("/data/capabilities/7/status").asText());
                assertEquals("memory",cap.at("/data/persistence/mode").asText());assertEquals(1,c.getBeansOfType(DataSource.class).size());verifyNoInteractions(legacy);});
            driver.verifyNoInteractions();
        }
    }
    @Test void existingDocumentAndRunRoutesKeepDtoAndBodyHandling()throws Exception {
        AssistantReadHostService testOnly=mock(AssistantReadHostService.class);
        ObjectNode documents=JSON.createObjectNode();documents.putArray("items").addObject().put("documentId","7").put("title","SYNTHETIC ONLY");documents.put("total","1");
        ObjectNode run=JSON.createObjectNode().put("runId","synthetic-run").put("status","running");
        when(testOnly.documents(Set.of(1L),1,20)).thenReturn(documents);when(testOnly.create("{}")).thenReturn(run);when(testOnly.run("synthetic-run")).thenReturn(run);
        website().withBean(AssistantReadHostService.class,()->testOnly).run(c->{MockMvc mvc=mvc(c);
            assertEquals(documents,response(mvc,get("/api/assistant/scope/documents").param("libraryIds","1"),200).path("data"));
            assertEquals(run,response(mvc,post("/api/assistant/runs").contentType("application/json").content("{}"),202).path("data"));
            assertEquals(run,response(mvc,get("/api/assistant/runs/synthetic-run"),200).path("data"));
            verify(testOnly).create("{}");verify(testOnly).documents(Set.of(1L),1,20);});
    }
    @Test void incompleteConfigurationAndHttpSelfAuthorizationNeverBecomeReady() {
        website().withPropertyValues(AssistantWebsiteReadConfiguration.CONFIG+"=target/unreviewed.json","assistant.website.enabled=true").run(c->{MockMvc mvc=mvc(c);
            var cap=response(mvc,get("/api/assistant/capabilities"),200);assertFalse(cap.at("/data/databaseIdentityVerified").asBoolean());
            assertEquals("unavailable",cap.at("/data/capabilities/0/status").asText());assertEquals("READ_CONTEXT_OR_IDENTITY_UNAVAILABLE",cap.at("/data/capabilities/0/reasonCodes/0").asText());
            assertEquals("INVALID_ARGUMENT",response(mvc,get("/api/assistant/capabilities").param("verified","true"),400).path("code").asText());
            assertEquals("INVALID_ARGUMENT",response(mvc,get("/api/assistant/capabilities").content("{}"),400).path("code").asText());});
    }
    @Test void selectedScopeAndRemoteExecutionRemainClosed()throws Exception {
        SyntheticBundle b=new SyntheticBundle("assistant_reader");
        try(var driver=mockStatic(DriverManager.class)) {
            website().withPropertyValues(b.properties()).run(c->{MockMvc mvc=mvc(c);
                assertEquals("SCOPE_NOT_ALLOWED",response(mvc,get("/api/assistant/scope/documents").param("libraryIds","999"),403).path("code").asText());
                assertEquals("ACCESS_CONTEXT_UNAVAILABLE",response(mvc,get("/api/assistant/scope/libraries").with(r->{r.setServerName("tupu.gmule.cn");r.setRemoteAddr("203.0.113.7");return r;}),401).path("code").asText());
                assertEquals("ACCESS_CONTEXT_UNAVAILABLE",response(mvc,get("/api/assistant/capabilities").header("Forwarded","for=127.0.0.1"),401).path("code").asText());});
            driver.verifyNoInteractions();
        }
    }
    @Test void writerIdentityInServerConfigurationIsRejectedWithoutCredentialsLeak()throws Exception {
        SyntheticBundle b=new SyntheticBundle("root_writer");
        try(var driver=mockStatic(DriverManager.class)) {
            website().withPropertyValues(b.properties()).run(c->{var cap=response(mvc(c),get("/api/assistant/capabilities"),200);
                assertFalse(cap.at("/data/databaseIdentityVerified").asBoolean());assertEquals("unavailable",cap.at("/data/capabilities/0/status").asText());
                assertFalse(cap.toString().contains("SYNTHETIC_NOT_REAL_PASSWORD"));assertFalse(cap.toString().contains("root_writer"));});
            driver.verifyNoInteractions();
        }
    }
    @Test void oldStandaloneScanAndDisabledLauncherStayIsolated() {
        var scanner=new ClassPathScanningCandidateComponentProvider(true);assertTrue(scanner.findCandidateComponents("com.knowledgegraph.assistant.host").isEmpty());
        try(var registry=new org.springframework.context.support.GenericApplicationContext()) {
            // Inspect an ordinary root definition without refreshing/starting the business application.
            registry.registerBeanDefinition("knowledgeGraphApplication",new org.springframework.beans.factory.support.RootBeanDefinition(KnowledgeGraphApplication.class));
            var ordinaryScan=new ClassPathBeanDefinitionScanner(registry);ordinaryScan.setResourceLoader(registry);
            assertTrue(ordinaryScan.findCandidateComponents("com.knowledgegraph.assistant.host.demo").stream()
                    .anyMatch(bean->AssistantWebsiteReadConfiguration.class.getName().equals(bean.getBeanClassName())));
        }
        new WebApplicationContextRunner().withUserConfiguration(MvcOnly.class,AssistantWebsiteReadConfiguration.class).run(c->{
            assertNull(c.getStartupFailure());assertTrue(c.getBeansOfType(AssistantReadHostService.class).isEmpty());assertTrue(c.getBeansOfType(AssistantReadHostController.class).isEmpty());});
        assertThrows(IllegalArgumentException.class,()->AssistantReadHostApplication.main(new String[]{"--enable=true"}));
        try(var closed=AssistantReadHostService.disabled()){assertFalse(closed.capabilities().path("databaseIdentityVerified").asBoolean());}
    }
}
