package com.knowledgegraph.assistant.i2;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="KG_I2_AUTHORIZED", matches="8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2ReadOnlyPrivilegeTest extends I2MySqlSupport {
    @TestFactory Stream<DynamicTest> serverRejectsEveryWriteCategory() {
        // Valid zero-row DML; a successful no-op is a failure of authorization, not a pass.
        // DDL is confined to disposable canary objects, never a business table.
        return Stream.of(
                "INSERT INTO knowledge_nodes(canonical_name) SELECT 'I2 forbidden' WHERE FALSE",
                "UPDATE knowledge_nodes SET canonical_name=canonical_name WHERE id=-1",
                "DELETE FROM knowledge_nodes WHERE id=-1",
                "CREATE TABLE i2_forbidden_probe(id BIGINT)",
                "ALTER TABLE i2_privilege_canary ADD forbidden_column INT",
                "DROP TABLE i2_privilege_canary",
                "TRUNCATE TABLE i2_privilege_canary",
                "CREATE TEMPORARY TABLE i2_forbidden_temp(id BIGINT)"
        ).map(sql->DynamicTest.dynamicTest(sql,()->{
            try(var c=dataSource.getConnection();var s=c.createStatement()) {
                SQLException e=assertThrows(SQLException.class,()->s.execute(sql));
                assertTrue(List.of(1142,1044).contains(e.getErrorCode()),"Must be server privilege denial, not syntax/timeout/client read-only error: "+e.getErrorCode());
            }
        }));
    }
    @Test void minimumGrantsAndNoRole() {
        assertEquals("NONE",jdbc.sql("SELECT CURRENT_ROLE()").query(String.class).single());
        var grants=jdbc.sql("SHOW GRANTS").query(String.class).list();
        assertEquals(24,grants.size());
        for(String grant:grants) {
            assertTrue(grant.startsWith("GRANT USAGE ON *.*") || grant.startsWith("GRANT SELECT"));
            assertFalse(grant.contains("WITH GRANT OPTION")); assertFalse(grant.contains("ON `"+DB+"`.*"));
        }
    }
    @TestFactory Stream<DynamicTest> unrelatedDataAndColumnsDenied() {
        return Stream.of("SELECT * FROM mysql.user","SELECT * FROM chat_messages",
                "SELECT updated_at FROM system_settings","SELECT * FROM llm_profiles")
                .map(sql->DynamicTest.dynamicTest(sql,()->{
                    try(var c=dataSource.getConnection();var s=c.createStatement()) {
                        SQLException e=assertThrows(SQLException.class,()->s.execute(sql));
                        assertTrue(List.of(1142,1143).contains(e.getErrorCode()),"Wrong rejection: "+e.getErrorCode());
                    }
                }));
    }
}
