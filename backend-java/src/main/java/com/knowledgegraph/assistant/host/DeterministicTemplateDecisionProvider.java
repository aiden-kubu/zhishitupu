package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.runtime.DecisionProvider;
import com.knowledgegraph.assistant.research.ResearchArtifacts;
import java.util.*;

/** Only chooses existing tools/finishes; no JDBC, model, network or hidden tool execution. */
final class DeterministicTemplateDecisionProvider implements DecisionProvider {
    private final AssistantRunStore store;
    DeterministicTemplateDecisionProvider(AssistantRunStore store) { this.store=store; }
    @Override public String decide(Context c) {
        store.capture(c);
        JsonNode input=HostJson.internal(c.task().inputJson(),16000);
        long doc=input.path("documentId").longValue(); String query=input.path("query").textValue();
        String stage=AssistantTemplatePlanner.stage(c.task().taskId());
        return switch(stage) {
            case "document_metadata" -> {
                if(c.observations().isEmpty()) yield call("ingestion.get_document",HostJson.obj("documentId",doc));
                var observation=c.observations().get(0); requireData(observation,"id");
                yield finish(c,HostJson.obj("kind",stage,"documentId",doc,"data",data(observation)),List.of());
            }
            case "source_read" -> {
                if(c.observations().isEmpty()) yield call("research.retrieve_chunks",HostJson.obj("query",query,"maxSources",2,"maxChars",1200));
                var observation=c.observations().get(0); requireData(observation,"items");
                JsonNode d=data(observation);
                yield finish(c,HostJson.obj("kind",stage,"documentId",doc,"items",d.get("items"),
                        "coverage",HostJson.coverage(d.path("completeCoverage").asBoolean(),d.path("hasMore").asBoolean(),
                                d.path("coverageKind").asText("bounded_query_candidates"),longs(d.path("uncoveredRequestedDocumentIds")),observation.result().limitations())),List.of());
            }
            case "directed_graph" -> graph(c,doc,query);
            case "citation_check" -> verify(c,doc);
            default -> throw new IllegalArgumentException("Unknown stage");
        };
    }
    private String graph(Context c,long doc,String query) {
        if(c.observations().isEmpty()) return call("research.search_nodes",HostJson.obj("query",query,"limit",5));
        ToolObservation search=c.observations().get(0);requireData(search,"items");
        JsonNode hits=data(search).path("items");
        if(!hits.isEmpty() && c.observations().size()==1) {
            long first=hits.get(0).path("nodeId").longValue();
            if(hits.size()==1) return call("research.get_neighbors",HostJson.obj("nodeId",first,"depth",2,"maxNodes",10,"maxEdges",10));
            return call("research.find_paths",HostJson.obj("sourceNodeId",first,"targetNodeId",hits.get(1).path("nodeId").longValue(),"maxDepth",2,"maxPaths",3));
        }
        if(hits.size()>=2) {
            LinkedHashSet<Long> wanted=pathNodes(c);
            long completed=c.observations().stream().filter(x->x.toolName().equals("research.get_node")).count();
            if(completed<wanted.size()) return call("research.get_node",HostJson.obj("nodeId",wanted.stream().skip(completed).findFirst().orElseThrow()));
        }
        Artifact source=c.inputs().get(0);JsonNode sourceData=HostJson.internal(source.dataJson(),16000);
        ArrayNode claims=HostJson.JSON.createArrayNode(); Set<String> known=new HashSet<>();
        source.evidence().stream().filter(e->e.kind()==EvidenceKind.SOURCE).forEach(e->known.add(e.sourceId()));
        for(JsonNode chunk:sourceData.path("items")) {
            String id=chunk.path("sourceId").asText(); String quote=chunk.path("content").asText();
            if(!known.contains(id) || chunk.path("documentId").longValue()!=doc) throw new IllegalArgumentException("Source evidence mismatch");
            if(!quote.isBlank()) claims.add(HostJson.obj("claimId","quote-"+(claims.size()+1),"text",quote.substring(0,Math.min(1000,quote.length())),
                    "kind","source_fact","sourceIds",List.of(id),"originTaskId",source.producerTaskId(),"originArtifactId",source.artifactId(),"semanticSupport","not_evaluated"));
        }
        ObjectNode graph=graphData(c.observations());
        var gaps=new LinkedHashSet<String>();gaps.add("QUOTE_EXCERPT_ONLY");gaps.add("GRAPH_SEMANTICS_NOT_EVALUATED");
        if(graph.path("projectionIncomplete").asBoolean())gaps.add("GRAPH_PROJECTION_INCOMPLETE");
        if(hits.isEmpty())gaps.add("NO_GRAPH_MATCH");
        ObjectNode coverage=HostJson.coverage(false,graph.path("truncated").asBoolean(),"bounded_graph_window",Set.of(),gaps);
        return finish(c,HostJson.obj("kind","document_research","documentId",doc,"claims",claims,"graph",graph,"coverage",coverage),gaps);
    }
    private String verify(Context c,long doc) {
        Artifact original=c.inputs().get(0);
        var handoff=ResearchArtifacts.qualityHandoff(original,doc,c.task().dependencyIds());
        if(c.observations().isEmpty() && handoff.eligibleBatches().size()==1 && !handoff.eligibleBatches().get(0).claims().isEmpty()) {
            List<String> ids=handoff.eligibleBatches().get(0).claims().stream().filter(x->!x.sources().isEmpty() && x.sources().size()<=2)
                    .map(x->x.claimId()).toList();
            if(!ids.isEmpty())return call("quality.verify_artifact_claims",HostJson.obj("artifactId",original.artifactId(),"claimIds",ids));
        }
        ArrayNode unchecked=HostJson.JSON.createArrayNode();
        for(String id:handoff.deferredClaimIds())unchecked.add(HostJson.obj("claimId",id,"reasonCode","SOURCE_BATCH_REQUIRED"));
        if(c.observations().isEmpty()) {
            return finish(c,HostJson.obj("kind","citation_check","checkedArtifactId",original.artifactId(),"producerTaskId",original.producerTaskId(),
                    "checks",List.of(),"uncheckedClaims",unchecked,"currentClaims",0,"requestedClaims",0,"completeCoverage",false,
                    "coverageBasis","requested_claims_only","semanticSupport","not_evaluated","observedAt",null),List.of("NO_ELIGIBLE_SOURCE_CLAIMS"));
        }
        var observation=c.observations().get(0);requireData(observation,"checks");JsonNode d=data(observation);
        ObjectNode payload=HostJson.obj("kind","citation_check","checkedArtifactId",original.artifactId(),"producerTaskId",original.producerTaskId(),
                "checks",d.get("checks"),"uncheckedClaims",unchecked,"currentClaims",d.path("currentClaims").intValue(),
                "requestedClaims",d.path("requestedClaims").intValue(),"completeCoverage",d.path("completeCoverage").asBoolean(),
                "coverageBasis","requested_claims_only","semanticSupport","not_evaluated","observedAt",observation.observedAt().toString());
        return finish(c,payload,unchecked.isEmpty()?List.of():List.of("CLAIMS_UNCHECKED"));
    }
    static ObjectNode graphData(List<ToolObservation> observations) {
        LinkedHashMap<Long,JsonNode> nodes=new LinkedHashMap<>(),edges=new LinkedHashMap<>();
        ArrayNode paths=HostJson.JSON.createArrayNode(); boolean truncated=false,incomplete=false; Boolean noPath=null,pathTruncated=null;
        for(var observation:observations) {
            if(observation.result().status()==ToolStatus.ERROR) { incomplete=true;continue; }
            JsonNode d=data(observation);
            switch(observation.toolName()) {
                case "research.get_neighbors" -> {
                    for(JsonNode row:d.path("nodes"))nodes.putIfAbsent(row.path("nodeId").longValue(),row);
                    for(JsonNode row:d.path("edges"))edges.putIfAbsent(row.path("edgeId").longValue(),row);
                    truncated|=d.path("truncated").asBoolean();
                }
                case "research.get_node" -> { JsonNode row=d.get("node");if(row!=null)nodes.put(row.path("nodeId").longValue(),row); }
                case "research.find_paths" -> {
                    noPath=d.path("noPathWithinDepth").asBoolean();pathTruncated=d.path("truncated").asBoolean();truncated|=pathTruncated;
                    for(JsonNode path:d.path("paths")) {
                        ArrayNode ids=HostJson.JSON.createArrayNode();
                        for(JsonNode edge:path) {edges.putIfAbsent(edge.path("edgeId").longValue(),edge);ids.add(Long.toString(edge.path("edgeId").longValue()));}
                        paths.add(ids);
                    }
                }
                default -> { }
            }
        }
        // search gives real nodes; use them only if no richer node observation returned that ID.
        if(!observations.isEmpty() && observations.get(0).result().status()!=ToolStatus.ERROR) {
            JsonNode hits=data(observations.get(0)).path("items");
            int max=hits.size()>=2?2:hits.size();
            for(int j=0;j<max;j++) {JsonNode row=hits.get(j);nodes.putIfAbsent(row.path("nodeId").longValue(),row);}
        }
        Iterator<JsonNode> iterator=edges.values().iterator();
        while(iterator.hasNext()) {
            JsonNode row=iterator.next();
            if(!nodes.containsKey(row.path("sourceNodeId").longValue()) || !nodes.containsKey(row.path("targetNodeId").longValue())) {
                iterator.remove();incomplete=true;
            }
        }
        ArrayNode validPaths=HostJson.JSON.createArrayNode();
        for(JsonNode path:paths) {
            boolean valid=true;Long last=null;
            for(JsonNode id:path) {
                JsonNode edge=edges.get(Long.parseLong(id.textValue()));
                if(edge==null || last!=null && edge.path("sourceNodeId").longValue()!=last) {valid=false;break;}
                last=edge.path("targetNodeId").longValue();
            }
            if(valid)validPaths.add(path);else incomplete=true;
        }
        return HostJson.obj("nodes",nodes.values(),"edges",edges.values(),"paths",validPaths,"truncated",truncated,
                "projectionIncomplete",incomplete,"noPathWithinDepth",noPath,"pathTruncated",pathTruncated);
    }
    private static LinkedHashSet<Long> pathNodes(Context c) {
        LinkedHashSet<Long> ids=new LinkedHashSet<>();JsonNode hits=data(c.observations().get(0)).path("items");
        ids.add(hits.get(0).path("nodeId").longValue());ids.add(hits.get(1).path("nodeId").longValue());
        for(var o:c.observations())if(o.toolName().equals("research.find_paths") && o.result().status()!=ToolStatus.ERROR)
            for(JsonNode path:data(o).path("paths"))for(JsonNode edge:path) {ids.add(edge.path("sourceNodeId").longValue());ids.add(edge.path("targetNodeId").longValue());}
        if(ids.size()>5)throw new IllegalArgumentException("Path node bound");return ids;
    }
    private String finish(Context c,ObjectNode payload,Collection<String> extra) {
        LinkedHashMap<String,EvidenceRef> known=new LinkedHashMap<>();LinkedHashSet<String> gaps=new LinkedHashSet<>(extra);
        c.inputs().forEach(a->a.evidence().forEach(e->known.put(e.sourceId(),e)));
        c.observations().forEach(o->{o.result().evidence().forEach(e->known.put(e.sourceId(),e));gaps.addAll(o.result().limitations());});
        boolean failed=!c.observations().isEmpty() && c.observations().stream().allMatch(o->o.result().status()==ToolStatus.ERROR);
        String outcome=failed?"failed":gaps.isEmpty() && c.observations().stream().allMatch(o->o.result().status()==ToolStatus.OK)?"completed":"partial";
        if(payload.toString().length()>16000)throw new IllegalArgumentException("Host payload bound");
        return HostJson.obj("type","complete","outcome",outcome,"summary",c.task().objective(),"data",payload,
                "evidenceIds",known.keySet(),"actionProposals",List.of(),"limitations",gaps).toString();
    }
    private static String call(String name,ObjectNode args) { return HostJson.obj("type","call_tool","name",name,"arguments",args).toString(); }
    static JsonNode data(ToolObservation o) { return HostJson.internal(o.result().dataJson(),32000); }
    private static void requireData(ToolObservation o,String key) {
        if(!data(o).has(key))throw new IllegalStateException("No trusted payload; no artifact is synthesized");
    }
    static List<Long> longs(JsonNode node) { List<Long> ids=new ArrayList<>();node.forEach(n->ids.add(n.longValue()));return ids; }
}
