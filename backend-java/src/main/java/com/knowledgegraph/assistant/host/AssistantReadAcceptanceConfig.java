package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.util.*;

/** Server-side, digest-pinned acceptance configuration. Never a Spring property binding or HTTP DTO. */
public final class AssistantReadAcceptanceConfig {
    static final String VERSION="8.4.11", UUID="8c6cfcb3-ba49-11f1-bf64-005056c00001";
    static final String ADDRESS="127.0.0.1", DATABASE="kg_assistant_i2_20260927_r01";
    static final int DATABASE_PORT=33078;
    static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Path directory;
    private final String launchId, owner, account, password, evidenceFile, evidenceSha256;
    private final int port;
    private final Scope grant;
    private final Set<String> tables;
    private final JsonNode frontend;

    private AssistantReadAcceptanceConfig(Path directory,JsonNode n) {
        fields(n,"schemaVersion","enabled","launchId","owner","listenAddress","listenPort","instance","reader","grant","selectTables","evidence","frontend");
        equal(n,"schemaVersion","08-read-acceptance-v1");
        if(!n.path("enabled").isBoolean() || !n.path("enabled").booleanValue())throw invalid();
        launchId=text(n,"launchId",36); java.util.UUID.fromString(launchId);
        owner=text(n,"owner",128); com.knowledgegraph.assistant.contract.AssistantContracts.identifier(owner);
        equal(n,"listenAddress",ADDRESS);port=integer(n,"listenPort",1024,65535);
        instance(n.path("instance"));
        JsonNode reader=n.path("reader");fields(reader,"account","password");account=text(reader,"account",64);
        if(!account.matches("[a-zA-Z0-9_]*reader[a-zA-Z0-9_]*") || account.toLowerCase(Locale.ROOT).matches(".*(root|writer|admin).*"))throw invalid();
        password=text(reader,"password",1024);
        grant=scope(n.path("grant"));tables=tables(n.path("selectTables"));
        JsonNode evidence=n.path("evidence");fields(evidence,"file","sha256");
        evidenceFile=fileName(text(evidence,"file",128));evidenceSha256=hash(text(evidence,"sha256",64));
        frontend=n.path("frontend").deepCopy();
        if(!frontend.isNull()) {
            fields(frontend,"directory","manifestFile","manifestSha256");
            absolute(text(frontend,"directory",512));fileName(text(frontend,"manifestFile",128));hash(text(frontend,"manifestSha256",64));
        }
        this.directory=directory;
    }

    /** The expected CONFIG digest must be received out of band from the controller's reviewed 06 batch.
     * A digest read from the same untrusted file, HTTP request or environment is not a trust anchor. */
    public static AssistantReadAcceptanceConfig load(Path file,String controllerApprovedConfigSha256) {
        try {
            file=absolute(file.toString());
            byte[] raw=read(file,262144,true);
            if(!sha256(raw).equals(hash(controllerApprovedConfigSha256)))throw invalid();
            return new AssistantReadAcceptanceConfig(file.getParent(),JSON.readTree(raw));
        }catch(Exception ex){throw invalid();}
    }

    static void instance(JsonNode n) {
        fields(n,"version","uuid","address","port","database");
        equal(n,"version",VERSION);equal(n,"uuid",UUID);equal(n,"address",ADDRESS);
        if(integer(n,"port",1,65535)!=DATABASE_PORT)throw invalid();equal(n,"database",DATABASE);
    }
    static Scope scope(JsonNode n) {
        fields(n,"mode","libraryIds","documentIds","nodeIds");equal(n,"mode","selected");
        Set<Long> libraries=ids(n.path("libraryIds")),documents=ids(n.path("documentIds")),nodes=ids(n.path("nodeIds"));
        if(libraries.isEmpty() || !nodes.isEmpty())throw invalid();
        return new Scope(ScopeMode.SELECTED,libraries,documents,nodes);
    }
    private static Set<Long> ids(JsonNode n) {
        if(!n.isArray() || n.size()>1000)throw invalid();Set<Long> values=new LinkedHashSet<>();
        for(JsonNode id:n) {
            if(!id.isTextual() || !id.textValue().matches("[1-9][0-9]{0,18}"))throw invalid();
            long value=Long.parseLong(id.textValue());if(value<=0 || !values.add(value))throw invalid();
        }
        return Collections.unmodifiableSet(values);
    }
    static Set<String> tables(JsonNode n) {
        if(!n.isArray() || n.isEmpty() || n.size()>64)throw invalid();Set<String> result=new LinkedHashSet<>();
        for(JsonNode t:n) {
            if(!t.isTextual() || !t.textValue().matches("[a-z_]{1,64}")
                    || Set.of("system_settings","llm_profiles").contains(t.textValue()) || !result.add(t.textValue()))throw invalid();
        }
        return Collections.unmodifiableSet(result);
    }
    static void fields(JsonNode n,String... keys) {
        if(!n.isObject() || n.size()!=keys.length)throw invalid();
        for(String key:keys)if(!n.has(key))throw invalid();
    }
    static String text(JsonNode n,String key,int max) {
        JsonNode value=n.path(key);if(!value.isTextual() || value.textValue().isEmpty() || value.textValue().length()>max
                || value.textValue().chars().anyMatch(c->c<32))throw invalid();return value.textValue();
    }
    static int integer(JsonNode n,String key,int min,int max) {
        JsonNode value=n.path(key);if(!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<min || value.intValue()>max)throw invalid();return value.intValue();
    }
    static void equal(JsonNode n,String key,String expected) {if(!expected.equals(text(n,key,256)))throw invalid();}
    static String hash(String s) {if(s==null || !s.matches("[0-9a-f]{64}"))throw invalid();return s;}
    static String fileName(String s) {if(!s.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,127}") || s.contains(".."))throw invalid();return s;}
    static Path absolute(String s) {Path p=Path.of(s);if(!p.isAbsolute() || !p.equals(p.normalize()))throw invalid();return p;}
    static String sha256(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception ex){throw invalid();}
    }
    /** Bounded regular files only; reject links/junction redirection. Authority files must be owner-only. */
    static byte[] read(Path p,int max,boolean restricted)throws IOException {
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || !p.toRealPath().equals(p.toAbsolutePath().normalize()))throw invalid();
        if(restricted) {
            AclFileAttributeView acl=Files.getFileAttributeView(p,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
            if(acl!=null) {
                UserPrincipal owner=acl.getOwner();
                for(AclEntry entry:acl.getAcl())if(entry.type()==AclEntryType.ALLOW && !entry.principal().equals(owner)
                        && entry.permissions().stream().anyMatch(permission->Set.of(AclEntryPermission.READ_DATA,AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,AclEntryPermission.WRITE_ACL,AclEntryPermission.WRITE_OWNER).contains(permission)))throw invalid();
            }else {
                PosixFileAttributeView posix=Files.getFileAttributeView(p,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
                if(posix==null || posix.readAttributes().permissions().stream().anyMatch(permission->permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_")))throw invalid();
            }
        }
        try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)) {
            byte[] raw=in.readNBytes(max+1);if(raw.length>max)throw invalid();return raw;
        }
    }
    static IllegalArgumentException invalid(){return new IllegalArgumentException("Acceptance configuration or evidence rejected");}
    Path evidencePath(){return directory.resolve(evidenceFile);}
    Path bundleFile(String name){return directory.resolve(fileName(name));}
    String evidenceSha256(){return evidenceSha256;}
    String launchId(){return launchId;}
    String owner(){return owner;}
    String account(){return account;}
    String password(){return password;}
    int port(){return port;}
    Scope grant(){return grant;}
    Set<String> tables(){return tables;}
    JsonNode frontend(){return frontend.deepCopy();}
    @Override public String toString(){return "AssistantReadAcceptanceConfig[restricted]";}
}
