package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.runtime.*;
import com.knowledgegraph.assistant.tool.*;
import java.time.*;
import java.util.*;
import java.util.function.*;

/** Explicit host owner; no component scanning, credentials, model or legacy service lookup. */
public final class AssistantReadHostService implements AutoCloseable {
    @FunctionalInterface interface Factory {
        AssistantReadWiring.Bindings create(HostReadIo io,AssistantRunStore store,Clock clock);
    }
    private final AssistantReadHostProperties properties;
    private final Supplier<AssistantReadAccessContext> access;
    private final Clock clock;
    private final HostReadIo io=new HostReadIo();
    final AssistantRunStore store;
    private final String instance=UUID.randomUUID().toString();
    private final AssistantObservationProjection projection=new AssistantObservationProjection(instance);
    private final AssistantReadWiring.Bindings bindings;
    private final ToolRegistry registry;
    private final AssistantRuntime runtime;

    public static AssistantReadHostService disabled() {
        return new AssistantReadHostService(AssistantReadHostProperties.disabled(),()->null,Clock.systemUTC(),null);
    }
    /** Future approved server wiring must supply verified reader evidence separately from flags. */
    static AssistantReadHostService production(AssistantReadHostProperties p,Supplier<AssistantReadAccessContext> access,
                                               AssistantReadWiring.VerifiedReader reader) {
        return new AssistantReadHostService(p,access,Clock.systemUTC(),reader==null?null:(io,store,clock)->AssistantReadWiring.production(reader,io,store,clock));
    }
    AssistantReadHostService(AssistantReadHostProperties p,Supplier<AssistantReadAccessContext> access,Clock clock,Factory factory) {
        this.properties=Objects.requireNonNull(p);this.access=Objects.requireNonNull(access);this.clock=clock;
        store=new AssistantRunStore(p,clock);
        // Disabled or model-enabled gates do not even invoke the private reader factory.
        bindings=p.enabled()&&p.databaseEnabled()&&!p.modelEnabled()&&factory!=null?factory.create(io,store,clock):null;
        if(bindings==null) {registry=null;runtime=null;return;}
        List<AssistantTool> tools=bindings.tools().stream().map(tool->(AssistantTool)new AssistantTool() {
            public ToolDescriptor descriptor(){return tool.descriptor();}
            public void validateArguments(JsonNode a,Scope s){tool.validateArguments(a,s);}
            public ToolResult execute(Context c,JsonNode a)throws Exception {
                String owner=store.ownerOf(c.taskId());
                check(owner,c.scope());
                BooleanSupplier revoked=()->c.cancellationRequested().getAsBoolean() || !permitted(owner,c.scope());
                ToolResult result=tool.execute(new Context(c.runId(),c.taskId(),c.role(),c.scope(),c.deadline(),revoked),a);
                check(owner,c.scope());return result;
            }
        }).toList();
        registry=new ToolRegistry(tools);
        var decisions=new DeterministicTemplateDecisionProvider(store);
        runtime=new AssistantRuntime(Map.of(Role.INGESTION,decisions,Role.GRAPH_RESEARCH,decisions,Role.QUALITY_GOVERNANCE,decisions),tools,1);
    }
    private boolean permitted(String owner,Scope scope) {
        try {AssistantReadAccessContext a=access.get();return a!=null&&(owner==null||a.owner().equals(owner))&&a.allows(scope)
                &&bindings!=null&&bindings.verified().test(scope);}catch(RuntimeException ex){return false;}
    }
    private AssistantReadAccessContext context() {
        if(!properties.enabled())throw HostJson.error(503,"ASSISTANT_DISABLED");
        if(properties.modelEnabled()||!properties.databaseEnabled())throw HostJson.error(503,"CAPABILITY_UNAVAILABLE");
        AssistantReadAccessContext a=access.get();if(a==null)throw HostJson.error(401,"ACCESS_CONTEXT_UNAVAILABLE");
        if(bindings==null||!bindings.verified().test(a.grant()))throw HostJson.error(503,"READ_IDENTITY_UNVERIFIED");
        return a;
    }
    private void check(String owner,Scope scope) {
        var a=context();if(owner!=null&&!owner.equals(a.owner()))throw HostJson.error(404,"NOT_FOUND");
        if(!a.allows(scope))throw HostJson.error(403,"SCOPE_NOT_ALLOWED");
        if(!bindings.verified().test(scope))throw HostJson.error(503,"READ_IDENTITY_UNVERIFIED");
    }
    public ObjectNode capabilities() {
        boolean ready=false;try{var a=context();ready=a.allows(a.grant());}catch(RuntimeException ignored){}
        boolean switchedOff=!properties.enabled()||!properties.databaseEnabled();
        String status=ready?"available":switchedOff?"disabled":"unavailable";
        List<String> reasons=ready?List.of():List.of(!properties.enabled()?"ASSISTANT_DISABLED":!properties.databaseEnabled()?"DATABASE_DISABLED":
                properties.modelEnabled()?"REAL_MODEL_NOT_SUPPORTED":"READ_CONTEXT_OR_IDENTITY_UNAVAILABLE");
        ArrayNode items=HostJson.JSON.createArrayNode();
        for(String id:List.of("ingestion_read","research_graph","citation_check","source_reread","learning_preview","assessment_preview","formal_assessment","real_model")) {
            boolean supported=Set.of("ingestion_read","research_graph","citation_check","source_reread").contains(id);
            String unavailableReason=switch(id) {
                case "learning_preview","assessment_preview"->"NOT_IN_STAGE1_TEMPLATE";
                case "formal_assessment"->"FORMAL_SCORING_UNAVAILABLE";
                case "real_model"->"REAL_MODEL_NOT_CONNECTED";
                default->null;
            };
            items.add(HostJson.obj("id",id,"status",supported?status:"unavailable","reasonCodes",supported?reasons:List.of(unavailableReason),"readOnly",true,"formalAvailable",false));
        }
        return HostJson.obj("contractVersion","08-stage1-v1","hostInstanceId",instance,"observedAt",clock.instant().toString(),
                "executionMode","deterministic_tools","accessMode","isolated_local_single_owner","multiUserIsolation",false,
                "databaseIdentityVerified",ready,"capabilities",items,"persistence",HostJson.obj("mode","memory","recoverableAfterRestart",false,
                        "recoverableAfterRefresh",false,"terminalRetentionSeconds",1800,"maxRetainedRuns",32,"maxActiveRuns",8));
    }
    public ObjectNode templates() {
        JsonNode capability=capabilities().path("capabilities").get(0);
        return HostJson.obj("items",List.of(HostJson.obj("templateId",AssistantTemplatePlanner.TEMPLATE,"label","资料、定向图谱与引用检查",
                "status",capability.get("status"),"reasonCodes",capability.get("reasonCodes"),"minDocuments",1,"maxDocuments",2,
                "queryMinLength",2,"queryMaxLength",200,"goalPolicy","display_only_no_plan_change","executionMode","deterministic_tools",
                "stages",List.of("document_metadata","source_read","directed_graph","citation_check"))),"observedAt",clock.instant().toString());
    }
    private ToolResult invoke(String owner,Scope scope,String name,JsonNode args) {
        check(owner,scope);
        Role role=name.startsWith("ingestion.")?Role.INGESTION:Role.GRAPH_RESEARCH;
        AgentTask task=new AgentTask("http:"+UUID.randomUUID(),role,"approved read",scope,Set.of(),Set.of(name),"{}",
                new Limits(1,1,Duration.ofSeconds(10)),new CompletionPolicy(0,false,true,true));
        // HTTP operations have a trusted task context, but are not inserted into the runtime DAG.
        AssistantTool tool=bindings.tools().stream().filter(t->t.descriptor().name().equals(name)).findFirst().orElseThrow();
        tool.validateArguments(args,scope);
        ToolResult result;
        try {result=tool.execute(new AssistantTool.Context("http:"+instance,task.taskId(),role,scope,Instant.now().plusSeconds(10),()->!permitted(owner,scope)),args);}
        catch(com.knowledgegraph.common.ApiException ex){throw ex;}
        catch(Exception ex){throw HostJson.error(500,"INTERNAL_ERROR");}
        check(owner,scope);
        if(!result.effectiveScope().isWithin(scope))throw HostJson.error(500,"INTERNAL_ERROR");
        return result;
    }
    private JsonNode data(ToolResult result) {
        if(result.status()==ToolStatus.ERROR)throw HostJson.error(503,"SOURCE_CHANGED_OR_UNAVAILABLE");
        return HostJson.internal(result.dataJson(),32000);
    }
    public ObjectNode libraries(int page,int size) {
        var a=context();Scope scope=a.grant();JsonNode raw=data(invoke(a.owner(),scope,"ingestion.list_libraries",HostJson.obj("page",page,"pageSize",size)));
        ArrayNode items=HostJson.JSON.createArrayNode();for(JsonNode row:raw.path("items"))items.add(HostJson.obj("libraryId",Long.toString(row.path("id").longValue()),
                "name",row.get("name"),"status",row.get("status"),"selectable",true,"reasonCodes",List.of()));
        return page(raw,items,scope);
    }
    public ObjectNode documents(Set<Long> libraries,int page,int size) {
        var a=context();Scope scope=new Scope(ScopeMode.SELECTED,libraries,a.grant().documentIds(),Set.of());check(a.owner(),scope);
        JsonNode raw=data(invoke(a.owner(),scope,"ingestion.list_documents",HostJson.obj("page",page,"pageSize",size)));
        ArrayNode items=HostJson.JSON.createArrayNode();for(JsonNode row:raw.path("items")) {
            List<String> reasons=selectionReasons(row);
            items.add(HostJson.obj("documentId",Long.toString(row.path("id").longValue()),"title",row.get("title"),"originalName",row.get("originalName"),
                    "visibleLibraryIds",AssistantObservationProjection.wireIds(row.path("libraryIds")),"processingStatus",row.get("processingStatus"),
                    "lifecycleStatus",row.get("lifecycleStatus"),"selectable",reasons.isEmpty(),"reasonCodes",reasons));
        }
        return page(raw,items,scope);
    }
    private static List<String> selectionReasons(JsonNode row) {
        List<String> reasons=new ArrayList<>();
        // Selection readiness is metadata only. Formal source availability remains a domain tool decision.
        if(!"COMPLETED".equals(row.path("processingStatus").asText()))reasons.add("DOCUMENT_NOT_COMPLETED");
        if(row.hasNonNull("lifecycleStatus")&&!"active".equals(row.path("lifecycleStatus").asText()))reasons.add("DOCUMENT_NOT_ACTIVE");
        return reasons;
    }
    private ObjectNode page(JsonNode raw,ArrayNode items,Scope scope) {
        return HostJson.obj("items",items,"total",Long.toString(raw.path("total").longValue()),"page",raw.get("page"),"pageSize",raw.get("pageSize"),
                "hasMore",raw.get("hasMore"),"authorizedScope",HostJson.scope(scope),"observedAt",clock.instant().toString(),
                "coverage",HostJson.coverage(raw.path("completeCoverage").asBoolean(),raw.path("truncated").asBoolean(),"selected_scope_page",Set.of(),List.of()));
    }
    public ObjectNode create(String body) {
        var selection=AssistantTemplatePlanner.request(body);var a=context();check(a.owner(),selection.scope());
        for(long doc:selection.documentOrder()) {
            Scope scope=new Scope(ScopeMode.SELECTED,selection.scope().libraryIds(),Set.of(doc),Set.of());
            ToolResult result=invoke(a.owner(),scope,"ingestion.get_document",HostJson.obj("documentId",doc));
            if(result.status()==ToolStatus.ERROR||!selectionReasons(HostJson.internal(result.dataJson(),32000)).isEmpty())throw HostJson.error(409,"SELECTION_CHANGED");
        }
        var e=store.reserve(a.owner(),AssistantTemplatePlanner.plan(selection),selection.query(),selection.goal());
        try {store.attach(e,runtime.start(e.plan));}
        catch(RuntimeException ex){store.abandon(e);throw HostJson.error(429,"RUN_CAPACITY_EXCEEDED");}
        return projection.run(e,clock.instant());
    }
    private AssistantRunStore.Entry entry(String id) {
        context();var e=store.get(HostJson.identifier(id));check(e.owner,e.plan.scope());return e;
    }
    public ObjectNode run(String id) {return projection.run(entry(id),clock.instant());}
    public ObjectNode cancel(String id) {var e=entry(id);boolean accepted=e.handle.cancel();return HostJson.obj("accepted",accepted,"run",projection.run(e,clock.instant()));}
    public ObjectNode artifact(String run,String artifact) {var e=entry(run);return projection.artifact(e,e.artifact(HostJson.identifier(artifact)));}
    public ObjectNode graph(String run,String artifact) {var e=entry(run);return projection.graph(e,e.artifact(HostJson.identifier(artifact)));}
    public ObjectNode reread(String run,String artifact,String body) {
        JsonNode request=HostJson.parse(body,4096);HostJson.fields(request,Set.of("sourceId","expectedContentHash","maxChars"),Set.of("sourceId","expectedContentHash"));
        String id=HostJson.text(request,"sourceId",1,256),hash=HostJson.text(request,"expectedContentHash",64,64);
        if(!hash.matches("[0-9a-f]{64}"))throw HostJson.error(400,"INVALID_ARGUMENT");
        int chars=HostJson.integer(request,"maxChars",200,4000,4000);
        var e=entry(run);Artifact original=e.artifact(HostJson.identifier(artifact));
        EvidenceRef ref=original.evidence().stream().filter(r->r.sourceId().equals(id)).findFirst().orElseThrow(()->HostJson.error(404,"NOT_FOUND"));
        var pointer=projection.pointer(e,ref).orElseThrow(()->HostJson.error(409,"SOURCE_UNAVAILABLE"));
        if(!hash.equals(pointer.hash()))throw HostJson.error(409,"STALE_SOURCE");
        Scope scope=new Scope(ScopeMode.SELECTED,original.authorizedScope().libraryIds(),Set.of(pointer.document()),Set.of());
        ToolResult result=invoke(e.owner,scope,"research.read_source",HostJson.obj("documentId",pointer.document(),"chunkId",pointer.chunk(),"expectedContentHash",hash,"maxChars",chars));
        if(result.status()==ToolStatus.ERROR)throw switch(result.errorCode()) {
            case "SOURCE_NOT_FOUND_OR_OUT_OF_SCOPE"->HostJson.error(404,"NOT_FOUND");
            case "SOURCE_UNAVAILABLE"->HostJson.error(409,"SOURCE_UNAVAILABLE");
            case "SOURCE_CHANGED_OR_UNAVAILABLE"->HostJson.error(503,"SOURCE_CHANGED_OR_UNAVAILABLE");
            case "STALE_SOURCE"->HostJson.error(409,"STALE_SOURCE");
            case "READ_IDENTITY_UNVERIFIED"->HostJson.error(503,"READ_IDENTITY_UNVERIFIED");
            default->HostJson.error(500,"INTERNAL_ERROR");
        };
        JsonNode raw=data(result);if(!hash.equals(raw.path("contentHash").asText())||raw.path("documentId").longValue()!=pointer.document()||raw.path("chunkId").longValue()!=pointer.chunk())throw HostJson.error(409,"STALE_SOURCE");
        ObjectNode view=AssistantObservationProjection.convert(raw,Set.of("documentId","chunkId","unitId"),Set.of(),"documentId","chunkId","unitId","unitIndex","unitType","sourceLocator",
                "chunkIndex","startOffset","endOffset","contentHash","content","bodyTruncated","formalAvailable");
        view.put("runId",e.runId);view.put("artifactId",original.artifactId());view.put("originalSourceId",id);view.put("status",result.status().wireValue());
        view.set("effectiveScope",HostJson.scope(result.effectiveScope()));view.put("observedAt",clock.instant().toString());view.put("semanticSupport","not_evaluated");
        // Retains original evidence provenance. The fresh read time is the top-level observedAt.
        ObjectNode evidence=projection.evidence(e,original,ref);evidence.put("sourceState",raw.path("bodyTruncated").asBoolean()?"truncated":"current");
        view.set("evidence",evidence);view.set("limitations",HostJson.array(result.limitations()));return view;
    }
    @Override public void close() {store.close();if(runtime!=null)runtime.close();io.close();}
}
