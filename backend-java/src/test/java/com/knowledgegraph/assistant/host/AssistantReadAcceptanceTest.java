package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpServletRequest;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.*;
import java.util.*;
import static com.knowledgegraph.assistant.host.AssistantReadAcceptanceConfig.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssistantReadAcceptanceTest {
    /** All files are generated under this worktree's target. This is NOT a GRANTS verification result. */
    static final class Bundle {
        static final String SYNTHETIC_PASSWORD="SYNTHETIC_ONLY_NOT_A_REAL_CREDENTIAL";
        final Path root,configFile,build;
        final Instant now=Instant.now();
        final ObjectNode config,evidence;
        String configSha;
        Bundle()throws Exception {
            Files.createDirectories(Path.of("target"));root=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"acceptance-synthetic-");
            configFile=root.resolve("config.json");build=root.resolve("build");Files.createDirectory(build);
            ObjectNode identity=JSON.createObjectNode().put("version",VERSION).put("uuid",UUID).put("address",ADDRESS).put("port",DATABASE_PORT).put("database",DATABASE);
            ObjectNode grant=JSON.createObjectNode().put("mode","selected");grant.putArray("libraryIds").add("1");grant.putArray("documentIds");grant.putArray("nodeIds");
            config=JSON.createObjectNode().put("schemaVersion","08-read-acceptance-v1").put("enabled",true)
                    .put("launchId",java.util.UUID.randomUUID().toString()).put("owner","local-acceptance-owner").put("listenAddress",ADDRESS).put("listenPort",18088);
            config.set("instance",identity.deepCopy());config.putObject("reader").put("account","assistant_reader").put("password",SYNTHETIC_PASSWORD);
            config.set("grant",grant.deepCopy());config.putArray("selectTables").add("knowledge_libraries").add("documents");config.putNull("frontend");
            evidence=JSON.createObjectNode().put("schemaVersion","08-read-evidence-v1").put("launchId",config.path("launchId").asText());
            evidence.set("instance",identity.deepCopy());evidence.put("readerAccount","assistant_reader");evidence.set("grant",grant.deepCopy());evidence.set("selectTables",config.path("selectTables").deepCopy());
            evidence.putObject("verification").put("executor","06").put("result","PASS").put("identityMatch",true).put("selectOnly",true).put("noOtherPrivileges",true)
                    .put("verifiedAt",now.minusSeconds(10).toString()).put("validUntil",now.plusSeconds(300).toString());
            evidence.putObject("review").put("reviewer","controller").put("result","APPROVED").put("reviewedAt",now.minusSeconds(5).toString())
                    .put("reference","SYNTHETIC-OFFLINE-NO-LIVE-AUTHORITY");
            ObjectNode raw=evidence.putObject("rawEvidence");
            for(String kind:List.of("instance","grants")) {
                byte[] bytes=("SYNTHETIC "+kind+" fixture; no real database observation\n").getBytes(StandardCharsets.UTF_8);
                privateWrite(root.resolve(kind+".txt"),bytes);raw.putObject(kind).put("file",kind+".txt").put("sha256",sha256(bytes));
            }
            save();
        }
        static void privateWrite(Path p,byte[] bytes)throws Exception {
            Files.write(p,bytes);
            var acl=Files.getFileAttributeView(p,AclFileAttributeView.class);
            if(acl!=null)acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            else Files.setPosixFilePermissions(p,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
        }
        void save()throws Exception {
            byte[] proof=JSON.writeValueAsBytes(evidence);privateWrite(root.resolve("evidence.json"),proof);
            config.putObject("evidence").put("file","evidence.json").put("sha256",sha256(proof));
            byte[] bytes=JSON.writeValueAsBytes(config);privateWrite(configFile,bytes);configSha=sha256(bytes);
        }
        AssistantReadAcceptanceConfig config()throws Exception {save();return AssistantReadAcceptanceConfig.load(configFile,configSha);}
        AssistantReadAcceptanceEvidence proof(AssistantReadAcceptanceConfig c){return AssistantReadAcceptanceEvidence.load(c,Clock.systemUTC());}
        String[] args(){return new String[]{"--reviewed-config="+configFile,"--controller-approved-config-sha256="+configSha};}
        AssistantReadAcceptanceApplication.StaticSite site()throws Exception {
            return site(build,build);
        }
        AssistantReadAcceptanceApplication.StaticSite site(Path directory,Path boundary)throws Exception {
            Files.createDirectories(directory);
            byte[] index=("<html>synthetic offline build "+directory.getFileName()+"</html>").getBytes(StandardCharsets.UTF_8),js="/* synthetic offline asset */".getBytes(StandardCharsets.UTF_8);
            Files.write(directory.resolve("index.html"),index);Files.createDirectories(directory.resolve("assets"));Files.write(directory.resolve("assets/app.js"),js);
            ObjectNode manifest=JSON.createObjectNode().put("schemaVersion","08-static-build-v1");ObjectNode files=manifest.putObject("files");
            files.putObject("index.html").put("sha256",sha256(index)).put("bytes",index.length);
            files.putObject("assets/app.js").put("sha256",sha256(js)).put("bytes",js.length);
            byte[] bytes=JSON.writeValueAsBytes(manifest);privateWrite(root.resolve("build-manifest.json"),bytes);
            config.putObject("frontend").put("directory",directory.toString()).put("manifestFile","build-manifest.json").put("manifestSha256",sha256(bytes));
            return AssistantReadAcceptanceApplication.staticSite(config(),boundary);
        }
        static void redirect(Path link,Path destination)throws Exception {
            Path testOutputs=Path.of("target").toAbsolutePath();
            assertTrue(link.toAbsolutePath().startsWith(testOutputs));assertTrue(destination.toRealPath().startsWith(testOutputs));
            if(System.getProperty("os.name").startsWith("Windows")) {
                // Junction fixture only, under our target; /d excludes cmd AutoRun and no service is started.
                Process process=new ProcessBuilder("cmd.exe","/d","/c","mklink","/J",link.toString(),destination.toString())
                        .directory(Path.of("").toAbsolutePath().toFile()).redirectErrorStream(true).start();
                assertTrue(process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue());
            }else Files.createSymbolicLink(link,destination);
        }
    }

    @Test void missingConfigurationCannotCreateAnEnabledHostOrListener() {
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.main(new String[0]));
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.configuration(new String[]{"--spring.profiles.active=reader"}));
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceConfig.load(Path.of("target/absent.json").toAbsolutePath(),"a".repeat(64)));
    }
    @ParameterizedTest @ValueSource(strings={"version","uuid","address","port","database","root","writer","admin","listenAddress","listenPort"})
    void wrongInstanceAccountOrListenerRejected(String kind)throws Exception {
        Bundle b=new Bundle();ObjectNode instance=(ObjectNode)b.config.path("instance"),reader=(ObjectNode)b.config.path("reader");
        switch(kind) {
            case "version"->instance.put(kind,"8.4.12");case "uuid"->instance.put(kind,java.util.UUID.randomUUID().toString());
            case "address"->instance.put(kind,"db.example.test");case "port"->instance.put(kind,3306);case "database"->instance.put(kind,"production");
            case "root","writer","admin"->reader.put("account",kind+"_reader");
            case "listenAddress"->b.config.put(kind,"0.0.0.0");case "listenPort"->b.config.put(kind,0);
            default->fail();
        }
        assertThrows(IllegalArgumentException.class,b::config);
    }
    @ParameterizedTest @ValueSource(strings={"configHash","evidenceHash","rawHash","rawMissing","expired","future","scope","tables","identity","account","launchId","verification","review"})
    void brokenEvidenceChainOrFieldsFailClosed(String kind)throws Exception {
        Bundle b=new Bundle();
        switch(kind) {
            case "configHash"->{assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceConfig.load(b.configFile,"a".repeat(64)));return;}
            case "evidenceHash"->{var c=b.config();Bundle.privateWrite(b.root.resolve("evidence.json"),"{}".getBytes(StandardCharsets.UTF_8));assertThrows(IllegalArgumentException.class,()->b.proof(c));return;}
            case "rawHash"->Bundle.privateWrite(b.root.resolve("grants.txt"),"SYNTHETIC changed raw proof".getBytes(StandardCharsets.UTF_8));
            case "rawMissing"->Files.delete(b.root.resolve("instance.txt"));
            case "expired"->((ObjectNode)b.evidence.path("verification")).put("validUntil",b.now.minusSeconds(1).toString());
            case "future"->((ObjectNode)b.evidence.path("verification")).put("verifiedAt",b.now.plusSeconds(30).toString());
            case "scope"->((ObjectNode)b.evidence.path("grant")).putArray("libraryIds").add("2");
            case "tables"->b.evidence.putArray("selectTables").add("documents");
            case "identity"->((ObjectNode)b.evidence.path("instance")).put("uuid",java.util.UUID.randomUUID().toString());
            case "account"->b.evidence.put("readerAccount","another_reader");
            case "launchId"->b.evidence.put("launchId",java.util.UUID.randomUUID().toString());
            case "verification"->((ObjectNode)b.evidence.path("verification")).put("selectOnly",false);
            case "review"->((ObjectNode)b.evidence.path("review")).put("result","PENDING");
            default->fail();
        }
        var c=b.config();assertThrows(IllegalArgumentException.class,()->b.proof(c));
    }
    @ParameterizedTest @ValueSource(strings={"disabled","project","nodes","tooMany","duplicate","unknownField","systemTable","initSql","multiQuery"})
    void unsupportedConfigurationDoesNotExpandAuthority(String kind)throws Exception {
        Bundle b=new Bundle();ObjectNode grant=(ObjectNode)b.config.path("grant");
        switch(kind) {
            case "disabled"->b.config.put("enabled",false);case "project"->grant.put("mode","project");case "nodes"->grant.putArray("nodeIds").add("1");
            case "tooMany"->{var ids=grant.putArray("libraryIds");for(int i=1;i<=1001;i++)ids.add(Integer.toString(i));}
            case "duplicate"->grant.putArray("libraryIds").add("1").add("1");
            case "unknownField"->b.config.put("requestOwner","untrusted");case "systemTable"->b.config.putArray("selectTables").add("system_settings");
            case "initSql"->((ObjectNode)b.config.path("reader")).put("initSql","SELECT 1");
            case "multiQuery"->((ObjectNode)b.config.path("reader")).put("allowMultiQueries",true);default->fail();
        }
        assertThrows(IllegalArgumentException.class,b::config);
    }
    @Test void exactlyOneThousandSelectedIdsArePreserved()throws Exception {
        Bundle b=new Bundle();var ids=((ObjectNode)b.config.path("grant")).putArray("libraryIds");for(int i=1;i<=1000;i++)ids.add(Integer.toString(i));
        b.evidence.set("grant",b.config.path("grant").deepCopy());var c=b.config();assertEquals(1000,c.grant().libraryIds().size());assertNotNull(b.proof(c));
    }
    @Test void strictJsonAndSecretFailuresAreRedacted()throws Exception {
        Bundle b=new Bundle();String json=Files.readString(b.configFile);
        for(String malformed:List.of(json+" {}",json.replace("\"enabled\":true","\"enabled\":true,\"enabled\":false"))) {
            byte[] bytes=malformed.getBytes(StandardCharsets.UTF_8);Bundle.privateWrite(b.configFile,bytes);
            var error=assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceConfig.load(b.configFile,sha256(bytes)));
            assertNull(error.getCause());assertFalse(error.toString().contains(Bundle.SYNTHETIC_PASSWORD));
        }
        var c=b.config();assertFalse(c.toString().contains(Bundle.SYNTHETIC_PASSWORD));
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.configuration(new String[]{b.args()[0],b.args()[1],"--owner=attacker"}));
    }
    @Test void broadFilePermissionsAreNotARestrictedEvidenceSource()throws Exception {
        Bundle b=new Bundle();var acl=Files.getFileAttributeView(b.configFile,AclFileAttributeView.class);
        if(acl!=null) {
            var everyone=b.configFile.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone");
            var entries=new ArrayList<>(acl.getAcl());entries.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone).setPermissions(AclEntryPermission.READ_DATA).build());acl.setAcl(entries);
        }else Files.setPosixFilePermissions(b.configFile,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OTHERS_READ));
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceConfig.load(b.configFile,b.configSha));
    }
    @Test void acceptanceCompositionHasTenReadToolsNoConnectionAndNoLegacyBeans()throws Exception {
        Bundle b=new Bundle();var c=b.config();var proof=b.proof(c);var connector=mock(AssistantReadAcceptanceDataSource.Connector.class);
        var ds=new AssistantReadAcceptanceDataSource(c,proof,connector,Clock.systemUTC());
        try(var host=AssistantReadAcceptanceApplication.host(c,proof,ds)) {
            var field=AssistantReadHostService.class.getDeclaredField("bindings");field.setAccessible(true);var bindings=(AssistantReadWiring.Bindings)field.get(host);
            assertEquals(10,bindings.tools().size());assertTrue(bindings.tools().stream().allMatch(t->t.descriptor().effect()==ToolEffect.READ_ONLY));
            assertTrue(bindings.tools().stream().noneMatch(t->t.descriptor().roles().contains(Role.LEARNING_COACH)||t.descriptor().roles().contains(Role.ASSESSMENT_ANALYSIS)));
            assertEquals("deterministic_tools",host.capabilities().path("executionMode").asText());assertTrue(host.capabilities().path("databaseIdentityVerified").asBoolean());
            assertEquals("memory",host.capabilities().at("/persistence/mode").asText());
            new ApplicationContextRunner().withUserConfiguration(AssistantReadAcceptanceApplication.class)
                    .withInitializer(context->AssistantReadAcceptanceApplication.install((GenericApplicationContext)context,host,null))
                    .run(context->{assertNull(context.getStartupFailure());assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
                        assertTrue(context.getBeansOfType(SettingsService.class).isEmpty());assertEquals(1,context.getBeansOfType(AssistantReadHostController.class).size());
                        for(String type:List.of("SqlDataSourceScriptDatabaseInitializer","Flyway","SpringLiquibase"))
                            assertTrue(Arrays.stream(context.getBeanDefinitionNames()).noneMatch(name->name.toLowerCase(Locale.ROOT).contains(type.toLowerCase(Locale.ROOT))));});
            verifyNoInteractions(connector);
        }
        assertTrue(new ClassPathScanningCandidateComponentProvider(true).findCandidateComponents("com.knowledgegraph.assistant.host").isEmpty());
    }
    @Test void grantExpiryClosesPreviouslyAvailableHost()throws Exception {
        Bundle b=new Bundle();var c=b.config();var proof=b.proof(c);var clock=new AssistantReadAcceptanceDataSourceTest.MutableClock(b.now);
        var connector=mock(AssistantReadAcceptanceDataSource.Connector.class);var ds=new AssistantReadAcceptanceDataSource(c,proof,connector,clock);
        try(var host=AssistantReadAcceptanceApplication.host(c,proof,ds)) {
            assertTrue(host.capabilities().path("databaseIdentityVerified").asBoolean());clock.now=b.now.plusSeconds(301);
            assertFalse(host.capabilities().path("databaseIdentityVerified").asBoolean());
            assertThrows(ApiException.class,()->host.documents(Set.of(1L),1,20));assertThrows(java.sql.SQLException.class,ds::getConnection);verifyNoInteractions(connector);
        }
    }
    @Test void environmentIsExplicitAndCannotImportBusinessConfiguration() {
        var env=AssistantReadAcceptanceApplication.environment(18088);
        assertFalse(env.getPropertySources().contains("systemEnvironment"));assertFalse(env.getPropertySources().contains("systemProperties"));
        assertEquals("127.0.0.1",env.getProperty("server.address"));assertEquals("18088",env.getProperty("server.port"));assertEquals("never",env.getProperty("spring.sql.init.mode"));
        assertEquals("false",env.getProperty("spring.web.resources.add-mappings"));assertEquals("NONE",env.getProperty("server.forward-headers-strategy"));
    }
    @Test void absentFrontendDoesNotSearchAndUnapprovedRootRejected()throws Exception {
        Bundle b=new Bundle();assertNull(AssistantReadAcceptanceApplication.staticSite(b.config(),b.build));b.site();
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.staticSite(b.config(),b.root.resolve("different-build")));
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.staticSite(b.config(),AssistantReadAcceptanceApplication.APPROVED_FRONTEND_ROOT));
    }
    @Test void staticManifestAndResourceMutationFailClosed()throws Exception {
        Bundle b=new Bundle();var site=b.site();Files.writeString(b.build.resolve("assets/app.js"),"changed synthetic asset");
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/assets/app.js");assertEquals(503,site.get(request).getStatusCode().value());
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.staticSite(b.config(),b.build));
        b.site();Bundle.privateWrite(b.root.resolve("build-manifest.json"),"{}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.staticSite(AssistantReadAcceptanceConfig.load(b.configFile,b.configSha),b.build));
    }
    @Test void reviewedIndependentDistDirectoryDoesNotOverwriteRootBuild()throws Exception {
        Bundle b=new Bundle();b.site();byte[] rootIndex=Files.readAllBytes(b.build.resolve("index.html")),rootJs=Files.readAllBytes(b.build.resolve("assets/app.js"));
        Path independent=b.build.resolve("06-ui-polish-UIPolish-20261001T002724731/site");var site=b.site(independent,b.build);
        assertArrayEquals(rootIndex,Files.readAllBytes(b.build.resolve("index.html")));assertArrayEquals(rootJs,Files.readAllBytes(b.build.resolve("assets/app.js")));
        assertArrayEquals(Files.readAllBytes(independent.resolve("index.html")),site.get(new MockHttpServletRequest("GET","/")).getBody());
        assertFalse(Arrays.equals(rootIndex,site.get(new MockHttpServletRequest("GET","/")).getBody()));
        assertEquals(200,site.get(new MockHttpServletRequest("GET","/assistant/return")).getStatusCode().value());
        assertNull(site.get(new MockHttpServletRequest("HEAD","/assets/app.js")).getBody());
        for(String path:List.of("/api","/api/unknown","/API/unknown"))assertEquals(404,site.get(new MockHttpServletRequest("GET",path)).getStatusCode().value());
        ((ObjectNode)b.config.path("frontend")).put("manifestSha256","a".repeat(64));
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.staticSite(b.config(),b.build));
    }
    @Test void distBoundaryUsesPathComponentsAndRejectsOutsideOrTraversal()throws Exception {
        Bundle b=new Bundle();Path sibling=b.root.resolve("build-other/site");b.site(sibling,b.root);
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.staticSite(b.config(),b.build));
        ((ObjectNode)b.config.path("frontend")).put("directory",b.build+"/../build-other/site");
        assertThrows(IllegalArgumentException.class,b::config);
    }
    @ParameterizedTest @ValueSource(strings={"directory","ancestor","resource"})
    void redirectedDirectoryAncestorOrApprovedResourceRejected(String kind)throws Exception {
        Bundle b=new Bundle();
        if(kind.equals("resource")) {
            var site=b.site();Path source=b.build.resolve("assets"),destination=b.root.resolve("redirected-assets");
            assertTrue(source.toRealPath().startsWith(b.root));assertEquals(b.root,destination.getParent().toRealPath());
            Files.move(source,destination);Bundle.redirect(source,destination);
            assertEquals(503,site.get(new MockHttpServletRequest("GET","/assets/app.js")).getStatusCode().value());
            assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.staticSite(b.config(),b.build));
        }else {
            Path outside=b.root.resolve("outside-dist");Path directory=kind.equals("ancestor")?outside.resolve("site"):outside;
            b.site(directory,b.root);Path link=b.build.resolve("redirect");Bundle.redirect(link,outside);
            ((ObjectNode)b.config.path("frontend")).put("directory",(kind.equals("ancestor")?link.resolve("site"):link).toString());
            assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.staticSite(b.config(),b.build));
        }
    }
    @Test void independentResourceMutationStillFailsClosed()throws Exception {
        Bundle b=new Bundle();var site=b.site(b.build.resolve("reviewed-batch/site"),b.build);
        Path index=b.build.resolve("reviewed-batch/site/index.html");Files.writeString(index,"synthetic changed child index");
        assertEquals(503,site.get(new MockHttpServletRequest("GET","/")).getStatusCode().value());
        assertThrows(IllegalArgumentException.class,()->AssistantReadAcceptanceApplication.staticSite(b.config(),b.build));
    }
    @ParameterizedTest @ValueSource(strings={"/../config.json","/assets/../../config.json","/%2e%2e/config.json","/assets%2fapp.js","/C:/secret","/assets\\app.js","//secret"})
    void staticPathTraversalRejected(String path)throws Exception {
        Bundle b=new Bundle();var site=b.site();MockHttpServletRequest request=new MockHttpServletRequest("GET",path);
        assertEquals(400,site.get(request).getStatusCode().value());
    }
    @Test void sameOriginGetHeadAndSpaNeverFallbackForApiOrWrites()throws Exception {
        Bundle b=new Bundle();var site=b.site();var c=b.config();var proof=b.proof(c);var connector=mock(AssistantReadAcceptanceDataSource.Connector.class);
        var ds=new AssistantReadAcceptanceDataSource(c,proof,connector,Clock.systemUTC());
        try(var host=AssistantReadAcceptanceApplication.host(c,proof,ds)) {
            MockMvc mvc=MockMvcBuilders.standaloneSetup(new AssistantReadHostController(host),site).setControllerAdvice(new AssistantReadHostErrors())
                    .addFilters(new AssistantReadBoundaryFilter()).build();
            mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"));
            mvc.perform(get("/assistant/return")).andExpect(status().isOk());
            mvc.perform(head("/assets/app.js")).andExpect(status().isOk()).andExpect(content().string(""));
            for(String path:List.of("/api","/api/unknown","/API/unknown","/api/settings","/missing.js"))mvc.perform(get(path)).andExpect(status().isNotFound());
            mvc.perform(post("/api/settings")).andExpect(status().isMethodNotAllowed());mvc.perform(post("/assets/app.js")).andExpect(status().isMethodNotAllowed());
            mvc.perform(get("/api/assistant/capabilities")).andExpect(status().isOk());
            mvc.perform(get("/api/assistant/capabilities").header("Origin","http://localhost:5173")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/assistant/capabilities").header("Forwarded","for=127.0.0.1")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/assistant/capabilities").content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(connector);
        }
    }
}
