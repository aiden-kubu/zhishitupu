package com.knowledgegraph.assistant.learning;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.research.ResearchReadRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/** IDs identify a particular scoped observation; physical-source counting uses object identity instead. */
final class LearningEvidence {
    private LearningEvidence() {}

    static EvidenceRef source(ResearchReadRepository.SourceMeta meta, String content, Scope scope) {
        String id = "learning:chunk:" + meta.documentId() + ":" + meta.chunkId() + ":"
                + meta.contentHash() + ":scope:" + fingerprint(scope);
        return new EvidenceRef(id, EvidenceKind.SOURCE,
                new ObjectRef("document_chunk", Long.toString(meta.chunkId()), meta.contentHash()),
                scope, "document=" + meta.documentId() + ";unit=" + meta.unitId()
                + ";unitIndex=" + meta.unitIndex() + ";chunk=" + meta.chunkId(),
                null);
    }
    static EvidenceRef node(ResearchReadRepository.Node node, Scope scope) {
        return new EvidenceRef("learning:node:" + node.id() + ":" + node.updatedAt()
                + ":scope:" + fingerprint(scope), EvidenceKind.GRAPH_FACT,
                new ObjectRef("knowledge_node", Long.toString(node.id()), node.updatedAt()),
                scope, "node=" + node.id(), cut(node.name(), 240));
    }
    static EvidenceRef edge(ResearchReadRepository.Edge edge, Scope scope) {
        return new EvidenceRef("learning:edge:" + edge.id() + ":" + edge.updatedAt()
                + ":scope:" + fingerprint(scope), EvidenceKind.GRAPH_FACT,
                new ObjectRef("knowledge_edge", Long.toString(edge.id()), edge.updatedAt()),
                scope, "edge=" + edge.id() + ";source=" + edge.sourceNodeId()
                + ";target=" + edge.targetNodeId(), cut(edge.relationType(), 240));
    }
    static String physicalSource(EvidenceRef ref) {
        if (ref.kind() != EvidenceKind.SOURCE || !"document_chunk".equals(ref.object().kind())) return "";
        String locator = ref.locator() == null ? "" : ref.locator();
        String document = "";
        for (String part : locator.split(";")) if (part.startsWith("document=")) document = part.substring(9);
        return document + ":" + ref.object().id() + ":" + ref.object().version();
    }
    private static String cut(String value, int limit) {
        String text = value == null || value.isBlank() ? "unavailable" : value;
        return text.substring(0, Math.min(text.length(), limit));
    }
    private static String fingerprint(Scope scope) {
        String value = scope.mode() + "|libraries=" + ids(scope.libraryIds())
                + "|documents=" + ids(scope.documentIds()) + "|nodes=" + ids(scope.nodeIds());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String ids(Set<Long> values) {
        return values.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
}
