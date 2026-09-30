package com.knowledgegraph.assistant.ingestion;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only persistence boundary. Implementations must filter selected scope in SQL, before LIMIT. */
public interface IngestionReadRepository {
    record Page<T>(List<T> items, long total, int page, int pageSize) {}
    record Library(long id, String name, String type, String description, String status,
                   LocalDateTime updatedAt, long documentCount, long nodeCount, long edgeCount, List<String> aliases) {}
    record Document(long id, long libraryId, String originalName, String title, String status,
                    String lifecycleStatus, String extension, long sizeBytes, String sha256,
                    LocalDateTime updatedAt, List<String> tags, boolean filePresent) {}
    record Job(long id, long documentId, String documentName, String stage, String status,
               int progress, int processedUnits, int totalUnits, String errorCode, String errorMessage,
               int retryCount, LocalDateTime createdAt, LocalDateTime finishedAt,
               long candidateCount, int stagedBatches, boolean extractable, boolean reviewable,
               String sourceWarning) {}
    record Destination(long libraryId, String name, int assignedCandidateKeys) {}

    Page<Library> libraries(Scope scope, int page, int size);
    Optional<Library> library(long id);
    Page<Document> documents(Scope scope, String status, int page, int size);
    Optional<Document> document(long id);
    /** Includes primary and topic-assignment libraries; no scope is inferred from a file name. */
    Set<Long> documentLibraries(long documentId);
    Page<Job> jobs(Scope scope, String status, int page, int size);
    Optional<Job> job(long id);
    boolean hasChunks(long documentId);
    boolean hasOcrGaps(long documentId);
    Page<Destination> destinations(Scope scope, long documentId);
    int maxUploadSizeMb();
    boolean visionConfigured();
}
