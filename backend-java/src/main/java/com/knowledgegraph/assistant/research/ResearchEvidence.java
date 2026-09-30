package com.knowledgegraph.assistant.research;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/** Stable IDs and excerpts are shared by search and exact reread. */
final class ResearchEvidence {
    private ResearchEvidence() {}

    static EvidenceRef source(ResearchReadRepository.SourceMeta meta, String content, Scope scope) {
        String hash = meta.contentHash();
        String id = "research:chunk:" + meta.documentId() + ":" + meta.chunkId() + ":" + hash
                + ":scope:" + scopeFingerprint(scope);
        String locator = "document=" + meta.documentId() + ";unit=" + meta.unitId()
                + ";unitIndex=" + meta.unitIndex() + ";type=" + safe(meta.unitType())
                + ";source=" + safe(meta.sourceLocator()) + ";chunk=" + meta.chunkId();
        return new EvidenceRef(id, EvidenceKind.SOURCE,
                new ObjectRef("document_chunk", Long.toString(meta.chunkId()), hash), scope,
                locator, cut(content, 240));
    }

    static EvidenceRef node(ResearchReadRepository.Node node, Scope scope) {
        String version = node.updatedAt();
        return new EvidenceRef("research:node:" + node.id() + ":" + version + ":scope:" + scopeFingerprint(scope),
                EvidenceKind.GRAPH_FACT,
                new ObjectRef("knowledge_node", Long.toString(node.id()), version), scope,
                "node=" + node.id(), cut(node.name(), 240));
    }

    static EvidenceRef edge(ResearchReadRepository.Edge edge, Scope scope) {
        String version = edge.updatedAt();
        return new EvidenceRef("research:edge:" + edge.id() + ":" + version + ":scope:" + scopeFingerprint(scope),
                EvidenceKind.GRAPH_FACT,
                new ObjectRef("knowledge_edge", Long.toString(edge.id()), version), scope,
                "edge=" + edge.id() + ";source=" + edge.sourceNodeId() + ";target=" + edge.targetNodeId(),
                cut(edge.relationType(), 240));
    }

    private static String cut(String value, int max) {
        String text = value == null || value.isBlank() ? "unavailable" : value;
        return text.substring(0, Math.min(max, text.length()));
    }
    private static String safe(String value) { return value == null ? "unknown" : value.replace(';', ','); }

    /** Evidence is a scoped observation, not a globally transferable authorization token. */
    static String scopeFingerprint(Scope scope) {
        String canonical = scope.mode() + "|libraries=" + ids(scope.libraryIds())
                + "|documents=" + ids(scope.documentIds()) + "|nodes=" + ids(scope.nodeIds());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String ids(Set<Long> values) {
        return values.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
}
