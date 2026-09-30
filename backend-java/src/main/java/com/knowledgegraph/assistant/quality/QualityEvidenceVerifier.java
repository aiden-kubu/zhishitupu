package com.knowledgegraph.assistant.quality;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.source.SourceLocator;

import java.util.Optional;

/** Deterministic identity, ownership and hash check. It does not judge semantic support. */
public final class QualityEvidenceVerifier {
    public record SourceCheck(String status, long chunkId, String expectedHash, String actualHash,
                              Integer unitIndex, String locator, String content, boolean truncated,
                              EvidenceRef evidence) {
        public boolean verifiedIdentity() { return status.equals("CURRENT") || status.equals("TRUNCATED"); }
    }

    private final QualityReadRepository repository;
    private final SourceLocator locator;

    public QualityEvidenceVerifier(QualityReadRepository repository, SourceLocator locator) {
        this.repository = repository;
        this.locator = locator;
    }

    public SourceCheck check(Scope scope, long documentId, long chunkId, String expectedHash,
                             String sourceId, int maxChars) {
        Optional<Long> owner = repository.chunkDocumentId(chunkId);
        if (owner.isEmpty()) return empty("MISSING", chunkId, expectedHash);
        if (owner.get() != documentId) return empty("WRONG_DOCUMENT", chunkId, expectedHash);
        Optional<SourceLocator.Chunk> found = locator.chunk(scope, documentId, chunkId, maxChars);
        if (found.isEmpty()) return empty("OUT_OF_SCOPE_OR_MISSING", chunkId, expectedHash);
        SourceLocator.Chunk chunk = found.get();
        if (chunk.chunkId() != chunkId) return empty("IDENTITY_MISMATCH", chunkId, expectedHash);
        Optional<QualityReadRepository.UnitMeta> meta = repository.unitMeta(scope, documentId, chunk.unitId());
        if (meta.isEmpty() || meta.get().unitId() != chunk.unitId()
                || meta.get().documentId() != documentId) return empty("UNIT_UNAVAILABLE", chunkId, expectedHash);
        if (chunk.contentHash() == null || !chunk.contentHash().equalsIgnoreCase(expectedHash)) {
            return new SourceCheck("STALE", chunkId, expectedHash, chunk.contentHash(),
                    meta.get().unitIndex(), meta.get().locator(), null, chunk.contentTruncated(), null);
        }
        String content = chunk.content() == null ? "" : chunk.content();
        String excerpt = content.substring(0, Math.min(content.length(), 200));
        EvidenceRef evidence = new EvidenceRef(sourceId, EvidenceKind.SOURCE,
                new ObjectRef("document_chunk", Long.toString(chunkId), chunk.contentHash()), scope,
                meta.get().locator(), excerpt.isBlank() ? null : excerpt);
        return new SourceCheck(chunk.contentTruncated() ? "TRUNCATED" : "CURRENT", chunkId,
                expectedHash, chunk.contentHash(), meta.get().unitIndex(), meta.get().locator(),
                content, chunk.contentTruncated(), evidence);
    }

    private static SourceCheck empty(String status, long chunkId, String expectedHash) {
        return new SourceCheck(status, chunkId, expectedHash, null, null, null, null, false, null);
    }
}
