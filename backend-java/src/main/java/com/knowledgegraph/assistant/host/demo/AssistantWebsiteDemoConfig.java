package com.knowledgegraph.assistant.host.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import java.util.*;

/** Immutable restricted server configuration. No request binding and no secret-bearing toString. */
public final class AssistantWebsiteDemoConfig {
    private final String owner,password,reader,readerPassword;
    private final Scope grant;
    private final int sessionSeconds;
    public AssistantWebsiteDemoConfig(JsonNode n) {
        fields(n,"schemaVersion","owner","accessPassword","sessionSeconds","reader","grant");
        if(!"08-website-demo-v1".equals(text(n,"schemaVersion")))throw rejected();
        owner=text(n,"owner");com.knowledgegraph.assistant.contract.AssistantContracts.identifier(owner);
        password=text(n,"accessPassword");if(password.length()<16 || password.length()>256)throw rejected();
        if(!n.path("sessionSeconds").isInt())throw rejected();sessionSeconds=n.path("sessionSeconds").intValue();
        if(sessionSeconds<60 || sessionSeconds>1800)throw rejected();
        JsonNode r=n.path("reader");fields(r,"address","port","database","account","password");
        if(!"127.0.0.1".equals(text(r,"address")) || !r.path("port").isInt() || r.path("port").intValue()!=3306 || !"tupu_kg".equals(text(r,"database")))throw rejected();
        reader=text(r,"account");readerPassword=text(r,"password");
        if(!reader.matches("[a-zA-Z0-9_]*reader[a-zA-Z0-9_]*") || reader.toLowerCase(Locale.ROOT).matches(".*(root|writer|admin).*"))throw rejected();
        JsonNode g=n.path("grant");fields(g,"mode","libraryIds","documentIds","nodeIds");
        if(!"selected".equals(text(g,"mode")))throw rejected();
        Set<Long> libraries=ids(g.path("libraryIds")),documents=ids(g.path("documentIds")),nodes=ids(g.path("nodeIds"));
        if(libraries.isEmpty() || !nodes.isEmpty())throw rejected();grant=new Scope(ScopeMode.SELECTED,libraries,documents,nodes);
    }
    private static Set<Long> ids(JsonNode n) {
        if(!n.isArray() || n.size()>1000)throw rejected();Set<Long> ids=new LinkedHashSet<>();
        for(JsonNode v:n) {if(!v.isTextual() || !v.textValue().matches("[1-9][0-9]{0,18}"))throw rejected();
            long id=Long.parseLong(v.textValue());if(id<1 || !ids.add(id))throw rejected();}
        return Collections.unmodifiableSet(ids);
    }
    private static void fields(JsonNode n,String...keys) {
        if(!n.isObject() || n.size()!=keys.length)throw rejected();for(String k:keys)if(!n.has(k))throw rejected();
    }
    private static String text(JsonNode n,String key) {
        var v=n.path(key);if(!v.isTextual() || v.textValue().isBlank() || v.textValue().length()>1024 || v.textValue().chars().anyMatch(c->c<32))throw rejected();return v.textValue();
    }
    public String owner(){return owner;} public Scope grant(){return grant;}
    public String accessPassword(){return password;} public String readerAccount(){return reader;}
    public String readerPassword(){return readerPassword;} public int sessionSeconds(){return sessionSeconds;}
    public static IllegalArgumentException rejected(){return new IllegalArgumentException("Website demo configuration rejected");}
    @Override public String toString(){return "WebsiteDemoConfig[restricted]";}
}
