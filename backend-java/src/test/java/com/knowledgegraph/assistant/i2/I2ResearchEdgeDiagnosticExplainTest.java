package com.knowledgegraph.assistant.i2;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** D2 integration-only executor for the original 03's sealed diagnostic templates. */
@EnabledIfEnvironmentVariable(named = "KG_I2_AUTHORIZED", matches = "8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2ResearchEdgeDiagnosticExplainTest extends I2MySqlSupport {
    private static final String TEMPLATE_SHA = "531988319b3aed79a8bcd3f5c9088f4bc7f11273573553bb8a183af309c68d01";
    private static final String E02_SHA = "72edc590f96d98642e530d8f6aadcd8614b29014f7497817fa7a2e4cdbfde609";
    private static final Map<String, Integer> COUNTS = Map.of("P1", 22, "P2", 16, "P3", 10);

    private static final class Trace {
        String preparedSql;
        Long sessionBudgetMs;
        Integer statementTimeoutSeconds;
        int executeQueryCount;
        boolean executeAttempted;
        final Map<Integer, Map<String, Object>> bindings = new TreeMap<>();
        JsonNode expectedBindings;
        String expectedPreparedSql;
    }

    @Test void explainExactlyOneApprovedD2Probe() throws Exception {
        String probeId = System.getenv("KG_I2_D2_PROBE");
        String evidenceDir = System.getenv("KG_I2_D2_EVIDENCE_DIR");
        assertTrue(COUNTS.containsKey(probeId), "Unapproved D2 probe");
        assertNotNull(evidenceDir, "Unique D2 evidence directory required");
        Path output = Path.of(evidenceDir, probeId + ".json");
        assertTrue(Files.isDirectory(output.getParent()));
        assertFalse(Files.exists(output), "Never overwrite probe evidence");

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("probe", probeId);
        record.put("diagnosticOnly", true);
        record.put("businessSelectExecuted", false);
        record.put("templateRawSha256", TEMPLATE_SHA);
        record.put("baselineE02RawSha256", E02_SHA);
        Trace trace = new Trace();
        Throwable failure = null;
        try {
            JsonNode template = loadPinnedJson(System.getenv("KG_I2_D2_TEMPLATE"), TEMPLATE_SHA);
            JsonNode baseline = loadPinnedJson(System.getenv("KG_I2_D2_BASELINE"), E02_SHA);
            assertEquals("I2-R4-D2-research-diagnostic-template-v1", template.path("schema").asText());
            assertEquals("E02", template.path("baseline").path("case").asText());
            assertEquals(E02_SHA, template.path("baseline").path("rawSha256").asText());
            assertEquals(sha256(baseline.path("sqlTemplate").asText()), template.path("baseline").path("namedSelectSha256").asText());
            assertEquals(3, template.path("probes").size());
            for (JsonNode item : template.path("probes")) checkTemplateSqlHashes(item);
            JsonNode probe = null;
            for (JsonNode item : template.path("probes")) {
                if (probeId.equals(item.path("id").asText())) probe = item;
            }
            assertNotNull(probe);
            assertTrue(probe.path("diagnosticOnly").asBoolean());
            assertFalse(probe.path("businessSelectExecutionAllowed").asBoolean(true));
            assertEquals(COUNTS.get(probeId).intValue(), probe.path("orderedBindingsExpected").size());
            assertEquals(COUNTS.get(probeId).intValue(), probe.path("staticCounts").path("expectedQuestionMarks").asInt());
            assertEquals(3000, template.path("budgets").path("connectMs").asInt());
            assertEquals(4000, template.path("budgets").path("socketMs").asInt());
            assertEquals(2, template.path("budgets").path("statementSeconds").asInt());
            assertEquals(1500, template.path("budgets").path("sessionMaxExecutionTimeMs").asInt());

            trace.expectedPreparedSql = probe.path("expectedPreparedExplainSql").asText();
            trace.expectedBindings = probe.path("orderedBindingsExpected");
            String namedExplainSql = probe.path("namedExplainSql").asText();
            assertTrue(namedExplainSql.startsWith("EXPLAIN FORMAT=JSON SELECT "));
            assertEquals("EXPLAIN FORMAT=JSON " + probe.path("namedSelectSql").asText(), namedExplainSql);
            record.put("namedExplainSql", namedExplainSql);
            record.put("namedExplainSha256", sha256(namedExplainSql));
            record.put("expectedPreparedExplainSha256", sha256(trace.expectedPreparedSql));
            record.put("expectedBindings", trace.expectedBindings);

            JsonNode definitions = probe.path("parameterDefinitions");
            List<Long> libraries = longList(definitions.path("libraryIds").path("elements"));
            List<Long> documents = longList(definitions.path("documentIds").path("elements"));
            Long nodeId = Long.valueOf(definitions.path("nodeId").path("value").longValue());
            Integer maxRows = Integer.valueOf(definitions.path("maxRows").path("value").intValue());
            assertEquals(List.of(7101L), libraries);
            assertEquals(List.of(7201L, 7202L), documents);
            assertEquals(Long.valueOf(7701), nodeId);
            assertEquals(Integer.valueOf(11), maxRows);
            assertEquals("java.lang.Long", definitions.path("libraryIds").path("javaType").asText());
            assertEquals("java.lang.Long", definitions.path("documentIds").path("javaType").asText());
            assertEquals("java.lang.Long", definitions.path("nodeId").path("javaType").asText());
            assertEquals("java.lang.Integer", definitions.path("maxRows").path("javaType").asText());
            record.put("namedParameters", Map.of("nodeId", nodeId, "libraryIds", libraries, "documentIds", documents, "maxRows", maxRows));

            JdbcTemplate jdbcTemplate = new JdbcTemplate(recordingDataSource(trace));
            jdbcTemplate.setQueryTimeout(2);
            JdbcClient.StatementSpec statement = JdbcClient.create(jdbcTemplate).sql(namedExplainSql)
                    .param("nodeId", nodeId).param("libraryIds", libraries)
                    .param("documentIds", documents).param("maxRows", maxRows);
            long began = System.nanoTime();
            try {
                String rawPlan = statement.query(String.class).single();
                record.put("planRaw", rawPlan);
                JsonNode plan;
                try { plan = JSON.readTree(rawPlan); }
                catch (Exception parseError) { record.put("status", "plan_parse_error"); throw parseError; }
                if (plan == null || !plan.isObject() || !plan.has("query_block")) {
                    record.put("status", "plan_invalid");
                    throw new IllegalStateException("EXPLAIN returned no valid query_block JSON plan");
                }
                record.put("plan", plan);
                record.put("status", "plan_captured");
            } catch (Throwable ex) {
                if (!"plan_invalid".equals(record.get("status")) && !"plan_parse_error".equals(record.get("status"))) {
                    record.put("status", trace.executeAttempted ? "explain_error" : "preflight_or_trace_error");
                }
                addError(record, ex);
                failure = ex;
            } finally {
                record.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began));
            }
            if (!trace.executeAttempted && failure == null) {
                throw new IllegalStateException("EXPLAIN returned without a JDBC execution boundary");
            }
        } catch (Throwable ex) {
            if (failure == null) failure = ex;
            if (record.get("status") == null) record.put("status", "preflight_or_trace_error");
            record.put("traceErrorType", ex.getClass().getName());
            record.put("traceErrorMessage", String.valueOf(ex.getMessage()));
        } finally {
            record.put("preparedSql", trace.preparedSql);
            record.put("preparedSqlSha256", trace.preparedSql == null ? null : sha256(trace.preparedSql));
            record.put("orderedBindingsActual", new ArrayList<>(trace.bindings.values()));
            record.put("explainConnectionSessionMaxExecutionTimeMs", trace.sessionBudgetMs);
            record.put("jdbcQueryTimeoutSeconds", trace.statementTimeoutSeconds);
            record.put("executeQueryCount", trace.executeQueryCount);
            record.put("executeAttempted", trace.executeAttempted);
            Files.writeString(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(record),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        if (failure != null) fail("D2 " + probeId + " retained diagnostic error: " + failure.getClass().getName());
    }

    private static JsonNode loadPinnedJson(String file, String expectedSha) throws Exception {
        assertNotNull(file, "Pinned diagnostic input path missing");
        byte[] bytes = Files.readAllBytes(Path.of(file));
        assertEquals(expectedSha, sha256(bytes), "Pinned diagnostic input changed");
        return JSON.readTree(bytes);
    }

    private static void checkTemplateSqlHashes(JsonNode probe) {
        assertTrue(COUNTS.containsKey(probe.path("id").asText()));
        JsonNode hashes = probe.path("hashes");
        assertEquals(hashes.path("namedSelectSha256").asText(), sha256(probe.path("namedSelectSql").asText()));
        assertEquals(hashes.path("namedExplainSha256").asText(), sha256(probe.path("namedExplainSql").asText()));
        assertEquals(hashes.path("expectedPreparedExplainSha256").asText(), sha256(probe.path("expectedPreparedExplainSql").asText()));
    }

    private static List<Long> longList(JsonNode elements) {
        assertTrue(elements.isArray());
        List<Long> values = new ArrayList<>();
        for (JsonNode element : elements) values.add(Long.valueOf(element.longValue()));
        return values;
    }

    private static DataSource recordingDataSource(Trace trace) {
        return new DelegatingDataSource(dataSource) {
            @Override public Connection getConnection() throws SQLException {
                Connection original = super.getConnection();
                try (Statement check = original.createStatement();
                     ResultSet result = check.executeQuery("SELECT @@SESSION.max_execution_time")) {
                    if (!result.next()) throw new SQLException("Session budget observation missing");
                    trace.sessionBudgetMs = result.getLong(1);
                    if (trace.sessionBudgetMs != 1500L) throw new SQLException("EXPLAIN connection session budget changed");
                } catch (SQLException ex) { original.close(); throw ex; }
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                            if (method.getName().equals("createStatement") || method.getName().equals("prepareCall"))
                                throw new SQLException("Only the approved prepared EXPLAIN is allowed");
                            if (method.getName().equals("prepareStatement") && args != null && args.length > 0 && args[0] instanceof String sql) {
                                if (!sql.equals(trace.expectedPreparedSql)) throw new SQLException("Prepared EXPLAIN SQL differs from sealed template");
                                if (trace.preparedSql != null) throw new SQLException("Second prepared statement forbidden");
                                trace.preparedSql = sql;
                                PreparedStatement statement = (PreparedStatement) invoke(original, method, args);
                                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                        new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                                            if (m.getName().equals("setQueryTimeout")) {
                                                int seconds = (Integer) a[0];
                                                if (seconds != 2) throw new SQLException("Statement timeout changed");
                                                trace.statementTimeoutSeconds = seconds;
                                            }
                                            if (m.getName().startsWith("set") && a != null && a.length >= 2 && a[0] instanceof Integer index) {
                                                if (!m.getName().equals("setObject")) throw new SQLException("Unexpected JDBC binding method");
                                                if (index < 1 || index > trace.expectedBindings.size() || trace.bindings.containsKey(index))
                                                    throw new SQLException("Binding position missing, duplicate, or out of range");
                                                Object value = a[1];
                                                JsonNode expected = trace.expectedBindings.get(index - 1);
                                                String type = value == null ? "NULL" : value.getClass().getName();
                                                if (expected.path("position").asInt() != index || !type.equals(expected.path("javaType").asText())
                                                        || !(value instanceof Number number) || number.longValue() != expected.path("value").longValue())
                                                    throw new SQLException("JDBC binding differs from sealed position/type/value");
                                                Map<String, Object> binding = new LinkedHashMap<>();
                                                binding.put("position", index); binding.put("method", m.getName());
                                                binding.put("javaType", type); binding.put("value", value);
                                                trace.bindings.put(index, binding);
                                            }
                                            if (m.getName().equals("executeQuery")) {
                                                if (trace.executeAttempted || trace.bindings.size() != trace.expectedBindings.size()
                                                        || trace.statementTimeoutSeconds == null || trace.statementTimeoutSeconds != 2
                                                        || trace.sessionBudgetMs == null || trace.sessionBudgetMs != 1500L)
                                                    throw new SQLException("Pre-execution SQL, binding, or budget gate failed");
                                                trace.executeAttempted = true;
                                                trace.executeQueryCount++;
                                            } else if (m.getName().startsWith("execute")) {
                                                throw new SQLException("Only one executeQuery is allowed");
                                            }
                                            return invoke(statement, m, a);
                                        });
                            }
                            return invoke(original, method, args);
                        });
            }
            @Override public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("Explicit credentials forbidden in diagnostic child");
            }
        };
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException ex) { throw ex.getCause(); }
    }

    private static void addError(Map<String, Object> record, Throwable ex) {
        List<Map<String, Object>> chain = new ArrayList<>();
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("type", cause.getClass().getName());
            part.put("message", String.valueOf(cause.getMessage()));
            if (cause instanceof SQLException sql) {
                part.put("sqlState", sql.getSQLState()); part.put("vendorCode", sql.getErrorCode());
                if (!record.containsKey("sqlState")) {
                    record.put("sqlState", sql.getSQLState()); record.put("vendorCode", sql.getErrorCode());
                }
            }
            chain.add(part);
        }
        record.put("errorChain", chain);
    }

    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha256(byte[] value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
