package com.knowledgegraph.assistant.host;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import java.time.Duration;
import java.util.*;

final class AssistantTemplatePlanner {
    static final String TEMPLATE="source_graph_citation_v1";
    record Selection(Scope scope,List<Long> documentOrder,String query,String goal) {
        Selection {
            documentOrder=List.copyOf(documentOrder);
            if(documentOrder.isEmpty() || documentOrder.size()>2 || new HashSet<>(documentOrder).size()!=documentOrder.size()
                    || !new HashSet<>(documentOrder).equals(scope.documentIds()))throw new IllegalArgumentException("Invalid document order");
        }
    }
    static Selection request(String body) {
        var root=HostJson.parse(body,65536);
        HostJson.fields(root,Set.of("templateId","selection","query","goal"),Set.of("templateId","selection","query"));
        if(!TEMPLATE.equals(HostJson.text(root,"templateId",1,100))) throw HostJson.error(400,"INVALID_ARGUMENT");
        var selected=root.get("selection");
        HostJson.fields(selected,Set.of("libraryIds","documentIds"),Set.of("libraryIds","documentIds"));
        Set<Long> documents=HostJson.ids(selected.get("documentIds"),2);
        Scope scope=new Scope(ScopeMode.SELECTED,HostJson.ids(selected.get("libraryIds"),1000),documents,Set.of());
        return new Selection(scope,List.copyOf(documents),HostJson.text(root,"query",2,200),root.has("goal")?HostJson.text(root,"goal",1,4000):null);
    }
    static RunPlan plan(Selection selection) {
        String prefix="p"+UUID.randomUUID(); List<AgentTask> tasks=new ArrayList<>(); int index=0;
        for(long doc:selection.documentOrder()) {
            Scope scope=new Scope(ScopeMode.SELECTED,selection.scope().libraryIds(),Set.of(doc),Set.of());
            String base=prefix+":"+(++index)+":";
            tasks.add(task(base+"metadata",Role.INGESTION,scope,Set.of(),Set.of("ingestion.get_document"),doc,selection.query(),2,1,10,1));
            tasks.add(task(base+"source",Role.GRAPH_RESEARCH,scope,Set.of(base+"metadata"),Set.of("research.retrieve_chunks"),doc,selection.query(),2,1,20,1));
            tasks.add(task(base+"graph",Role.GRAPH_RESEARCH,scope,Set.of(base+"source"),
                    Set.of("research.search_nodes","research.get_neighbors","research.find_paths","research.get_node"),doc,selection.query(),8,7,30,1));
            tasks.add(task(base+"verify",Role.QUALITY_GOVERNANCE,scope,Set.of(base+"graph"),Set.of("quality.verify_artifact_claims"),doc,selection.query(),2,1,20,0));
        }
        return new RunPlan("Read sources, directed graph, check citations",selection.scope(),tasks,
                new Limits(32,24,Duration.ofSeconds(180)));
    }
    static String stage(String id) {
        return switch(id.substring(id.lastIndexOf(':')+1)) {
            case "metadata" -> "document_metadata"; case "source" -> "source_read";
            case "graph" -> "directed_graph"; case "verify" -> "citation_check";
            default -> throw new IllegalArgumentException("Unknown stage");
        };
    }
    private static AgentTask task(String id,Role role,Scope scope,Set<String> dependencies,Set<String> tools,
                                  long doc,String query,int decisions,int calls,int seconds,int minTools) {
        return new AgentTask(id,role,stage(id),scope,dependencies,tools,
                HostJson.obj("documentId",doc,"query",query).toString(),new Limits(decisions,calls,Duration.ofSeconds(seconds)),
                new CompletionPolicy(minTools,false,true,true));
    }
}
