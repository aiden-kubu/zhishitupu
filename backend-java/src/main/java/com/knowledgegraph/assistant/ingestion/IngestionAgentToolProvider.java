package com.knowledgegraph.assistant.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ten bounded, read-only ingestion tools. No method here starts processing or calls a model. */
public final class IngestionAgentToolProvider {
    private static final Set<String> JOB_STATUSES = Set.of("UPLOADED", "VALIDATING", "PARSING", "OCR_RUNNING",
            "CHUNKING", "AI_EXTRACTING", "AWAITING_REVIEW", "AI_REVIEWING", "IMPORTING",
            "COMPLETED", "FAILED", "CANCELLED");
    private static final Set<String> DOCUMENT_STATUSES = Set.of("UPLOADED", "PROCESSING", "AWAITING_REVIEW",
            "COMPLETED", "FAILED", "CANCELLED");
    private final IngestionReadRepository repository;
    private final SourceLocator source;
    private final ObjectMapper mapper;
    private final IngestionRecoveryAdvisor advisor = new IngestionRecoveryAdvisor();

    public IngestionAgentToolProvider(IngestionReadRepository repository, SourceLocator source, ObjectMapper mapper) {
        this.repository = repository; this.source = source; this.mapper = mapper;
    }

    public List<AssistantTool> tools() {
        return List.of(
                tool("list_libraries", Set.of("page", "pageSize"), (scope, a) -> {
                    int page = number(a, "page", 1, 1, 1000), size = number(a, "pageSize", 20, 1, 20);
                    var result = repository.libraries(scope, page, size);
                    List<Map<String, Object>> rows = result.items().stream().map(l -> libraryData(scope, l)).toList();
                    return paged(scope, rows, result.total(), page, size);
                }),
                tool("get_library", Set.of("libraryId"), (scope, a) -> {
                    long id = requiredId(a, "libraryId");
                    if (!libraryAllowed(scope, id)) return ToolResult.error(scope, "OUT_OF_SCOPE");
                    var library = repository.library(id);
                    if (library.isEmpty()) return ToolResult.error(scope, "NOT_FOUND");
                    return found(libraryScope(scope, id), libraryData(scope, library.get()), "library", id,
                            library.get().updatedAt() == null ? null : library.get().updatedAt().toString());
                }),
                tool("list_documents", Set.of("status", "page", "pageSize"), (scope, a) -> {
                    String status = status(a, true);
                    int page = number(a, "page", 1, 1, 1000), size = number(a, "pageSize", 10, 1, 20);
                    var result = repository.documents(scope, status, page, size);
                    return paged(scope, result.items().stream().map(d -> documentData(scope, d)).toList(), result.total(), page, size);
                }),
                tool("get_document", Set.of("documentId"), (scope, a) -> {
                    long id = requiredId(a, "documentId");
                    if (!documentAllowed(scope, id)) return ToolResult.error(scope, "OUT_OF_SCOPE_OR_NOT_FOUND");
                    var document = repository.document(id).orElseThrow();
                    return found(documentScope(scope, id), documentData(scope, document), "document", id,
                            document.updatedAt() == null ? null : document.updatedAt().toString());
                }),
                tool("get_document_unit", Set.of("documentId", "unitIndex", "chunkId", "expectedContentHash"), (scope, a) -> {
                    long documentId = requiredId(a, "documentId");
                    int unitIndex = number(a, "unitIndex", -1, 1, 1000000);
                    if (!documentAllowed(scope, documentId)) return ToolResult.error(scope, "OUT_OF_SCOPE_OR_NOT_FOUND");
                    var unit = source.unit(scope, documentId, unitIndex, 12, 1200);
                    if (unit.isEmpty()) return ToolResult.error(scope, "NOT_FOUND");
                    var value = unit.get();
                    List<SourceLocator.Chunk> chunks = value.chunks();
                    if (a.has("chunkId")) {
                        long chunkId = requiredId(a, "chunkId");
                        String expected = a.path("expectedContentHash").textValue();
                        var chunk = source.chunk(scope, documentId, chunkId, 2400);
                        if (chunk.isEmpty() || chunk.get().unitId() != value.unitId()) return ToolResult.error(scope, "NOT_FOUND");
                        if (!expected.equals(chunk.get().contentHash())) return ToolResult.error(scope, "STALE_SOURCE");
                        chunks = List.of(chunk.get());
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("documentId", documentId); data.put("unitId", value.unitId());
                    data.put("unitIndex", unitIndex); data.put("unitType", value.unitType());
                    data.put("sourceLocator", value.sourceLocator()); data.put("unitStatus", value.status());
                    data.put("chunks", chunks); data.put("totalChunks", value.totalChunks());
                    data.put("truncated", value.truncated() && !a.has("chunkId"));
                    Scope observed = documentScope(scope, documentId);
                    List<EvidenceRef> evidence = chunks.stream().map(c -> new EvidenceRef(
                            "document:" + documentId + ":chunk:" + c.chunkId() + ":" + c.contentHash(), EvidenceKind.SOURCE,
                            new ObjectRef("document_chunk", String.valueOf(c.chunkId()), c.contentHash()), observed,
                            value.sourceLocator(), c.content().substring(0, Math.min(c.content().length(), 2048)))).toList();
                    List<String> limitations = new ArrayList<>();
                    if (value.truncated() && !a.has("chunkId")) limitations.add("CHUNKS_TRUNCATED");
                    if (chunks.stream().anyMatch(SourceLocator.Chunk::contentTruncated)) limitations.add("CONTENT_TRUNCATED");
                    if (chunks.stream().anyMatch(c -> c.content().length() > 2048)) limitations.add("EVIDENCE_EXCERPT_TRUNCATED");
                    return result(observed, data, evidence, limitations);
                }),
                tool("list_jobs", Set.of("status", "page", "pageSize"), (scope, a) -> {
                    String status = status(a, false);
                    int page = number(a, "page", 1, 1, 1000), size = number(a, "pageSize", 10, 1, 20);
                    var result = repository.jobs(scope, status, page, size);
                    return paged(scope, result.items().stream().map(IngestionAgentToolProvider::jobData).toList(), result.total(), page, size);
                }),
                tool("get_job", Set.of("jobId"), (scope, a) -> {
                    long id = requiredId(a, "jobId");
                    var job = repository.job(id);
                    if (job.isEmpty()) return ToolResult.error(scope, "NOT_FOUND");
                    if (!documentAllowed(scope, job.get().documentId())) return ToolResult.error(scope, "OUT_OF_SCOPE");
                    return found(documentScope(scope, job.get().documentId()), jobData(job.get()), "ingestion_job", id, null);
                }),
                tool("get_organization", Set.of("documentId"), (scope, a) -> {
                    long id = requiredId(a, "documentId");
                    if (!documentAllowed(scope, id)) return ToolResult.error(scope, "OUT_OF_SCOPE_OR_NOT_FOUND");
                    var visible = repository.destinations(scope, id);
                    boolean truncated = visible.items().size() < visible.total();
                    return result(documentScope(scope, id), Map.of("documentId", id, "destinations", visible.items(),
                            "countMeaning", "assignedCandidateKeys, not committed node count",
                            "totalWithinScope", visible.total(), "truncated", truncated,
                            "selectedLibraryFilterApplied", !scope.libraryIds().isEmpty()), List.of(),
                            truncated ? List.of("DESTINATIONS_TRUNCATED") : List.of());
                }),
                tool("assess_recovery", Set.of("jobId"), (scope, a) -> {
                    long id = requiredId(a, "jobId");
                    var job = repository.job(id);
                    if (job.isEmpty()) return ToolResult.error(scope, "NOT_FOUND");
                    if (!documentAllowed(scope, job.get().documentId())) return ToolResult.error(scope, "OUT_OF_SCOPE");
                    var document = repository.document(job.get().documentId());
                    if (document.isEmpty()) return ToolResult.error(scope, "DOCUMENT_MISSING");
                    var assessment = advisor.assess(job.get(), document.get(), repository.visionConfigured(),
                            repository.hasChunks(document.get().id()), repository.hasOcrGaps(document.get().id()));
                    return result(documentScope(scope, job.get().documentId()), assessment, List.of(), assessment.limitations());
                }),
                tool("prepare_import", Set.of("targetLibraryId"), (scope, a) -> {
                    Long id = a.has("targetLibraryId") ? requiredId(a, "targetLibraryId") : null;
                    if (id != null) {
                        if (!libraryAllowed(scope, id)) return ToolResult.error(scope, "OUT_OF_SCOPE");
                        var library = repository.library(id);
                        if (library.isEmpty()) return ToolResult.error(scope, "NOT_FOUND");
                        if (!"active".equals(library.get().status())) return ToolResult.error(scope, "LIBRARY_NOT_ACTIVE");
                    }
                    if (id == null && scope.mode() == ScopeMode.SELECTED) return ToolResult.error(scope, "TARGET_LIBRARY_REQUIRED");
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("targetLibraryId", id); data.put("acceptedExtensions", List.of("pdf", "ppt", "pptx", "zip"));
                    data.put("maxUploadSizeMb", repository.maxUploadSizeMb());
                    data.put("visionConfigured", repository.visionConfigured());
                    data.put("navigationTarget", "/processing"); data.put("openImportModal", true);
                    data.put("afterUpload", "Start existing UPLOADED job; extraction may organize, review and import automatically");
                    data.put("executionAllowed", false);
                    return result(scope, data, List.of(), List.of("FILE_MUST_BE_SELECTED_IN_UI"));
                }));
    }

    private AssistantTool tool(String suffix, Set<String> keys, Operation operation) {
        return new AssistantTool() {
            @Override public ToolDescriptor descriptor() {
                return new ToolDescriptor("ingestion." + suffix, Set.of(Role.INGESTION), ToolEffect.READ_ONLY,
                        "Read-only ingestion " + suffix, argumentsSchema(suffix, keys));
            }
            @Override public void validateArguments(JsonNode args, Scope scope) {
                if (args == null || !args.isObject() || !keys.containsAll(toKeys(args))) throw new IllegalArgumentException("INVALID_ARGUMENTS");
                if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
                for (String key : List.of("libraryId", "documentId", "jobId", "targetLibraryId", "chunkId"))
                    if (args.has(key)) requiredId(args, key);
                if (args.has("page")) number(args, "page", -1, 1, 1000);
                if (args.has("pageSize")) number(args, "pageSize", -1, 1, 20);
                if (args.has("unitIndex")) number(args, "unitIndex", -1, 1, 1000000);
                if (args.has("status")) status(args, suffix.equals("list_documents"));
                if (args.has("expectedContentHash") && (!suffix.equals("get_document_unit") || !args.has("chunkId")))
                    throw new IllegalArgumentException("HASH_WITHOUT_CHUNK");
                if (args.has("chunkId") && (!args.has("expectedContentHash") || !args.path("expectedContentHash").isTextual()
                        || !args.path("expectedContentHash").textValue().matches("[0-9a-fA-F]{64}")))
                    throw new IllegalArgumentException("HASH_REQUIRED");
                if (Set.of("get_library", "get_document", "get_document_unit", "get_job", "get_organization", "assess_recovery")
                        .contains(suffix) && !args.has(suffix.equals("get_library") ? "libraryId" :
                        suffix.equals("get_job") || suffix.equals("assess_recovery") ? "jobId" : "documentId"))
                    throw new IllegalArgumentException("ID_REQUIRED");
                if (suffix.equals("get_document_unit") && !args.has("unitIndex")) throw new IllegalArgumentException("UNIT_REQUIRED");
                if (suffix.equals("get_library") && !libraryAllowed(scope, requiredId(args, "libraryId")))
                    throw new IllegalArgumentException("OUT_OF_SCOPE");
                if (suffix.equals("prepare_import") && args.has("targetLibraryId")
                        && !libraryAllowed(scope, requiredId(args, "targetLibraryId")))
                    throw new IllegalArgumentException("OUT_OF_SCOPE");
            }
            @Override public ToolResult execute(Context context, JsonNode args) { return operation.run(context.scope(), args); }
        };
    }

    private String argumentsSchema(String suffix, Set<String> keys) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        keys.stream().sorted().forEach(key -> {
            ObjectNode property = properties.putObject(key);
            switch (key) {
                case "libraryId", "documentId", "jobId", "targetLibraryId", "chunkId" -> {
                    property.put("type", "integer"); property.put("minimum", 1);
                }
                case "page" -> {
                    property.put("type", "integer"); property.put("minimum", 1); property.put("maximum", 1000);
                }
                case "pageSize" -> {
                    property.put("type", "integer"); property.put("minimum", 1); property.put("maximum", 20);
                }
                case "unitIndex" -> {
                    property.put("type", "integer"); property.put("minimum", 1); property.put("maximum", 1000000);
                }
                case "status" -> {
                    property.put("type", "string");
                    ArrayNode values = property.putArray("enum");
                    (suffix.equals("list_documents") ? DOCUMENT_STATUSES : JOB_STATUSES)
                            .stream().sorted().forEach(values::add);
                }
                case "expectedContentHash" -> {
                    property.put("type", "string"); property.put("pattern", "^[0-9a-fA-F]{64}$");
                    property.put("description", "Required with chunkId; both must be supplied together.");
                }
                default -> throw new IllegalArgumentException("UNKNOWN_SCHEMA_FIELD");
            }
        });
        ArrayNode required = schema.putArray("required");
        switch (suffix) {
            case "get_library" -> required.add("libraryId");
            case "get_document", "get_organization" -> required.add("documentId");
            case "get_document_unit" -> { required.add("documentId"); required.add("unitIndex"); }
            case "get_job", "assess_recovery" -> required.add("jobId");
            default -> { }
        }
        if (suffix.equals("get_document_unit")) {
            ObjectNode dependencies = schema.putObject("dependentRequired");
            dependencies.putArray("chunkId").add("expectedContentHash");
            dependencies.putArray("expectedContentHash").add("chunkId");
        }
        schema.put("additionalProperties", false);
        try { return mapper.writeValueAsString(schema); }
        catch (Exception ex) { throw new IllegalStateException("INVALID_TOOL_SCHEMA", ex); }
    }

    private boolean libraryAllowed(Scope scope, long id) {
        if (!scope.libraryIds().isEmpty() && !scope.libraryIds().contains(id)) return false;
        return scope.documentIds().isEmpty() || scope.documentIds().stream()
                .anyMatch(documentId -> repository.documentLibraries(documentId).contains(id));
    }
    private boolean documentAllowed(Scope scope, long id) {
        if (!scope.documentIds().isEmpty() && !scope.documentIds().contains(id)) return false;
        var document = repository.document(id);
        return document.isPresent() && (scope.libraryIds().isEmpty()
                || !java.util.Collections.disjoint(scope.libraryIds(), repository.documentLibraries(id)));
    }
    private static Scope documentScope(Scope requested, long documentId) {
        return new Scope(ScopeMode.SELECTED, requested.libraryIds(), Set.of(documentId), Set.of());
    }
    private static Scope libraryScope(Scope requested, long libraryId) {
        return new Scope(ScopeMode.SELECTED, Set.of(libraryId), requested.documentIds(), Set.of());
    }
    private static Set<String> toKeys(JsonNode args) {
        java.util.Set<String> keys = new java.util.HashSet<>(); args.fieldNames().forEachRemaining(keys::add); return keys;
    }
    private static long requiredId(JsonNode args, String key) {
        JsonNode value = args.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
            throw new IllegalArgumentException("INVALID_ID");
        return value.longValue();
    }
    private static int number(JsonNode args, String key, int fallback, int min, int max) {
        if (!args.has(key)) { if (fallback < min) throw new IllegalArgumentException("MISSING_NUMBER"); return fallback; }
        JsonNode value = args.get(key);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < min || value.intValue() > max)
            throw new IllegalArgumentException("INVALID_NUMBER");
        return value.intValue();
    }
    private static String status(JsonNode args, boolean documentStatus) {
        if (!args.has("status")) return null;
        String value = args.path("status").textValue();
        if (value == null || !(documentStatus ? DOCUMENT_STATUSES : JOB_STATUSES).contains(value))
            throw new IllegalArgumentException("INVALID_STATUS");
        return value;
    }
    private Map<String, Object> libraryData(Scope scope, IngestionReadRepository.Library library) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", library.id()); data.put("name", library.name()); data.put("type", library.type());
        data.put("description", library.description()); data.put("status", library.status());
        data.put("updatedAt", library.updatedAt() == null ? null : library.updatedAt().toString());
        if (scope.documentIds().isEmpty()) {
            data.put("documentCount", library.documentCount()); data.put("nodeCount", library.nodeCount());
            data.put("edgeCount", library.edgeCount());
        } else data.put("countsOmittedOutsideSelectedDocuments", true);
        data.put("aliases", library.aliases()); return data;
    }
    private Map<String, Object> documentData(Scope scope, IngestionReadRepository.Document document) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", document.id());
        boolean primaryVisible = scope.libraryIds().isEmpty() || scope.libraryIds().contains(document.libraryId());
        if (primaryVisible) data.put("libraryId", document.libraryId());
        else data.put("primaryLibraryOutsideSelectedScope", true);
        data.put("libraryIds", repository.documentLibraries(document.id()).stream()
                .filter(id -> scope.libraryIds().isEmpty() || scope.libraryIds().contains(id)).toList());
        data.put("originalName", document.originalName()); data.put("title", document.title());
        data.put("processingStatus", document.status()); data.put("lifecycleStatus", document.lifecycleStatus());
        data.put("extension", document.extension()); data.put("sizeBytes", document.sizeBytes());
        data.put("sha256", document.sha256()); data.put("updatedAt", document.updatedAt() == null ? null : document.updatedAt().toString());
        data.put("tags", document.tags()); data.put("sourceFilePresent", document.filePresent()); return data;
    }
    private static Map<String, Object> jobData(IngestionReadRepository.Job job) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", job.id()); data.put("documentId", job.documentId());
        data.put("documentName", job.documentName()); data.put("stage", job.stage());
        data.put("status", job.status()); data.put("progress", job.progress());
        data.put("processedUnits", job.processedUnits()); data.put("totalUnits", job.totalUnits());
        data.put("errorCode", job.errorCode()); data.put("errorMessageAvailable", job.errorMessage() != null);
        data.put("retryCount", job.retryCount()); data.put("createdAt", job.createdAt() == null ? null : job.createdAt().toString());
        data.put("finishedAt", job.finishedAt() == null ? null : job.finishedAt().toString()); data.put("candidateCount", job.candidateCount());
        data.put("stagedBatches", job.stagedBatches()); data.put("extractable", job.extractable());
        data.put("reviewable", job.reviewable());
        data.put("sourceWarningAvailable", job.sourceWarning() != null); return data;
    }
    private ToolResult paged(Scope scope, List<?> rows, long total, int page, int size) {
        long until = (long) (page - 1) * size + rows.size();
        boolean hasMore = until < total;
        boolean completeCoverage = page == 1 && rows.size() == total;
        return result(scope, Map.of("items", rows, "total", total, "page", page, "pageSize", size,
                "hasMore", hasMore, "completeCoverage", completeCoverage,
                "truncated", !completeCoverage), List.of(),
                completeCoverage ? List.of() : List.of("PAGINATED_RESULT"));
    }
    private ToolResult found(Scope scope, Object data, String kind, long id, String version) {
        EvidenceRef evidence = new EvidenceRef(kind + ":" + id + ":" + (version == null ? Instant.now().toEpochMilli() : version),
                EvidenceKind.SYSTEM_STATE, new ObjectRef(kind, String.valueOf(id), version), scope, null, null);
        return result(scope, data, List.of(evidence), version == null ? List.of("NO_STABLE_OBJECT_VERSION") : List.of());
    }
    private ToolResult result(Scope scope, Object data, List<EvidenceRef> evidence, List<String> limitations) {
        try {
            String json = mapper.writeValueAsString(data);
            return new ToolResult(limitations.isEmpty() ? ToolStatus.OK : ToolStatus.PARTIAL,
                    scope, json, evidence, limitations, null);
        } catch (Exception ignored) { return ToolResult.error(scope, "SERIALIZATION_FAILED"); }
    }

    @FunctionalInterface private interface Operation { ToolResult run(Scope scope, JsonNode args); }
}
