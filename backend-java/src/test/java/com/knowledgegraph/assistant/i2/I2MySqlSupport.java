package com.knowledgegraph.assistant.i2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.ingestion.*;
import com.knowledgegraph.assistant.quality.*;
import com.knowledgegraph.assistant.research.*;
import com.knowledgegraph.assistant.source.*;
import com.knowledgegraph.assistant.learning.*;
import com.knowledgegraph.assistant.assessment.*;
import com.knowledgegraph.assistant.tool.AssistantTool;
import com.knowledgegraph.config.AppProperties;
import com.knowledgegraph.settings.SettingsService;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** I2 harness only. No Spring context, application.yml, model, HTTP, or write credential. */
abstract class I2MySqlSupport {
    static final String UUID = "8c6cfcb3-ba49-11f1-bf64-005056c00001";
    static final String DB = "kg_assistant_i2_20260927_r01";
    static final ObjectMapper JSON = new ObjectMapper();
    static final Scope LIB = scope(Set.of(7101L),Set.of(),Set.of());
    static final Scope AB = scope(Set.of(7101L),Set.of(7201L,7202L),Set.of());
    static final Scope A = scope(Set.of(7101L),Set.of(7201L),Set.of());
    static final Scope DISJOINT = scope(Set.of(7101L),Set.of(7205L),Set.of());
    static final Scope NODE = scope(Set.of(7101L),Set.of(),Set.of(7701L));
    static DriverManagerDataSource dataSource;
    static JdbcTemplate template;
    static JdbcClient jdbc;
    static JdbcIngestionReadRepository ingestion;
    static JdbcSourceLocator source;
    static JdbcQualityReadRepository quality;
    static JdbcResearchReadRepository research;
    static JdbcLearningReadRepository learning;
    static JdbcAssessmentReadRepository assessment;
    static JsonNode fixture;

    static Scope scope(Set<Long> libraries, Set<Long> docs, Set<Long> nodes) {
        return new Scope(ScopeMode.SELECTED,libraries,docs,nodes);
    }
    @BeforeAll static void connectOnlyApprovedReader() throws Exception {
        assertEquals(UUID,System.getenv("KG_I2_AUTHORIZED"),"Explicit I2 child-process authorization required");
        assertNull(System.getenv("KG_I2_ADMIN_PASSWORD"));
        assertNull(System.getenv("KG_I2_LOADER_PASSWORD"));
        String password = System.getenv("KG_I2_READER_PASSWORD");
        assertNotNull(password,"Reader credential absent; never fall back to application configuration");
        dataSource = new DriverManagerDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection c = super.getConnection();
                try (var s=c.createStatement()) { s.execute("SET SESSION max_execution_time=1500"); }
                catch (SQLException e) { c.close(); throw e; }
                return c;
            }
        };
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://127.0.0.1:33078/"+DB+"?sslMode=REQUIRED&connectTimeout=3000&socketTimeout=4000&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        dataSource.setUsername("kg_i2_reader"); dataSource.setPassword(password);
        template = new JdbcTemplate(dataSource); template.setQueryTimeout(2);
        jdbc = JdbcClient.create(template);
        assertEquals("kg_i2_reader@127.0.0.1",jdbc.sql("SELECT CURRENT_USER()").query(String.class).single());
        assertEquals(UUID,jdbc.sql("SELECT @@server_uuid").query(String.class).single());
        assertEquals("8.4.11",jdbc.sql("SELECT VERSION()").query(String.class).single());
        assertEquals(DB,jdbc.sql("SELECT DATABASE()").query(String.class).single());
        assertEquals(33078,jdbc.sql("SELECT @@port").query(Integer.class).single());
        assertEquals("D:/kg-i2-env/mysql-data/",jdbc.sql("SELECT @@datadir").query(String.class).single().replace('\\','/'));
        fixture = JSON.readTree(Files.readString(Path.of("../docs/assistant-team/integration/I1/fixtures/I1-FIXTURES.json")));
        // Real SettingsService read query, no cipher use for this non-secret synthetic setting.
        ingestion = new JdbcIngestionReadRepository(jdbc,new SettingsService(jdbc,null,new AppProperties()));
        source = new JdbcSourceLocator(jdbc); quality = new JdbcQualityReadRepository(jdbc,JSON);
        research = new JdbcResearchReadRepository(jdbc); learning = new JdbcLearningReadRepository(jdbc,research);
        assessment = new JdbcAssessmentReadRepository(jdbc);
    }
    static String hash(long id) {
        for (JsonNode chunk : fixture.path("chunks")) if (chunk.path("id").asLong()==id) return chunk.path("contentHash").asText();
        throw new IllegalArgumentException("Unknown fixture chunk");
    }
    static ToolResult call(List<AssistantTool> tools, String name, Role role, Scope scope, String args) throws Exception {
        var tool = tools.stream().filter(t->t.descriptor().name().equals(name)).findFirst().orElseThrow();
        JsonNode parameters=JSON.readTree(args); tool.validateArguments(parameters,scope);
        return tool.execute(new AssistantTool.Context("i2-isolated-run","i2-test",role,scope,Instant.now().plusSeconds(10),()->false),parameters);
    }
    static ToolResult researchTool(String name, Scope scope, String args) throws Exception {
        return call(new ResearchAgentToolProvider(research,source).tools(),"research."+name,Role.GRAPH_RESEARCH,scope,args);
    }
    static ToolResult assessmentTool(String name, String args) throws Exception {
        // Host not contacted by these fact reads. A missing host is explicitly represented, not production integration.
        return call(new AssessmentAgentToolProvider(learning,assessment,research,source,(r,t)->Optional.empty()).tools(),
                "assessment."+name,Role.ASSESSMENT_ANALYSIS,LIB,args);
    }
}
