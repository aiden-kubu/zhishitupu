package com.knowledgegraph.assistant.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.knowledgegraph.assistant.host.AssistantReadAcceptanceConfig.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssistantReadAcceptanceDataSourceTest {
    static final class MutableClock extends Clock {
        Instant now;MutableClock(Instant now){this.now=now;}
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
    static final class Fixture {
        final AssistantReadAcceptanceTest.Bundle bundle;
        final AssistantReadAcceptanceConfig config;
        final AssistantReadAcceptanceEvidence proof;
        final AssistantReadAcceptanceDataSource.Connector connector=mock(AssistantReadAcceptanceDataSource.Connector.class);
        final Connection raw=mock(Connection.class);
        final DatabaseMetaData metadata=mock(DatabaseMetaData.class);
        final Statement session=mock(Statement.class);
        final ResultSet identity=mock(ResultSet.class);
        final PreparedStatement prepared=mock(PreparedStatement.class);
        final MutableClock clock;
        final AssistantReadAcceptanceDataSource source;
        Fixture()throws Exception {
            bundle=new AssistantReadAcceptanceTest.Bundle();config=bundle.config();proof=bundle.proof(config);clock=new MutableClock(bundle.now);
            when(connector.connect(anyString(),any(Properties.class))).thenReturn(raw);when(raw.getMetaData()).thenReturn(metadata);
            when(metadata.getDatabaseProductName()).thenReturn("MySQL");when(metadata.getDatabaseProductVersion()).thenReturn(VERSION);
            when(metadata.getUserName()).thenReturn("assistant_reader@localhost");when(raw.getCatalog()).thenReturn(DATABASE);
            when(raw.createStatement()).thenReturn(session);when(raw.prepareStatement(anyString())).thenReturn(prepared);
            when(session.executeQuery(AssistantReadAcceptanceDataSource.INSTANCE_UUID)).thenReturn(identity);
            when(identity.next()).thenReturn(true,false);when(identity.getString(1)).thenReturn(UUID);
            source=new AssistantReadAcceptanceDataSource(config,proof,connector,clock);
        }
    }
    @Test void constructionIsLazyAndUsesOnlyFixedConnectionProperties()throws Exception {
        Fixture f=new Fixture();verifyNoInteractions(f.connector,f.raw);assertEquals(3,f.source.getLoginTimeout());
        try(Connection c=f.source.getConnection()) {
            var capture=org.mockito.ArgumentCaptor.forClass(Properties.class);verify(f.connector).connect(eq(AssistantReadAcceptanceDataSource.URL),capture.capture());
            Properties properties=capture.getValue();assertEquals("3000",properties.getProperty("connectTimeout"));assertEquals("4000",properties.getProperty("socketTimeout"));
            assertEquals("false",properties.getProperty("autoReconnect"));assertEquals("false",properties.getProperty("autoReconnectForPools"));assertEquals("false",properties.getProperty("allowMultiQueries"));
            assertFalse(AssistantReadAcceptanceDataSource.URL.contains(AssistantReadAcceptanceTest.Bundle.SYNTHETIC_PASSWORD));assertFalse(AssistantReadAcceptanceDataSource.URL.contains("?"));
            verify(f.raw).setReadOnly(true);verify(f.raw).setNetworkTimeout(any(),eq(4000));verify(f.session).setQueryTimeout(2);
            verify(f.session).execute("SET SESSION max_execution_time = 1500");verify(f.session).close();
            verify(f.session).executeQuery("SELECT @@server_uuid");verify(f.identity).close();
            assertFalse(c.toString().contains(AssistantReadAcceptanceTest.Bundle.SYNTHETIC_PASSWORD));
        }
        verify(f.raw).close();
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,10,Integer.MAX_VALUE})
    void threeSecondOuterTimeoutCannotWidenTwoSecondBudget(int requested)throws Exception {
        Fixture f=new Fixture();var outer=new AssistantReadDataSource(f.proof.reader(f.config,f.source));
        try(Connection c=outer.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM documents WHERE id=?")) {
            clearInvocations(f.prepared);p.setQueryTimeout(requested);verify(f.prepared).setQueryTimeout(requested==0?2:Math.min(requested,2));
            verify(f.prepared,never()).setQueryTimeout(3);assertThrows(SQLException.class,()->p.setQueryTimeout(-1));
            assertThrows(SQLException.class,()->p.unwrap(PreparedStatement.class));assertThrows(SQLException.class,()->p.executeUpdate());
        }
    }
    @Test void outerThreeSecondInitializationIsClampedAndForbiddenSqlStillRejected()throws Exception {
        Fixture f=new Fixture();var outer=new AssistantReadDataSource(f.proof.reader(f.config,f.source));
        try(Connection c=outer.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM documents WHERE id=?")) {
            verify(f.prepared,times(2)).setQueryTimeout(2);verify(f.prepared,never()).setQueryTimeout(3);
            assertThrows(SQLException.class,()->c.prepareStatement("UPDATE documents SET title='x'"));
            assertThrows(SQLException.class,()->c.prepareStatement("SELECT * FROM system_settings"));assertThrows(SQLException.class,()->c.createStatement());
            assertThrows(SQLException.class,()->c.getMetaData().getConnection());
        }
    }
    @ParameterizedTest @ValueSource(ints={0,1,4000,4001,10000})
    void socketTimeoutCannotBeDisabledOrWidened(int requested)throws Exception {
        Fixture f=new Fixture();try(Connection c=f.source.getConnection()) {
            clearInvocations(f.raw);c.setNetworkTimeout(Runnable::run,requested);verify(f.raw).setNetworkTimeout(any(),eq(requested==0?4000:Math.min(requested,4000)));
            assertThrows(SQLException.class,()->c.setNetworkTimeout(Runnable::run,-1));
        }
    }
    @ParameterizedTest @ValueSource(strings={"version","uuid","uuidRead","user","catalog","product","session","network","readOnly"})
    void identityOrInitializationFailureClosesConnectionAndLatchesClosed(String kind)throws Exception {
        Fixture f=new Fixture();
        switch(kind) {
            case "version"->when(f.metadata.getDatabaseProductVersion()).thenReturn("8.4.12");case "user"->when(f.metadata.getUserName()).thenReturn("root@localhost");
            case "uuid"->when(f.identity.getString(1)).thenReturn(java.util.UUID.randomUUID().toString());
            case "uuidRead"->when(f.session.executeQuery(AssistantReadAcceptanceDataSource.INSTANCE_UUID)).thenThrow(new SQLException("sensitive driver message"));
            case "catalog"->when(f.raw.getCatalog()).thenReturn("production");case "product"->when(f.metadata.getDatabaseProductName()).thenReturn("OtherDB");
            case "session"->when(f.session.execute(anyString())).thenThrow(new SQLException(AssistantReadAcceptanceTest.Bundle.SYNTHETIC_PASSWORD));
            case "network"->doThrow(new SQLException("sensitive driver message")).when(f.raw).setNetworkTimeout(any(),anyInt());
            case "readOnly"->doThrow(new SQLException("sensitive driver message")).when(f.raw).setReadOnly(true);default->fail();
        }
        SQLException error=assertThrows(SQLException.class,f.source::getConnection);assertNull(error.getCause());assertFalse(error.toString().contains(AssistantReadAcceptanceTest.Bundle.SYNTHETIC_PASSWORD));
        verify(f.raw).close();if(Set.of("session","uuid","uuidRead").contains(kind))verify(f.session).close();
        if(kind.equals("uuid"))verify(f.identity).close();
        assertFalse(f.source.authorityValid());assertThrows(SQLException.class,f.source::getConnection);verify(f.connector,times(1)).connect(anyString(),any());
    }
    @Test void failedConnectCannotLeakSecretsOrRetryWithAnotherAccount()throws Exception {
        Fixture f=new Fixture();when(f.connector.connect(anyString(),any())).thenThrow(new SQLException("password="+AssistantReadAcceptanceTest.Bundle.SYNTHETIC_PASSWORD));
        SQLException error=assertThrows(SQLException.class,f.source::getConnection);assertNull(error.getCause());assertFalse(error.toString().contains(AssistantReadAcceptanceTest.Bundle.SYNTHETIC_PASSWORD));
        assertThrows(SQLException.class,f.source::getConnection);verify(f.connector,times(1)).connect(anyString(),any());verifyNoInteractions(f.raw);
        assertThrows(SQLException.class,()->f.source.getConnection("root","synthetic"));assertThrows(SQLException.class,()->f.source.setLoginTimeout(4));
        assertThrows(SQLException.class,()->f.source.setLoginTimeout(0));assertThrows(SQLException.class,()->f.source.setLogWriter(new PrintWriter(new StringWriter())));
        assertNull(f.source.getLogWriter());assertThrows(SQLException.class,()->f.source.unwrap(javax.sql.DataSource.class));assertFalse(f.source.isWrapperFor(javax.sql.DataSource.class));
    }
    @Test void expiryBeforeConnectionAndBeforeExecutionIsRejected()throws Exception {
        Fixture f=new Fixture();try(Connection c=f.source.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM documents")) {
            f.clock.now=f.bundle.now.plusSeconds(301);assertThrows(SQLException.class,p::executeQuery);verify(f.prepared,never()).executeQuery();
            assertThrows(SQLException.class,()->c.prepareStatement("SELECT id FROM documents"));assertThrows(SQLException.class,f.source::getConnection);
            verify(f.connector,times(1)).connect(anyString(),any());
        }
    }
    @Test void expiryDuringAcquisitionClosesUnpublishedConnection()throws Exception {
        Fixture f=new Fixture();when(f.connector.connect(anyString(),any())).thenAnswer(invocation->{f.clock.now=f.bundle.now.plusSeconds(301);return f.raw;});
        assertThrows(SQLException.class,f.source::getConnection);verify(f.raw).close();verify(f.raw,never()).getMetaData();
    }
    @Test void timeoutInitializationFailureClosesPreparedStatement()throws Exception {
        Fixture f=new Fixture();doThrow(new SQLException("sensitive driver message")).when(f.prepared).setQueryTimeout(2);
        try(Connection c=f.source.getConnection()) {assertThrows(SQLException.class,()->c.prepareStatement("SELECT id FROM documents"));verify(f.prepared).close();}
        verify(f.raw).close();
    }
    private static SQLException privateDriverFailure() {
        SQLException original=new SQLException("SYNTHETIC private password; SELECT private_sql FROM private_table","HY000",3024,
                new IllegalStateException("SYNTHETIC private driver cause"));
        original.setNextException(new SQLException("SYNTHETIC private chained exception"));
        original.addSuppressed(new SQLException("SYNTHETIC private suppressed exception"));return original;
    }
    private static void assertSafeDriverIdentity(SQLException error,SQLException original) {
        assertNotSame(original,error);assertEquals("HY000",error.getSQLState());assertEquals(3024,error.getErrorCode());
        assertEquals("Acceptance reader unavailable or operation rejected",error.getMessage());
        assertNull(error.getCause());assertNull(error.getNextException());assertEquals(0,error.getSuppressed().length);
        assertFalse(error.toString().contains("SYNTHETIC"));assertFalse(error.toString().contains("SELECT"));
    }
    @ParameterizedTest @ValueSource(strings={"connect","metadata","session","uuidRead"})
    void connectionFailurePreservesOnlyRealSqlStateAndVendorCode(String kind)throws Exception {
        Fixture f=new Fixture();SQLException original=privateDriverFailure();
        switch(kind) {
            case "connect"->when(f.connector.connect(anyString(),any())).thenThrow(original);
            case "metadata"->when(f.raw.getMetaData()).thenThrow(original);
            case "session"->when(f.session.execute(anyString())).thenThrow(original);
            case "uuidRead"->when(f.session.executeQuery(AssistantReadAcceptanceDataSource.INSTANCE_UUID)).thenThrow(original);
            default->fail();
        }
        var outer=new AssistantReadDataSource(f.proof.reader(f.config,f.source));
        assertSafeDriverIdentity(assertThrows(SQLException.class,outer::getConnection),original);
        assertFalse(f.source.authorityValid());assertThrows(SQLException.class,outer::getConnection);verify(f.connector,times(1)).connect(anyString(),any());
        if(kind.equals("connect"))verifyNoInteractions(f.raw);else verify(f.raw).close();
        if(Set.of("session","uuidRead").contains(kind))verify(f.session).close();
    }
    @Test void statementExecutionPreservesRealErrorIdentityWithoutSecretMessageOrCause()throws Exception {
        Fixture f=new Fixture();SQLException original=privateDriverFailure();when(f.prepared.executeQuery()).thenThrow(original);
        var outer=new AssistantReadDataSource(f.proof.reader(f.config,f.source));
        try(Connection c=outer.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM documents")) {
            assertSafeDriverIdentity(assertThrows(SQLException.class,p::executeQuery),original);
            verify(f.prepared,times(2)).setQueryTimeout(2);verify(f.prepared,never()).setQueryTimeout(3);
        }
        verify(f.prepared).close();verify(f.raw).close();
    }
    @Test void statementBudgetFailurePreservesErrorIdentityAndReleasesStatement()throws Exception {
        Fixture f=new Fixture();SQLException original=privateDriverFailure();doThrow(original).when(f.prepared).setQueryTimeout(2);
        try(Connection c=f.source.getConnection()) {
            assertSafeDriverIdentity(assertThrows(SQLException.class,()->c.prepareStatement("SELECT id FROM documents")),original);verify(f.prepared).close();
        }
        verify(f.raw).close();
    }
}
