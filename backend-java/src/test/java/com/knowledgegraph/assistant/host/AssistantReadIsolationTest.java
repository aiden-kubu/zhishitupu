package com.knowledgegraph.assistant.host;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.GenericApplicationContext;
import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssistantReadIsolationTest {
    AssistantReadWiring.VerifiedReader proof(DataSource source) {
        return new AssistantReadWiring.VerifiedReader(source,"assistant_reader",new Scope(ScopeMode.SELECTED,Set.of(1L),Set.of(),Set.of()),
                "b".repeat(64),Instant.now().plusSeconds(100),Set.of("documents","knowledge_libraries"));
    }
    @Test void oldScanHasNoHostCandidates() {
        var scanner=new ClassPathScanningCandidateComponentProvider(true);
        assertTrue(scanner.findCandidateComponents("com.knowledgegraph.assistant.host").isEmpty());
    }
    @Test void isolatedInMemorySpringContextHasNoDataSourceOrOldServices() {
        try(var host=AssistantReadHostService.disabled()) {
            new ApplicationContextRunner().withUserConfiguration(AssistantReadHostApplication.class)
                    .withInitializer(c->AssistantReadHostApplication.install((GenericApplicationContext)c,host))
                    .run(c->{assertNull(c.getStartupFailure());assertEquals(0,c.getBeansOfType(DataSource.class).size());
                        assertEquals(0,c.getBeansOfType(SettingsService.class).size());assertEquals(1,c.getBeansOfType(AssistantReadHostController.class).size());
                        assertFalse(host.capabilities().path("databaseIdentityVerified").asBoolean());
                        assertEquals(1,c.getBeansWithAnnotation(org.springframework.web.bind.annotation.RestController.class).size());});
        }
    }
    @Test void trueH03ConstructionDoesNotReadSettingsOrOpenConnection() {
        DataSource dataSource=mock(DataSource.class);var properties=AssistantReadHostProperties.disabled();
        try(var io=new HostReadIo();var store=new AssistantRunStore(properties,Clock.systemUTC())) {
            var bindings=AssistantReadWiring.production(proof(dataSource),io,store,Clock.systemUTC());
            assertEquals(10,bindings.tools().size());assertTrue(bindings.tools().stream().allMatch(t->t.descriptor().effect()==ToolEffect.READ_ONLY));
            assertFalse(bindings.tools().stream().anyMatch(t->t.descriptor().name().contains("settings")));
            verifyNoInteractions(dataSource);
        }
    }
    @Test void readerMetadataMismatchClosesConnection()throws Exception {
        DataSource ds=mock(DataSource.class);Connection raw=mock(Connection.class);DatabaseMetaData meta=mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(raw);when(raw.getMetaData()).thenReturn(meta);when(meta.getUserName()).thenReturn("root@localhost");
        AssistantReadDataSource guarded=new AssistantReadDataSource(proof(ds));
        assertThrows(SQLException.class,guarded::getConnection);verify(raw).close();verify(raw,never()).setReadOnly(true);
        assertFalse(guarded.authorityValid());assertThrows(SQLException.class,guarded::getConnection);verify(ds,times(1)).getConnection();
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE documents SET status='x'","SELECT * FROM system_settings","SELECT * FROM documents; DELETE FROM documents",
            "SELECT * FROM documents FOR UPDATE","SELECT /* bypass */ * FROM documents","SELECT SLEEP(10) FROM documents"})
    void statementsOutsideReadGrantRejected(String sql)throws Exception {
        DataSource ds=mock(DataSource.class);Connection raw=mock(Connection.class);DatabaseMetaData meta=mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(raw);when(raw.getMetaData()).thenReturn(meta);when(meta.getUserName()).thenReturn("assistant_reader@localhost");
        try(Connection c=new AssistantReadDataSource(proof(ds)).getConnection()) {assertThrows(SQLException.class,()->c.prepareStatement(sql));verify(raw,never()).prepareStatement(anyString());}
    }
    @Test void approvedSelectGetsThreeSecondTimeoutAndCannotUnwrap()throws Exception {
        DataSource ds=mock(DataSource.class);Connection raw=mock(Connection.class);DatabaseMetaData meta=mock(DatabaseMetaData.class);PreparedStatement statement=mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(raw);when(raw.getMetaData()).thenReturn(meta);when(meta.getUserName()).thenReturn("assistant_reader@localhost");
        when(raw.prepareStatement(anyString())).thenReturn(statement);
        try(Connection c=new AssistantReadDataSource(proof(ds)).getConnection()) {
            PreparedStatement p=c.prepareStatement("SELECT\n id FROM documents WHERE id=?");verify(statement).setQueryTimeout(3);
            assertThrows(SQLException.class,()->c.unwrap(Connection.class));assertThrows(SQLException.class,()->c.setReadOnly(false));
            assertThrows(SQLException.class,()->p.executeUpdate());assertThrows(SQLException.class,()->p.execute("DELETE FROM documents"));
        }
    }
    @Test void timedOutIoKeepsSlotUntilActualOperationEnds()throws Exception {
        try(var io=new HostReadIo()) {
            CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);
            ExecutorService callers=Executors.newFixedThreadPool(2);
            try {
                Callable<Void> call=()->{assertThrows(ApiException.class,()->io.read(Instant.now().plusMillis(150),()->false,()->{
                    entered.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}return 1;}));return null;};
                Future<Void> a=callers.submit(call),b=callers.submit(call);assertTrue(entered.await(2,TimeUnit.SECONDS));a.get(2,TimeUnit.SECONDS);b.get(2,TimeUnit.SECONDS);
                assertEquals(429,assertThrows(ApiException.class,()->io.read(Instant.now().plusSeconds(1),()->false,()->1)).getHttpStatus());release.countDown();
            }finally{release.countDown();callers.shutdownNow();}
        }
    }
}
