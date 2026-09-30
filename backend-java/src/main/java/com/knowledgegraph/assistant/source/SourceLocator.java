package com.knowledgegraph.assistant.source;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;

import java.util.List;
import java.util.Optional;

/** Stable read of parsed source. A chunk index is display order, never an identity. */
public interface SourceLocator {
    record Chunk(long chunkId, long unitId, int chunkIndex, String contentHash, int startOffset, int endOffset,
                 String content, boolean contentTruncated) {}
    record Unit(long documentId, long unitId, int unitIndex, String unitType, String sourceLocator,
                String status, List<Chunk> chunks, int totalChunks, boolean truncated) {}

    Optional<Unit> unit(Scope scope, long documentId, int unitIndex, int maxChunks, int maxCharsPerChunk);
    Optional<Chunk> chunk(Scope scope, long documentId, long chunkId, int maxChars);
}
