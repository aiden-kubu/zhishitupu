package com.knowledgegraph.assistant.i2;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.research.JdbcResearchReadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Integration diagnostics only: invoke the unchanged repository against a mock, then run one EXPLAIN. */
@EnabledIfEnvironmentVariable(named = "KG_I2_AUTHORIZED", matches = "8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2ResearchEdgeExplainTest extends I2MySqlSupport {
    private static final String SOURCE_SHA256 = "b922ec24fc397fd772524524c34c6837a3f044bff4bd18efcdd23c37ae67612f";
    private static final String EVIDENCE = "../docs/assistant-team/integration/I2/R3/evidence/POSTRUN-SUMMARY.json";

    private record Case(String id, boolean outgoing, Scope scope, long nodeId) {}
    private static final class Captured {
        String template;
        final Map<String, Object> parameters = new LinkedHashMap<>();
    }
    private static final class PreparedTrace {
        String sql;
        Long sessionMaxExecutionTimeMs;
        Integer timeoutSeconds;
        int executions;
        final Map<Integer, Map<String, Object>> bindings = new TreeMap<>();
    }

    @Test void explainExactlyOneApprovedEdgeCall() throws Exception {
        String id = System.getenv("KG_I2_R4_CASE");
        String directory = System.getenv("KG_I2_R4_EVIDENCE_DIR");
        assertNotNull(directory, "Unique evidence directory required");
        Case target = switch (id == null ? "" : id) {
            case "E01" -> new Case("E01", true, AB, 7702);
            case "E02" -> new Case("E02", true, AB, 7701);
            case "E03" -> new Case("E03", false, AB, 7701);
            case "E04" -> new Case("E04", false, LIB, 7701);
            default -> throw new IllegalArgumentException("Unapproved diagnostic case");
        };
        Path output = Path.of(directory, id + ".json");
        assertTrue(Files.isDirectory(output.getParent()), "Evidence directory must already exist");
        assertFalse(Files.exists(output), "Never overwrite diagnostic evidence");

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("case", id);
        record.put("sourceNormalizedSha256", SOURCE_SHA256);
        record.put("scopeMode", "SELECTED");
        record.put("nodeId", target.nodeId());
        record.put("businessLimit", 10);
        record.put("onlyExplain", true);
        PreparedTrace trace = new PreparedTrace();
        Throwable failure = null;
        try {
            Captured captured = capture(target);
            record.put("sqlTemplate", captured.template);
            record.put("namedParameters", printableParameters(captured.parameters));
            Set<String> expectedNames = id.equals("E04")
                    ? Set.of("libraryIds", "nodeId", "maxRows")
                    : Set.of("libraryIds", "documentIds", "nodeId", "maxRows");
            assertEquals(expectedNames, captured.parameters.keySet(), "Unexpected named bindings");
            assertEquals(Set.of(7101L), captured.parameters.get("libraryIds"));
            assertEquals(target.nodeId(), captured.parameters.get("nodeId"));
            assertEquals(11, captured.parameters.get("maxRows"));
            if (id.equals("E04")) assertFalse(captured.parameters.containsKey("documentIds"));
            else assertEquals(Set.of(7201L, 7202L), captured.parameters.get("documentIds"));
            assertTrue(captured.template.startsWith("SELECT e.id,e.source_node_id,e.target_node_id,"));
            assertTrue(captured.template.contains(" AS has_source FROM knowledge_edges e "));
            assertTrue(captured.template.endsWith(" ORDER BY e.id LIMIT :maxRows"));
            if (id.equals("E01")) {
                assertEquals(canonical(r3FailureSql()), canonical(expand(captured)));
                record.put("r3FailedSqlMatches", true);
            }
            JdbcTemplate template = new JdbcTemplate(recordingDataSource(trace));
            template.setQueryTimeout(2);
            JdbcClient diagnostic = JdbcClient.create(template);
            JdbcClient.StatementSpec statement = diagnostic.sql("EXPLAIN FORMAT=JSON " + captured.template);
            for (Map.Entry<String, Object> param : captured.parameters.entrySet()) {
                statement = statement.param(param.getKey(), param.getValue());
            }
            long started = System.nanoTime();
            try {
                String plan = statement.query(String.class).single();
                record.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                record.put("plan", JSON.readTree(plan));
                record.put("status", "plan_captured");
            } catch (Throwable ex) {
                record.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                record.put("status", "explain_error");
                record.put("errorType", ex.getClass().getName());
                record.put("errorMessage", String.valueOf(ex.getMessage()));
                for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                    if (cause instanceof SQLException sql) {
                        record.put("sqlState", sql.getSQLState());
                        record.put("vendorCode", sql.getErrorCode());
                        break;
                    }
                }
                failure = ex;
            }
            assertEquals(1, trace.executions, "One EXPLAIN execution required");
            assertNotNull(trace.sessionMaxExecutionTimeMs, "EXPLAIN connection budget observation missing");
            assertEquals(1500L, trace.sessionMaxExecutionTimeMs.longValue(),
                    "The EXPLAIN connection must retain the original session budget");
            assertNotNull(trace.timeoutSeconds, "JDBC query timeout observation missing");
            assertEquals(2, trace.timeoutSeconds.intValue(), "JDBC two-second statement budget required");
            assertEquals(canonical("EXPLAIN FORMAT=JSON " + expand(captured)), canonical(trace.sql));
            assertEquals(trace.sql.chars().filter(ch -> ch == '?').count(), trace.bindings.size());
        } catch (Throwable ex) {
            if (failure == null) failure = ex;
            record.put("status", "preflight_or_trace_error");
            record.put("traceErrorType", ex.getClass().getName());
            record.put("traceErrorMessage", String.valueOf(ex.getMessage()));
        } finally {
            record.put("preparedSql", trace.sql);
            record.put("preparedSqlSha256", trace.sql == null ? null : sha256(trace.sql));
            record.put("orderedBindings", new ArrayList<>(trace.bindings.values()));
            record.put("jdbcQueryTimeoutSeconds", trace.timeoutSeconds);
            record.put("explainConnectionSessionMaxExecutionTimeMs", trace.sessionMaxExecutionTimeMs);
            record.put("executeQueryCount", trace.executions);
            Files.writeString(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(record),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        if (failure != null) fail("R4 " + id + " retained a diagnostic error: " + failure.getMessage());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Captured capture(Case target) {
        Captured result = new Captured();
        JdbcClient mockJdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        JdbcClient.MappedQuerySpec<Object> rows = mock(JdbcClient.MappedQuerySpec.class);
        when(mockJdbc.sql(anyString())).thenAnswer(call -> {
            assertNull(result.template, "The original method must prepare exactly one statement");
            result.template = call.getArgument(0);
            return spec;
        });
        when(spec.param(anyString(), any())).thenAnswer(call -> {
            result.parameters.put(call.getArgument(0), call.getArgument(1));
            return spec;
        });
        when(spec.query(any(org.springframework.jdbc.core.RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec) rows);
        when(rows.list()).thenReturn(List.of());
        JdbcResearchReadRepository original = new JdbcResearchReadRepository(mockJdbc);
        if (target.outgoing()) original.outgoingEdges(target.scope(), target.nodeId(), 10);
        else original.incidentEdges(target.scope(), target.nodeId(), 10);
        assertNotNull(result.template);
        return result;
    }

    private static DataSource recordingDataSource(PreparedTrace trace) {
        return new DelegatingDataSource(dataSource) {
            @Override public Connection getConnection() throws SQLException {
                Connection original = super.getConnection();
                try (Statement check = original.createStatement();
                     ResultSet result = check.executeQuery("SELECT @@SESSION.max_execution_time")) {
                    if (!result.next()) throw new SQLException("Session budget observation missing");
                    trace.sessionMaxExecutionTimeMs = result.getLong(1);
                    if (trace.sessionMaxExecutionTimeMs != 1500L)
                        throw new SQLException("EXPLAIN connection session budget changed");
                } catch (SQLException ex) {
                    original.close();
                    throw ex;
                }
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                            if (method.getName().equals("prepareStatement") && args != null
                                    && args.length > 0 && args[0] instanceof String sql) {
                                if (!sql.startsWith("EXPLAIN FORMAT=JSON SELECT "))
                                    throw new SQLException("Only EXPLAIN FORMAT=JSON is allowed");
                                if (trace.sql != null) throw new SQLException("A second prepared statement is forbidden");
                                trace.sql = sql;
                                PreparedStatement statement = (PreparedStatement) invoke(original, method, args);
                                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                        new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                                            if (m.getName().equals("setQueryTimeout")) trace.timeoutSeconds = (Integer) a[0];
                                            if (isBinding(m, a)) {
                                                int index = (Integer) a[0];
                                                Object value = a.length > 1 ? a[1] : null;
                                                if (value instanceof byte[] bytes) value = Arrays.toString(bytes);
                                                Map<String, Object> bound = new LinkedHashMap<>();
                                                bound.put("index", index);
                                                bound.put("method", m.getName());
                                                bound.put("type", value == null ? "NULL" : value.getClass().getName());
                                                bound.put("value", value);
                                                trace.bindings.put(index, bound);
                                            }
                                            if (m.getName().equals("executeQuery")) trace.executions++;
                                            return invoke(statement, m, a);
                                        });
                            }
                            return invoke(original, method, args);
                        });
            }
            @Override public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("Explicit credentials are forbidden in the diagnostic child");
            }
        };
    }

    private static boolean isBinding(Method method, Object[] args) {
        return args != null && args.length >= 2 && args[0] instanceof Integer && Set.of(
                "setObject", "setLong", "setInt", "setString", "setNull", "setBoolean", "setDate",
                "setTime", "setTimestamp", "setBigDecimal", "setShort", "setByte", "setFloat",
                "setDouble", "setBytes").contains(method.getName());
    }
    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException ex) { throw ex.getCause(); }
    }
    private static String expand(Captured captured) {
        MapSqlParameterSource source = new MapSqlParameterSource();
        captured.parameters.forEach(source::addValue);
        return NamedParameterUtils.substituteNamedParameters(
                NamedParameterUtils.parseSqlStatement(captured.template), source);
    }
    private static String r3FailureSql() throws Exception {
        String message = JSON.readTree(Files.readString(Path.of(EVIDENCE)))
                .path("failures").get(0).path("error").asText();
        int start = message.indexOf("SQL [");
        int end = message.indexOf("]; SQL state", start);
        assertTrue(start >= 0 && end > start, "R3 original SQL not found");
        return message.substring(start + 5, end);
    }
    private static String canonical(String sql) { return sql.replaceAll("\\s+", " ").trim(); }
    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static Map<String, Object> printableParameters(Map<String, Object> parameters) {
        Map<String, Object> printable = new LinkedHashMap<>();
        parameters.forEach((name, value) -> printable.put(name,
                value instanceof Set<?> set ? set.stream().map(Object::toString).sorted().toList() : value));
        return printable;
    }
}
