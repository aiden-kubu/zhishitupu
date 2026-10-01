package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import java.time.*;
import java.util.*;

/** Whitelists trusted results; never recomputes rankings, claim support, or graph paths. */
final class AssistantObservationProjection {
    record Pointer(long document,long chunk,String hash) {}
    private final String hostInstance;
    AssistantObservationProjection(String hostInstance) { this.hostInstance=hostInstance; }
    ObjectNode run(AssistantRunStore.Entry e,Instant now) {
        RunSnapshot snapshot=e.snapshot();ArrayNode tasks=HostJson.JSON.createArrayNode();Set<String> limits=new LinkedHashSet<>();
        for(TaskSnapshot t:snapshot.tasks()) {
            AgentTask plan=e.plan.tasks().stream().filter(x->x.taskId().equals(t.taskId())).findFirst().orElseThrow();
            tasks.add(HostJson.obj("taskId",t.taskId(),"role",t.role().wireValue(),"stage",AssistantTemplatePlanner.stage(t.taskId()),
                    "objective",plan.objective(),"authorizedScope",HostJson.scope(plan.scope()),"dependencyIds",plan.dependencyIds(),
                    "inputArtifactIds",e.inputs(t.taskId()).stream().map(Artifact::artifactId).toList(),"status",t.status().wireValue(),
                    "artifactId",t.artifact()==null?null:t.artifact().artifactId(),"errorCode",t.errorCode(),"usage",HostJson.usage(t.usage()),
                    "startedAt",HostJson.instant(t.startedAt()),"finishedAt",HostJson.instant(t.finishedAt())));
            if(t.artifact()!=null)limits.addAll(t.artifact().limitations());if(t.errorCode()!=null)limits.add(t.errorCode());
            for(ToolObservation o:e.observations(t.taskId()))if(o.result().errorCode()!=null)
                limits.add("TOOL_ERROR:"+o.toolName()+":"+o.result().errorCode());
        }
        if(e.tooLarge)limits.add("HOST_CONTENT_MEMORY_LIMIT");
        if(limits.size()>64)throw HostJson.error(500,"INTERNAL_ERROR");
        return HostJson.obj("runId",snapshot.runId(),"hostInstanceId",hostInstance,"templateId",AssistantTemplatePlanner.TEMPLATE,
                "query",e.query,"goal",e.goal,"executionMode","deterministic_tools","status",snapshot.status().wireValue(),
                "authorizedScope",HostJson.scope(snapshot.authorizedScope()),"tasks",tasks,"usage",HostJson.usage(snapshot.usage()),
                "createdAt",snapshot.createdAt().toString(),"deadline",snapshot.deadline().toString(),"expiresAt",HostJson.instant(e.expiresAt),
                "observedAt",now.toString(),"cancellationRequested",snapshot.cancellationRequested(),"recoverableAfterRestart",false,
                "recoverableAfterRefresh",false,"limitations",limits);
    }
    ObjectNode artifact(AssistantRunStore.Entry e,Artifact a) {
        JsonNode raw=HostJson.internal(a.dataJson(),16000);ObjectNode payload;
        switch(raw.path("kind").asText()) {
            case "document_metadata" -> {
                JsonNode d=raw.path("data");
                payload=HostJson.obj("kind","document_metadata","documentId",Long.toString(raw.path("documentId").longValue()),
                        "visibleLibraryIds",wireIds(d.path("libraryIds")),"title",d.get("title"),"processingStatus",d.get("processingStatus"),
                        "lifecycleStatus",d.get("lifecycleStatus"),"sourceFilePresent",d.get("sourceFilePresent"));
            }
            case "source_read" -> {
                ArrayNode chunks=HostJson.JSON.createArrayNode();
                for(JsonNode row:raw.path("items"))chunks.add(convert(row,Set.of("documentId","chunkId","unitId"),Set.of(),
                        "documentId","chunkId","contentHash","sourceId","unitId","unitIndex","unitType","sourceLocator","content","bodyTruncated","matchScore"));
                payload=HostJson.obj("kind","source_read","documentId",Long.toString(raw.path("documentId").longValue()),"items",chunks,"coverage",raw.get("coverage"));
            }
            case "document_research" -> {
                payload=HostJson.obj("kind","document_research","documentId",Long.toString(raw.path("documentId").longValue()),
                        "claims",raw.get("claims"),"graph",graph(e,a),"coverage",raw.get("coverage"));
            }
            case "citation_check" -> {
                payload=HostJson.select(raw,"kind","checkedArtifactId","producerTaskId","uncheckedClaims","currentClaims","requestedClaims",
                        "completeCoverage","coverageBasis","semanticSupport","observedAt");
                ArrayNode checks=HostJson.JSON.createArrayNode();
                for(JsonNode check:raw.path("checks")) {
                    ObjectNode out=HostJson.select(check,"claimId","referenceStatus","semanticSupport");
                    if(check.has("sources")) {
                        ArrayNode sources=HostJson.JSON.createArrayNode();
                        for(JsonNode source:check.path("sources")) {
                            ObjectNode view=HostJson.obj();
                            for(String key:List.of("chunkId","status","expectedHash","actualHash","unitIndex","locator","truncated","originalSourceId"))
                                if(source.has(key))view.set(key,key.equals("chunkId")&&!source.get(key).isNull()?
                                        TextNode.valueOf(Long.toString(source.get(key).longValue())):source.get(key).deepCopy());
                            if(source.has("chunkId"))view.putNull("content");sources.add(view);
                        }
                        out.set("sources",sources);
                    }
                    checks.add(out);
                }
                payload.set("checks",checks);
            }
            default -> throw HostJson.error(500,"INTERNAL_ERROR");
        }
        ArrayNode observations=HostJson.JSON.createArrayNode();
        for(ToolObservation o:e.observations(a.producerTaskId()))observations.add(HostJson.obj("toolCallId",o.toolCallId(),
                "operation",o.toolName().substring(o.toolName().indexOf('.')+1),"status",o.result().status().wireValue(),
                "effectiveScope",HostJson.scope(o.result().effectiveScope()),"observedAt",o.observedAt().toString(),
                "errorCode",o.result().errorCode(),"limitations",o.result().limitations()));
        ObjectNode coverage=HostJson.coverage(false,raw.path("coverage").path("truncated").asBoolean(),
                raw.path("kind").asText(),raw.path("coverage").has("uncoveredDocumentIds")?
                readWireIds(raw.path("coverage").path("uncoveredDocumentIds")):Set.of(),a.limitations());
        if(raw.path("kind").asText().equals("document_metadata"))coverage.put("completeCoverage",true);
        return HostJson.obj("artifactId",a.artifactId(),"runId",e.runId,"producerTaskId",a.producerTaskId(),
                "inputArtifactIds",e.inputs(a.producerTaskId()).stream().map(Artifact::artifactId).toList(),
                "authorizedScope",HostJson.scope(a.authorizedScope()),"observedScopes",a.observedScopes().stream().map(HostJson::scope).toList(),
                "outcome",a.outcome().wireValue(),"summary",a.summary(),"createdAt",a.createdAt().toString(),"scopeMatched",a.scopeMatched(),
                "executionReceipt",false,"actionProposals",List.of(),"evidence",evidence(e,a),"observations",observations,"coverage",coverage,
                "payload",payload,"limitations",a.limitations());
    }
    ObjectNode graph(AssistantRunStore.Entry e,Artifact a) {
        JsonNode raw=HostJson.internal(a.dataJson(),16000);
        if(!raw.path("kind").asText().equals("document_research") || !raw.has("graph"))throw HostJson.error(409,"GRAPH_NOT_AVAILABLE");
        List<ToolObservation> observations=e.observations(a.producerTaskId());
        Instant observed=observations.stream().filter(o->o.toolName().startsWith("research.") && o.result().status()!=ToolStatus.ERROR)
                .map(ToolObservation::observedAt).max(Comparator.naturalOrder()).orElseThrow(()->HostJson.error(409,"GRAPH_NOT_AVAILABLE"));
        JsonNode g=raw.get("graph");ArrayNode nodes=HostJson.JSON.createArrayNode(),edges=HostJson.JSON.createArrayNode();
        for(JsonNode row:g.path("nodes"))nodes.add(convert(row,Set.of("nodeId"),Set.of("libraryIds"),
                "nodeId","name","nameEn","type","definition","definitionTruncated","status","updatedAt","versionKind","libraryIds",
                "aliases","origin","reviewStatus","matchedBy","sourceEvidenceAvailable"));
        for(JsonNode row:g.path("edges"))edges.add(convert(row,Set.of("edgeId","sourceNodeId","targetNodeId"),Set.of(),
                "edgeId","sourceNodeId","targetNodeId","relationType","status","updatedAt","origin","reviewStatus","sourceEvidenceAvailable"));
        if(nodes.size()>10 || edges.size()>10 || g.path("paths").size()>3)throw HostJson.error(500,"INTERNAL_ERROR");
        return HostJson.obj("runId",e.runId,"taskId",a.producerTaskId(),"artifactId",a.artifactId(),"authorizedScope",HostJson.scope(a.authorizedScope()),
                "observedScopes",a.observedScopes().stream().map(HostJson::scope).toList(),"observedAt",observed.toString(),"empty",nodes.isEmpty(),
                "direction","stored_source_to_target","neighborhoodTraversal","incident_edges","pathTraversal","outgoing_edges",
                "nodes",nodes,"edges",edges,"paths",g.get("paths"),"evidence",evidence(e,a),
                "coverage",HostJson.coverage(false,g.path("truncated").asBoolean(),"bounded_graph_window",Set.of(),a.limitations()),
                "noPathWithinDepth",g.get("noPathWithinDepth"),"pathTruncated",g.get("pathTruncated"));
    }
    ArrayNode evidence(AssistantRunStore.Entry e,Artifact a) {
        ArrayNode result=HostJson.JSON.createArrayNode();
        for(EvidenceRef ref:a.evidence())result.add(evidence(e,a,ref));return result;
    }
    ObjectNode evidence(AssistantRunStore.Entry e,Artifact a,EvidenceRef ref) {
        String originTask=null;Instant observed=null;String originArtifact=null;
        for(AgentTask task:e.plan.tasks())for(ToolObservation o:e.observations(task.taskId()))
            if(o.result().evidence().contains(ref) && (observed==null || o.observedAt().isBefore(observed))) {
                originTask=task.taskId();observed=o.observedAt();
                originArtifact=e.snapshot().tasks().stream().filter(t->t.taskId().equals(task.taskId())).map(TaskSnapshot::artifact)
                        .filter(Objects::nonNull).map(Artifact::artifactId).findFirst().orElse(null);
            }
        if(originTask==null || originArtifact==null)throw HostJson.error(500,"INTERNAL_ERROR");
        Optional<Pointer> pointer=pointer(e,ref);
        String state="not_checked";
        if(originTask.endsWith(":verify"))state="current"; // only CURRENT/TRUNCATED source checks produce this evidence
        if(originTask.endsWith(":verify")) {
            for(var o:e.observations(originTask))for(JsonNode claim:DeterministicTemplateDecisionProvider.data(o).path("checks"))
                for(JsonNode check:claim.path("sources"))if(check.path("chunkId").longValue()==Long.parseLong(ref.object().id())
                        && check.path("status").asText().equals("TRUNCATED"))state="truncated";
        }
        return HostJson.obj("sourceId",ref.sourceId(),"kind",ref.kind().wireValue(),"object",HostJson.obj("kind",ref.object().kind(),
                "id",ref.object().id(),"version",ref.object().version()),"scope",HostJson.scope(ref.scope()),"locator",ref.locator(),
                "excerpt",ref.excerpt(),"originTaskId",originTask,"originArtifactId",originArtifact,"observedAt",observed.toString(),
                "rereadPointer",pointer.map(p->HostJson.obj("documentId",Long.toString(p.document()),"chunkId",Long.toString(p.chunk()),"contentHash",p.hash())).orElse(null),
                "sourceState",state);
    }
    Optional<Pointer> pointer(AssistantRunStore.Entry e,EvidenceRef ref) {
        if(ref.kind()!=EvidenceKind.SOURCE || !ref.object().kind().equals("document_chunk"))return Optional.empty();
        for(var captured:e.contexts.values())for(var o:captured.observations()) {
            JsonNode data=DeterministicTemplateDecisionProvider.data(o);
            for(JsonNode item:data.path("items")) {
                if(!item.has("documentId") || !item.has("chunkId") || !item.has("contentHash") || !item.has("sourceId"))continue;
                long doc=item.path("documentId").longValue(),chunk=item.path("chunkId").longValue();String hash=item.path("contentHash").asText();
                if(!Long.toString(chunk).equals(ref.object().id()) || !hash.equals(ref.object().version())
                        || !ref.scope().documentIds().contains(doc))continue;
                boolean original=o.result().evidence().stream().anyMatch(x->x.sourceId().equals(item.path("sourceId").asText())
                        && x.object().id().equals(ref.object().id()) && Objects.equals(x.object().version(),hash));
                if(original && (ref.sourceId().equals(item.path("sourceId").asText()) || linkedVerified(e,ref,item.path("sourceId").asText(),chunk,hash)))
                    return Optional.of(new Pointer(doc,chunk,hash));
            }
        }
        return Optional.empty();
    }
    private boolean linkedVerified(AssistantRunStore.Entry e,EvidenceRef ref,String original,long chunk,String hash) {
        for(var c:e.contexts.values())for(var o:c.observations())if(o.result().evidence().contains(ref))
            for(JsonNode claim:DeterministicTemplateDecisionProvider.data(o).path("checks"))for(JsonNode check:claim.path("sources"))
                if(original.equals(check.path("originalSourceId").asText()) && check.path("chunkId").longValue()==chunk
                        && hash.equals(check.path("actualHash").asText()))return true;
        return false;
    }
    static ObjectNode convert(JsonNode raw,Set<String> ids,Set<String> arrays,String... names) {
        ObjectNode result=HostJson.select(raw,names);
        for(String name:ids)if(result.hasNonNull(name))result.put(name,Long.toString(result.get(name).longValue()));
        for(String name:arrays)result.set(name,HostJson.array(wireIds(result.path(name))));return result;
    }
    static List<String> wireIds(JsonNode array) { List<String> result=new ArrayList<>();array.forEach(n->result.add(Long.toString(n.longValue())));return result; }
    private static Set<Long> readWireIds(JsonNode array) { Set<Long> values=new LinkedHashSet<>();array.forEach(n->values.add(HostJson.id(n.textValue())));return values; }
}
