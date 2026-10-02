package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgegraph.KnowledgeGraphApplication;
import com.knowledgegraph.assistant.host.demo.*;
import com.knowledgegraph.common.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** All grants, passwords, JDBC responses and root types here are SYNTHETIC. No real GRANTS or server startup. */
class AssistantWebsiteDemoAccessTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String SECRET="SYNTHETIC-DEMO-SECRET-ONLY",DB_SECRET="SYNTHETIC-NOT-A-REAL-CREDENTIAL";
    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    @Conditional(AssistantWebsiteReadConfiguration.OrdinaryWebsite.class)
    static class MvcOnly {@Bean GlobalExceptionHandler errors(){return new GlobalExceptionHandler();}}
    static ObjectNode configNode() {
        var n=JSON.createObjectNode().put("schemaVersion","08-website-demo-v1").put("owner","synthetic-owner").put("accessPassword",SECRET).put("sessionSeconds",60);
        n.putObject("reader").put("address","127.0.0.1").put("port",3306).put("database","tupu_kg").put("account","assistant_reader").put("password",DB_SECRET);
        var g=n.putObject("grant").put("mode","selected");g.putArray("libraryIds").add("1");g.putArray("documentIds");g.putArray("nodeIds");return n;
    }
    static AssistantWebsiteDemoConfig config(){return new AssistantWebsiteDemoConfig(configNode());}
    static Path privateConfig()throws Exception {
        Files.createDirectories(Path.of("target"));Path p=Files.createTempDirectory(Path.of("target").toAbsolutePath(),"demo-synthetic-").resolve("config.json");
        Files.write(p,JSON.writeValueAsBytes(configNode()));var acl=Files.getFileAttributeView(p,AclFileAttributeView.class);
        if(acl!=null)acl.setAcl(List.of(AclEntry.newBuilder().setPrincipal(acl.getOwner()).setType(AclEntryType.ALLOW).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        else Files.setPosixFilePermissions(p,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));return p;
    }
    static MockHttpServletRequestBuilder web(MockHttpServletRequestBuilder request) {
        return request.with(r->{r.setServerName("tupu.gmule.cn");r.setScheme("https");r.setSecure(true);r.setServerPort(443);r.setRemoteAddr("203.0.113.9");return r;});
    }
    static class Time extends Clock {
        Instant now=Instant.parse("2026-10-02T12:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}
    }
    static class Browser {
        final Time time=new Time();final MockMvc mvc;
        Browser(AssistantReadHostService host) {this(host,config());}
        Browser(AssistantReadHostService host,AssistantWebsiteDemoConfig config) {mvc=MockMvcBuilders.standaloneSetup(new AssistantReadHostController(host)).setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new AssistantWebsiteDemoAccess(config,time),"/api/assistant/*").build();}
        MvcResult send(MockHttpServletRequestBuilder r,int status)throws Exception {
            var result=mvc.perform(web(r)).andReturn();assertEquals(status,result.getResponse().getStatus(),result.getResponse().getContentAsString());return result;
        }
        Cookie challenge()throws Exception {
            var page=send(get(AssistantWebsiteDemoAccess.LOGIN),200).getResponse();
            assertTrue(page.getContentAsString().contains("type=\"password\""));assertFalse(page.getContentAsString().contains(SECRET));
            assertTrue(page.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));return page.getCookie(AssistantWebsiteDemoAccess.CHALLENGE);
        }
        MvcResult attempt(Cookie csrf,String password,int status)throws Exception {
            return send(post(AssistantWebsiteDemoAccess.LOGIN).header("Origin","https://tupu.gmule.cn").cookie(csrf)
                    .contentType("application/x-www-form-urlencoded").content("csrf="+csrf.getValue()+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8)),status);
        }
        Cookie login()throws Exception {
            var r=attempt(challenge(),SECRET,303).getResponse();assertEquals("/assistant",r.getHeader("Location"));
            var c=r.getCookie(AssistantWebsiteDemoAccess.SESSION);assertNotNull(c);assertTrue(c.isHttpOnly());assertTrue(c.getSecure());assertEquals("/",c.getPath());
            assertTrue(r.getHeaders("Set-Cookie").stream().anyMatch(h->h.contains("SameSite=Strict")));assertFalse(r.getHeaders("Set-Cookie").toString().contains(SECRET));return c;
        }
    }
    static AssistantReadHostService metadataOnly() {
        var host=mock(AssistantReadHostService.class);when(host.capabilities()).thenReturn(JSON.createObjectNode().put("databaseIdentityVerified",false));return host;
    }
    static class JdbcMock {
        final Connection raw=mock(Connection.class);final DatabaseMetaData metadata=mock(DatabaseMetaData.class);
        final Statement bootstrap=mock(Statement.class);final List<PreparedStatement> statements=new ArrayList<>();
        List<String> grants=new ArrayList<>();Properties properties;String url;int opens;
        JdbcMock()throws Exception {
            grants.add("GRANT USAGE ON *.* TO `assistant_reader`@`localhost`");
            for(String t:new TreeSet<>(AssistantWebsiteDemoReadDataSource.TABLES))grants.add("GRANT SELECT ON `tupu_kg`.`"+t+"` TO `assistant_reader`@`localhost`");
            when(raw.getMetaData()).thenReturn(metadata);when(metadata.getDatabaseProductName()).thenReturn("MySQL");when(metadata.getUserName()).thenReturn("assistant_reader@localhost");when(raw.getCatalog()).thenReturn("tupu_kg");when(raw.createStatement()).thenReturn(bootstrap);
            when(bootstrap.executeQuery("SELECT DATABASE(), CURRENT_USER(), VERSION()")).thenAnswer(i->{
                ResultSet r=mock(ResultSet.class);when(r.next()).thenReturn(true,false);when(r.getString(1)).thenReturn("tupu_kg");when(r.getString(2)).thenReturn("assistant_reader@localhost");when(r.getString(3)).thenReturn("8.0.SYNTHETIC");return r;});
            when(bootstrap.executeQuery("SHOW GRANTS FOR CURRENT_USER()")).thenAnswer(i->{
                ResultSet r=mock(ResultSet.class);int[] pos={-1};when(r.next()).thenAnswer(a->++pos[0]<grants.size());when(r.getString(1)).thenAnswer(a->grants.get(pos[0]));return r;});
            when(raw.prepareStatement(anyString())).thenAnswer(i->{
                String sql=i.getArgument(0);PreparedStatement s=mock(PreparedStatement.class);statements.add(s);ResultSet r=mock(ResultSet.class);
                if(sql.startsWith("SELECT COUNT(*)")){when(r.next()).thenReturn(true,false);when(r.getLong(1)).thenReturn(0L);ResultSetMetaData meta=mock(ResultSetMetaData.class);when(meta.getColumnCount()).thenReturn(1);when(r.getMetaData()).thenReturn(meta);}
                when(s.executeQuery()).thenReturn(r);return s;});
        }
        AssistantWebsiteDemoReadDataSource source(Clock clock) {
            return new AssistantWebsiteDemoReadDataSource(config(),(u,p)->{url=u;properties=p;opens++;return raw;},clock);
        }
    }
    @Test void ordinaryWebsiteRoutesAndSeparateLazyReader()throws Exception {
        Path file=privateConfig();DataSource legacy=mock(DataSource.class);
        try(var driver=mockStatic(DriverManager.class)) {
            new WebApplicationContextRunner().withUserConfiguration(MvcOnly.class,AssistantWebsiteReadConfiguration.class)
                    .withInitializer(c->c.getBeanFactory().registerSingleton("syntheticOrdinaryRoot",mock(KnowledgeGraphApplication.class)))
                    .withPropertyValues(AssistantWebsiteReadConfiguration.DEMO_CONFIG+"="+file).withBean(DataSource.class,()->legacy).run(c->{
                        assertNull(c.getStartupFailure());assertEquals(10,c.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().values().stream().filter(m->m.getBeanType()==AssistantReadHostController.class).count());
                        var filter=c.getBean("websiteAssistantReadBoundary",FilterRegistrationBean.class);
                        assertInstanceOf(AssistantWebsiteDemoAccess.class,filter.getFilter());assertEquals(Set.of("/api/assistant/*"),Set.copyOf(filter.getUrlPatterns()));
                        MockMvc mvc=MockMvcBuilders.webAppContextSetup(c.getSourceApplicationContext()).addFilter(filter.getFilter(),"/api/assistant/*").build();
                        assertEquals(401,mvc.perform(web(get("/api/assistant/capabilities"))).andReturn().getResponse().getStatus());verifyNoInteractions(legacy);
                    });driver.verifyNoInteractions();
        }
    }
    @Test void incompleteUnrestrictedOrWrongConfigurationCannotEnableDemo()throws Exception {
        assertThrows(IllegalArgumentException.class,()->AssistantWebsiteDemoFactory.load(Path.of("target/missing-demo-config.json").toAbsolutePath()));
        var bad=configNode();bad.path("reader").deepCopy();((ObjectNode)bad.path("reader")).put("database","other");assertThrows(IllegalArgumentException.class,()->new AssistantWebsiteDemoConfig(bad));
        var noPassword=configNode();noPassword.remove("accessPassword");assertThrows(IllegalArgumentException.class,()->new AssistantWebsiteDemoConfig(noPassword));
        var badAccount=configNode();((ObjectNode)badAccount.path("reader")).put("account","root_writer");assertThrows(IllegalArgumentException.class,()->new AssistantWebsiteDemoConfig(badAccount));
        var mvc=MockMvcBuilders.standaloneSetup(new AssistantReadHostController(metadataOnly())).addFilter(new AssistantWebsiteDemoAccess(null),"/api/assistant/*").build();
        assertEquals(503,mvc.perform(web(get(AssistantWebsiteDemoAccess.LOGIN))).andReturn().getResponse().getStatus());
        Path p=privateConfig();var acl=Files.getFileAttributeView(p,AclFileAttributeView.class);
        if(acl!=null){var entries=new ArrayList<>(acl.getAcl());entries.add(AclEntry.newBuilder().setPrincipal(p.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone")).setType(AclEntryType.ALLOW).setPermissions(AclEntryPermission.READ_DATA).build());acl.setAcl(entries);}
        else Files.setPosixFilePermissions(p,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.GROUP_READ));
        assertThrows(IllegalArgumentException.class,()->AssistantWebsiteDemoFactory.load(p));
    }
    @Test void browserLoginWrongSecretExpiryReplayAndLogout()throws Exception {
        var browser=new Browser(metadataOnly());browser.send(get("/api/assistant/capabilities"),401);
        var nonce=browser.challenge();browser.attempt(nonce,"WRONG_SYNTHETIC",401);browser.attempt(nonce,SECRET,403);
        Cookie session=browser.login();browser.send(get("/api/assistant/capabilities").cookie(session),200);
        browser.time.now=browser.time.now.plusSeconds(61);browser.send(get("/api/assistant/capabilities").cookie(session),401);
        Cookie fresh=browser.login();browser.send(post(AssistantWebsiteDemoAccess.LOGOUT).cookie(fresh).header("Origin","https://tupu.gmule.cn"),204);
        browser.send(get("/api/assistant/capabilities").cookie(fresh),401);
        var chineseConfig=configNode();String chinese="口".repeat(256);chineseConfig.put("accessPassword",chinese);
        var chineseBrowser=new Browser(metadataOnly(),new AssistantWebsiteDemoConfig(chineseConfig));
        chineseBrowser.attempt(chineseBrowser.challenge(),chinese,303);
    }
    @Test void csrfOriginHostAndProxyHeadersCannotSelfAuthorize()throws Exception {
        var browser=new Browser(metadataOnly());var nonce=browser.challenge();
        browser.send(post(AssistantWebsiteDemoAccess.LOGIN).cookie(nonce).contentType("application/x-www-form-urlencoded").content("csrf="+nonce.getValue()+"&password="+SECRET),403);
        Cookie session=browser.login();browser.send(post("/api/assistant/runs").cookie(session).header("Origin","https://evil.invalid").contentType("application/json").content("{}"),403);
        browser.send(get("/api/assistant/capabilities?owner=other").cookie(session),400);
        browser.send(get("/api/assistant/capabilities").header("Forwarded","for=127.0.0.1").cookie(session),401);
        browser.send(get("/api/assistant/capabilities").header("X-Forwarded-Proto","https").cookie(session),401);
        var accepted=web(get("/api/assistant/capabilities").cookie(session).header("X-Forwarded-Proto","https").header("X-Forwarded-For","attacker"))
                .with(r->{r.setRemoteAddr("127.0.0.1");r.setSecure(false);r.setScheme("http");return r;});
        assertEquals(200,browser.mvc.perform(accepted).andReturn().getResponse().getStatus());
        assertEquals(401,browser.mvc.perform(web(get("/api/assistant/capabilities").cookie(session)).with(r->{r.setServerName("evil.invalid");return r;})).andReturn().getResponse().getStatus());
        browser.send(get("/api/assistant/capabilities").cookie(session).content("{}"),400);
    }
    @Test void mockJdbcIdentityGrantsBudgetAndRealDomainComposition()throws Exception {
        JdbcMock jdbc=new JdbcMock();Time time=new Time();var source=jdbc.source(time);assertEquals(0,jdbc.opens);
        try(var host=AssistantWebsiteDemoFactory.host(config(),source)) {
            assertEquals(0,jdbc.opens);var browser=new Browser(host);Cookie session=browser.login();assertEquals(0,jdbc.opens);
            var cap=JSON.readTree(browser.send(get("/api/assistant/capabilities").cookie(session),200).getResponse().getContentAsByteArray());
            assertTrue(cap.at("/data/databaseIdentityVerified").asBoolean());assertEquals("website_demo_single_owner",cap.at("/data/accessMode").asText());
            assertEquals("deterministic_tools",cap.at("/data/executionMode").asText());assertEquals("unavailable",cap.at("/data/capabilities/7/status").asText());
            var libs=JSON.readTree(browser.send(get("/api/assistant/scope/libraries").cookie(session),200).getResponse().getContentAsByteArray());assertEquals(0,libs.at("/data/items").size());
            assertEquals(AssistantWebsiteDemoReadDataSource.URL,jdbc.url);assertEquals("3000",jdbc.properties.getProperty("connectTimeout"));assertEquals("4000",jdbc.properties.getProperty("socketTimeout"));assertEquals("false",jdbc.properties.getProperty("allowMultiQueries"));
            verify(jdbc.bootstrap,atLeastOnce()).execute("SET SESSION max_execution_time = 1500");verify(jdbc.raw,atLeastOnce()).setNetworkTimeout(any(),eq(4000));verify(jdbc.raw,atLeastOnce()).close();
            for(var s:jdbc.statements){verify(s,atLeastOnce()).setQueryTimeout(2);verify(s,never()).setQueryTimeout(3);}
            assertFalse(cap.toString().contains(SECRET));assertFalse(cap.toString().contains(DB_SECRET));
            time.now=time.now.plusSeconds(61);jdbc.grants.add("GRANT UPDATE ON `tupu_kg`.`documents` TO `assistant_reader`@`localhost`");assertFalse(source.verifiedPermission());
            assertThrows(SQLException.class,source::getConnection);
        }
    }
    @Test void extraPrivilegesRolesWrongIdentityAndFailedConnectionRejectAndRelease()throws Exception {
        for(String extra:List.of("GRANT SELECT ON `tupu_kg`.* TO `assistant_reader`@`localhost`","GRANT SELECT ON `tupu_kg`.`llm_profiles` TO `assistant_reader`@`localhost`","GRANT `write_role`@`%` TO `assistant_reader`@`localhost`")) {
            JdbcMock jdbc=new JdbcMock();jdbc.grants.add(extra);assertThrows(SQLException.class,()->jdbc.source(new Time()).getConnection());verify(jdbc.raw).close();
        }
        JdbcMock wrong=new JdbcMock();when(wrong.metadata.getUserName()).thenReturn("root@localhost");assertThrows(SQLException.class,()->wrong.source(new Time()).getConnection());verify(wrong.raw).close();
        JdbcMock fail=new JdbcMock();when(fail.bootstrap.executeQuery("SHOW GRANTS FOR CURRENT_USER()")).thenThrow(new SQLException("SYNTHETIC secret SQL "+DB_SECRET,"HY000",3024,new IllegalStateException(SECRET)));
        var ex=assertThrows(SQLException.class,()->fail.source(new Time()).getConnection());assertEquals("HY000",ex.getSQLState());assertEquals(3024,ex.getErrorCode());assertNull(ex.getCause());assertFalse(ex.toString().contains(DB_SECRET));verify(fail.raw).close();
    }
    @Test void selectedGrantAndIdLimitRemainServerOwned()throws Exception {
        JdbcMock jdbc=new JdbcMock();try(var host=AssistantWebsiteDemoFactory.host(config(),jdbc.source(new Time()))) {
            var browser=new Browser(host);Cookie session=browser.login();var out=browser.send(get("/api/assistant/scope/documents").param("libraryIds","999").cookie(session),403);
            assertEquals("SCOPE_NOT_ALLOWED",JSON.readTree(out.getResponse().getContentAsByteArray()).path("code").asText());
        }
        var tooMany=configNode();var ids=((ObjectNode)tooMany.path("grant")).putArray("libraryIds");for(int i=1;i<=1001;i++)ids.add(Integer.toString(i));assertThrows(IllegalArgumentException.class,()->new AssistantWebsiteDemoConfig(tooMany));
        var nodes=configNode();((ObjectNode)nodes.path("grant")).putArray("nodeIds").add("1");assertThrows(IllegalArgumentException.class,()->new AssistantWebsiteDemoConfig(nodes));
    }
    @Test void oldI2DefaultScanAndModelRemainUnchanged() {
        assertTrue(new ClassPathScanningCandidateComponentProvider(true).findCandidateComponents("com.knowledgegraph.assistant.host").isEmpty());
        assertThrows(IllegalArgumentException.class,()->AssistantReadHostApplication.main(new String[]{"--enable=true"}));
        try(var host=AssistantReadHostService.disabled()) {var cap=host.capabilities();assertFalse(cap.path("databaseIdentityVerified").asBoolean());assertEquals("isolated_local_single_owner",cap.path("accessMode").asText());assertEquals("unavailable",cap.path("capabilities").get(7).path("status").asText());}
    }
}
