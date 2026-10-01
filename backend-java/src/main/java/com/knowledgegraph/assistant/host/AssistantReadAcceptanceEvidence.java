package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import javax.sql.DataSource;
import static com.knowledgegraph.assistant.host.AssistantReadAcceptanceConfig.*;

/** Immutable reviewed evidence, not a live GRANTS verifier. Synthetic offline fixtures confer no real authority. */
public final class AssistantReadAcceptanceEvidence {
    private final Instant verifiedAt, validUntil;
    private final String sha256;
    private AssistantReadAcceptanceEvidence(Instant verifiedAt,Instant validUntil,String sha256) {
        this.verifiedAt=verifiedAt;this.validUntil=validUntil;this.sha256=sha256;
    }
    public static AssistantReadAcceptanceEvidence load(AssistantReadAcceptanceConfig config,Clock clock) {
        try {
            byte[] raw=read(config.evidencePath(),262144,true);
            if(!sha256(raw).equals(config.evidenceSha256()))throw invalid();
            JsonNode n=JSON.readTree(raw);
            fields(n,"schemaVersion","launchId","instance","readerAccount","grant","selectTables","verification","review","rawEvidence");
            equal(n,"schemaVersion","08-read-evidence-v1");equal(n,"launchId",config.launchId());instance(n.path("instance"));
            equal(n,"readerAccount",config.account());
            if(!scope(n.path("grant")).equals(config.grant()) || !tables(n.path("selectTables")).equals(config.tables()))throw invalid();
            JsonNode v=n.path("verification");fields(v,"executor","result","identityMatch","selectOnly","noOtherPrivileges","verifiedAt","validUntil");
            equal(v,"executor","06");equal(v,"result","PASS");
            for(String flag:new String[]{"identityMatch","selectOnly","noOtherPrivileges"})if(!v.path(flag).isBoolean() || !v.path(flag).booleanValue())throw invalid();
            Instant at=Instant.parse(text(v,"verifiedAt",40)),until=Instant.parse(text(v,"validUntil",40)),now=clock.instant();
            if(at.isAfter(now) || !at.isBefore(until) || !now.isBefore(until))throw invalid();
            JsonNode review=n.path("review");fields(review,"reviewer","result","reviewedAt","reference");
            equal(review,"reviewer","controller");equal(review,"result","APPROVED");text(review,"reference",128);
            Instant reviewed=Instant.parse(text(review,"reviewedAt",40));if(reviewed.isBefore(at) || reviewed.isAfter(now) || !reviewed.isBefore(until))throw invalid();
            JsonNode proofs=n.path("rawEvidence");fields(proofs,"instance","grants");
            for(String kind:new String[]{"instance","grants"}) {
                JsonNode proof=proofs.path(kind);fields(proof,"file","sha256");
                byte[] original=read(config.bundleFile(text(proof,"file",128)),1048576,true);
                if(original.length==0 || !sha256(original).equals(hash(text(proof,"sha256",64))))throw invalid();
            }
            return new AssistantReadAcceptanceEvidence(at,until,config.evidenceSha256());
        }catch(Exception ex){throw invalid();}
    }
    boolean valid(Instant now){return !now.isBefore(verifiedAt) && now.isBefore(validUntil);}
    AssistantReadWiring.VerifiedReader reader(AssistantReadAcceptanceConfig config,DataSource source) {
        return new AssistantReadWiring.VerifiedReader(source,config.account(),config.grant(),sha256,validUntil,config.tables());
    }
    @Override public String toString(){return "AssistantReadAcceptanceEvidence[reviewed file reference; not live verification]";}
}
