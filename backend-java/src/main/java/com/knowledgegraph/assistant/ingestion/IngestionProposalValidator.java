package com.knowledgegraph.assistant.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.runtime.ProposalValidator;

import java.util.Set;

/** Validates drafts only. It never submits a domain operation. */
public final class IngestionProposalValidator implements ProposalValidator {
    private static final Set<String> ACTIONS = Set.of("ingestion.open_import", "ingestion.open_processing",
            "ingestion.start_process", "ingestion.retry_job", "ingestion.continue_ai_review",
            "ingestion.request_extraction", "ingestion.update_document_metadata");
    private final IngestionReadRepository repository;
    private final ObjectMapper mapper;
    private final IngestionRecoveryAdvisor advisor = new IngestionRecoveryAdvisor();

    public IngestionProposalValidator(IngestionReadRepository repository, ObjectMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }

    @Override public void validate(AgentTask task, ActionProposal proposal, java.util.List<EvidenceRef> evidence) {
        if (task.role() != Role.INGESTION || !ACTIONS.contains(proposal.actionType())
                || proposal.executionAllowed() || proposal.targets().size() != 1 || !task.scope().nodeIds().isEmpty())
            throw new IllegalArgumentException("INVALID_PROPOSAL");
        JsonNode params;
        try { params = mapper.readTree(proposal.parametersJson()); }
        catch (Exception ignored) { throw new IllegalArgumentException("INVALID_PARAMETERS"); }
        if (params == null || !params.isObject()) throw new IllegalArgumentException("INVALID_PARAMETERS");
        ObjectRef target = proposal.targets().get(0);
        long id;
        try { id = Long.parseLong(target.id()); }
        catch (NumberFormatException ignored) { throw new IllegalArgumentException("INVALID_TARGET"); }
        if (id <= 0) throw new IllegalArgumentException("INVALID_TARGET");
        switch (proposal.actionType()) {
            case "ingestion.open_import" -> {
                if (!target.kind().equals("library") || !keys(params, Set.of())) throw new IllegalArgumentException("INVALID_IMPORT_DRAFT");
                var library = repository.library(id).orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));
                if (!libraryAllowed(task.scope(), id) || !library.status().equals("active")) throw new IllegalArgumentException("OUT_OF_SCOPE");
            }
            case "ingestion.open_processing" -> {
                if (!target.kind().equals("ingestion_job") || !keys(params, Set.of())) throw new IllegalArgumentException("INVALID_NAVIGATION");
                job(task.scope(), id);
            }
            case "ingestion.update_document_metadata" -> {
                if (!target.kind().equals("document") || !keys(params, Set.of("title", "lifecycleStatus"))
                        || params.isEmpty() || !proposal.preconditions().contains("REFRESH_DOCUMENT_BEFORE_EXECUTION"))
                    throw new IllegalArgumentException("INVALID_METADATA_DRAFT");
                var document = document(task.scope(), id);
                if (target.version() != null && (document.updatedAt() == null || !target.version().equals(document.updatedAt().toString())))
                    throw new IllegalArgumentException("STALE_DOCUMENT");
                if (params.has("title") && (!params.path("title").isTextual()
                        || params.path("title").textValue().isBlank() || params.path("title").textValue().length() > 255))
                    throw new IllegalArgumentException("INVALID_TITLE");
                if (params.has("lifecycleStatus") && (!params.path("lifecycleStatus").isTextual()
                        || !Set.of("active", "archived", "outdated").contains(params.path("lifecycleStatus").textValue())))
                    throw new IllegalArgumentException("INVALID_LIFECYCLE");
            }
            default -> {
                if (!target.kind().equals("ingestion_job") || !keys(params, Set.of("stateToken"))
                        || !params.path("stateToken").isTextual()
                        || !proposal.preconditions().contains("REFRESH_JOB_AND_DOCUMENT_BEFORE_EXECUTION"))
                    throw new IllegalArgumentException("INVALID_JOB_DRAFT");
                var job = job(task.scope(), id);
                var document = document(task.scope(), job.documentId());
                var assessment = advisor.assess(job, document, repository.visionConfigured(),
                        repository.hasChunks(document.id()), repository.hasOcrGaps(document.id()));
                if (!assessment.stateToken().equals(params.path("stateToken").textValue()))
                    throw new IllegalArgumentException("STALE_JOB");
                String expected = switch (proposal.actionType()) {
                    case "ingestion.start_process" -> "start_process";
                    case "ingestion.retry_job" -> "retry_job";
                    case "ingestion.continue_ai_review" -> "continue_ai_review";
                    case "ingestion.request_extraction" -> "request_extraction";
                    default -> throw new IllegalArgumentException("INVALID_ACTION");
                };
                if (!expected.equals(assessment.suggestedAction())
                        || !proposal.impact().equals(assessment.requiredImpact()))
                    throw new IllegalArgumentException("MISSTATED_EFFECT");
            }
        }
    }

    private IngestionReadRepository.Job job(Scope scope, long id) {
        var job = repository.job(id).orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));
        document(scope, job.documentId());
        return job;
    }
    private IngestionReadRepository.Document document(Scope scope, long id) {
        var document = repository.document(id).orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));
        if (!scope.documentIds().isEmpty() && !scope.documentIds().contains(id)) throw new IllegalArgumentException("OUT_OF_SCOPE");
        if (!scope.libraryIds().isEmpty()
                && java.util.Collections.disjoint(scope.libraryIds(), repository.documentLibraries(id)))
            throw new IllegalArgumentException("OUT_OF_SCOPE");
        return document;
    }
    private boolean libraryAllowed(Scope scope, long id) {
        if (!scope.libraryIds().isEmpty() && !scope.libraryIds().contains(id)) return false;
        return scope.documentIds().isEmpty() || scope.documentIds().stream()
                .anyMatch(documentId -> repository.documentLibraries(documentId).contains(id));
    }
    private static boolean keys(JsonNode params, Set<String> allowed) {
        var names = params.fieldNames();
        while (names.hasNext()) if (!allowed.contains(names.next())) return false;
        return true;
    }
}
