package com.knowledgegraph.assistant.host;

import com.knowledgegraph.assistant.host.demo.AssistantWebsiteDemoConfig;
import java.nio.file.Path;
import java.time.Instant;

/** Website composition only; no I2 proof fabrication and no business DataSource lookup. */
public final class AssistantWebsiteDemoFactory {
    private AssistantWebsiteDemoFactory(){}
    public static AssistantWebsiteDemoConfig load(Path file) {
        try {return new AssistantWebsiteDemoConfig(AssistantReadAcceptanceConfig.JSON.readTree(
                AssistantReadAcceptanceConfig.read(AssistantReadAcceptanceConfig.absolute(file.toString()),262144,true)));}
        catch(Exception rejected){throw AssistantWebsiteDemoConfig.rejected();}
    }
    public static AssistantReadHostService host(AssistantWebsiteDemoConfig config) {
        return host(config,new AssistantWebsiteDemoReadDataSource(config));
    }
    /** Test-only package seam; public production path always constructs its independent JDBC adapter. */
    static AssistantReadHostService host(AssistantWebsiteDemoConfig config,AssistantWebsiteDemoReadDataSource reader) {
        return AssistantReadHostService.website(()->{
            String fingerprint=reader.verify(); // actual JDBC identity + SHOW GRANTS, before creating VerifiedReader
            return new AssistantReadWiring.VerifiedReader(reader,config.readerAccount(),config.grant(),fingerprint,Instant.MAX,AssistantWebsiteDemoReadDataSource.TABLES);
        },()->new AssistantReadAccessContext(config.owner(),config.grant(),scope->reader.verifiedPermission()));
    }
}
