package com.knowledgegraph.assistant.host;

import com.knowledgegraph.assistant.quality.QualityArtifactReader;
import com.knowledgegraph.assistant.research.ResearchArtifacts;
import java.util.*;

final class HostQualityArtifactReader implements QualityArtifactReader {
    private final AssistantRunStore store;
    HostQualityArtifactReader(AssistantRunStore store) { this.store=store; }
    @Override public Optional<DirectArtifact> directArtifact(String run,String consumer,String id) {
        return store.direct(run,consumer,id).flatMap(a->{
            try {
                if(a.authorizedScope().documentIds().size()!=1) return Optional.empty();
                long document=a.authorizedScope().documentIds().iterator().next();
                var handoff=ResearchArtifacts.qualityHandoff(a,document,Set.of(a.producerTaskId()));
                // The fixed template has at most two one-source claims; never hide a required second batch.
                return handoff.eligibleBatches().size()==1 ? Optional.of(handoff.eligibleBatches().get(0)):Optional.empty();
            } catch(RuntimeException ignored) { return Optional.empty(); }
        });
    }
}
