package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.ingestion.*;
import com.knowledgegraph.assistant.research.*;
import com.knowledgegraph.assistant.quality.*;
import com.knowledgegraph.assistant.source.*;
import com.knowledgegraph.assistant.tool.AssistantTool;
import com.knowledgegraph.settings.*;
import com.knowledgegraph.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import javax.sql.DataSource;
import java.time.*;
import java.security.SecureRandom;
import java.util.*;

/** Explicit private composition; none of the legacy objects below become Spring beans. */
final class AssistantReadWiring {
    static final Set<String> INGESTION=Set.of("ingestion.list_libraries","ingestion.list_documents","ingestion.get_document");
    static final Set<String> RESEARCH=Set.of("research.retrieve_chunks","research.read_source","research.search_nodes",
            "research.get_node","research.get_neighbors","research.find_paths");
    /** Must be supplied by a future approved reader verification batch, never flags or HTTP input.
     * The evidence reference is a trust boundary, not this code's claim to have checked GRANTS. */
    record VerifiedReader(DataSource assistantReadDataSource,String account,Scope grant,String verificationSha256,Instant validUntil,Set<String> selectTables) {
        VerifiedReader {
            Objects.requireNonNull(assistantReadDataSource);Objects.requireNonNull(grant);Objects.requireNonNull(validUntil);
            if(account==null || !account.matches("[a-zA-Z0-9_]*reader[a-zA-Z0-9_]*")
                    || account.toLowerCase(Locale.ROOT).matches(".*(root|writer|admin).*"))throw new IllegalArgumentException("Reader account required");
            if(verificationSha256==null || !verificationSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Reader verification reference required");
            selectTables=Set.copyOf(selectTables);
            if(selectTables.isEmpty() || selectTables.stream().anyMatch(t->!t.matches("[a-z_]+") || Set.of("system_settings","llm_profiles").contains(t)))
                throw new IllegalArgumentException("Explicit read table grant required");
        }
        boolean valid(Scope scope,Instant now) { return now.isBefore(validUntil) && scope.isWithin(grant); }
    }
    record Bindings(IngestionReadRepository ingestion,ResearchReadRepository research,List<AssistantTool> tools,
                    java.util.function.Predicate<Scope> verified) {
        Bindings { tools=List.copyOf(tools); }
    }
    static Bindings production(VerifiedReader identity,HostReadIo io,AssistantRunStore store,Clock clock) {
        if(!identity.valid(identity.grant(),clock.instant()))throw HostJson.error(503,"READ_IDENTITY_UNVERIFIED");
        AssistantReadDataSource reader=new AssistantReadDataSource(identity);
        JdbcTemplate template=new JdbcTemplate(reader);template.setQueryTimeout(3);
        JdbcClient assistantReadJdbcClient=JdbcClient.create(template);
        byte[] seed=new byte[32];new SecureRandom().nextBytes(seed);
        SecretCipher internalCipher=new SecretCipher(Base64.getEncoder().encodeToString(seed));Arrays.fill(seed,(byte)0);
        SettingsService internalSettings=new SettingsService(assistantReadJdbcClient,internalCipher,new AppProperties());
        IngestionReadRepository ingestion=new JdbcIngestionReadRepository(assistantReadJdbcClient,internalSettings);
        SourceLocator sources=new JdbcSourceLocator(assistantReadJdbcClient);
        ResearchReadRepository research=new JdbcResearchReadRepository(assistantReadJdbcClient);
        QualityReadRepository quality=new JdbcQualityReadRepository(assistantReadJdbcClient,new ObjectMapper());
        return domains(ingestion,research,quality,sources,io,store,clock,s->reader.authorityValid()&&identity.valid(s,clock.instant()));
    }
    /** Package-private composition seam for isolated tests using test-only repositories. */
    static Bindings domains(IngestionReadRepository ingestion,ResearchReadRepository research,QualityReadRepository quality,
                            SourceLocator source,HostReadIo io,AssistantRunStore store,Clock clock,java.util.function.Predicate<Scope> verified) {
        List<AssistantTool> selected=new ArrayList<>();
        selected.addAll(new IngestionAgentToolProvider(ingestion,source,new ObjectMapper()).tools().stream()
                .filter(t->INGESTION.contains(t.descriptor().name())).toList());
        selected.addAll(new ResearchAgentToolProvider(research,source).tools().stream().filter(t->RESEARCH.contains(t.descriptor().name())).toList());
        selected.addAll(new QualityAgentToolProvider(quality,new QualityEvidenceVerifier(quality,source),new HostQualityArtifactReader(store),
                new ObjectMapper(),clock).tools().stream().filter(t->t.descriptor().name().equals("quality.verify_artifact_claims")).toList());
        Set<String> names=new HashSet<>();List<AssistantTool> bounded=new ArrayList<>();
        for(AssistantTool tool:selected) {
            if(tool.descriptor().effect()!=ToolEffect.READ_ONLY || !names.add(tool.descriptor().name()))throw new IllegalStateException("Read tool whitelist mismatch");
            bounded.add(new AssistantTool() {
                public ToolDescriptor descriptor(){return tool.descriptor();}
                public void validateArguments(com.fasterxml.jackson.databind.JsonNode args,Scope scope){tool.validateArguments(args,scope);}
                public ToolResult execute(Context c,com.fasterxml.jackson.databind.JsonNode args) {
                    if(!verified.test(c.scope()))return ToolResult.error(c.scope(),"READ_IDENTITY_UNVERIFIED");
                    Instant deadline=c.deadline().isBefore(Instant.now().plusSeconds(10))?c.deadline():Instant.now().plusSeconds(10);
                    return io.read(deadline,c.cancellationRequested(),()->tool.execute(c,args));
                }
            });
        }
        if(names.size()!=10 || !names.containsAll(INGESTION) || !names.containsAll(RESEARCH))throw new IllegalStateException("Missing read tools");
        return new Bindings(ingestion,research,bounded,verified);
    }
}
