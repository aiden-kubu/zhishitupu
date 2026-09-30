package com.knowledgegraph.assistant.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Computes advice from observations only. No branch invokes a domain write or a model. */
public final class IngestionRecoveryAdvisor {
    public record Assessment(long jobId, long documentId, String observedStatus, String observedStage,
                             String errorCode, long candidateCount, int stagedBatches,
                             String classification, String suggestedAction, String resumeScope,
                             boolean mayCallModel, boolean mayCreateLibraries, boolean mayWriteTags,
                             boolean mayAutoReview, boolean mayWriteGraph,
                             String requiredImpact, List<String> preconditions, List<String> limitations, String stateToken,
                             boolean executionAllowed) {}

    public Assessment assess(IngestionReadRepository.Job job, IngestionReadRepository.Document document,
                             boolean visionConfigured, boolean hasChunks, boolean hasOcrGaps) {
        String status = job.status();
        String classification;
        String action;
        String resume;
        boolean model = false;
        List<String> preconditions = new java.util.ArrayList<>();
        List<String> limitations = new java.util.ArrayList<>();
        if (!document.filePresent() && "UPLOADED".equals(status)) {
            classification = "not_recoverable"; action = "none"; resume = "none";
            limitations.add("SOURCE_FILE_MISSING");
        } else if ("UPLOADED".equals(status)) {
            classification = "ready_to_start"; action = "start_process";
            resume = "reparse_ocr_extract_review_auto_import"; model = true;
        } else if ("AWAITING_REVIEW".equals(status)) {
            classification = "awaiting_auto_review";
            action = job.reviewable() ? "continue_ai_review" : "none";
            resume = job.reviewable() ? "review_and_auto_import" : "none";
            model = job.reviewable();
            if (job.candidateCount() > 0 && !job.reviewable()) limitations.add("RELATION_ONLY_CANDIDATES_CANNOT_START_REVIEW");
        } else if (Set.of("FAILED", "CANCELLED").contains(status)) {
            if (job.reviewable()) {
                classification = "review_recoverable"; action = "continue_ai_review";
                resume = "review_and_auto_import"; model = true;
            } else if (job.candidateCount() > 0) {
                classification = "candidate_state_invalid"; action = "none"; resume = "none";
                limitations.add("RELATION_ONLY_CANDIDATES_CANNOT_START_REVIEW");
            } else if (hasChunks && !hasOcrGaps) {
                classification = "extraction_recoverable"; action = "retry_job";
                resume = "extraction_from_checkpoint_then_review_and_auto_import"; model = true;
                if (job.stagedBatches() > 0) limitations.add("STAGED_BATCHES_REUSED_ONLY_IF_SIGNATURE_MATCHES");
            } else if (!document.filePresent()) {
                classification = "not_recoverable"; action = "none"; resume = "none";
                limitations.add("SOURCE_FILE_MISSING_AND_REPARSE_REQUIRED");
            } else if ("DOCUMENT_OCR_REQUIRED".equals(job.errorCode()) && !visionConfigured) {
                classification = "configuration_required"; action = "open_settings";
                resume = "none"; preconditions.add("CONFIGURE_VISION_MODEL");
            } else {
                classification = "full_retry_may_be_required"; action = "retry_job";
                resume = "server_decides_at_execution"; model = true;
                if (job.stagedBatches() > 0) limitations.add("STAGED_BATCHES_MAY_BE_REUSED_IF_SIGNATURE_MATCHES");
            }
        } else if ("COMPLETED".equals(status)) {
            if (job.extractable()) {
                classification = "extraction_recoverable"; action = "request_extraction";
                resume = "reuse_chunks_then_review_and_auto_import"; model = true;
            } else {
                classification = "completed"; action = "none"; resume = "none";
            }
        } else if (Set.of("VALIDATING", "PARSING", "OCR_RUNNING", "CHUNKING", "AI_EXTRACTING",
                "AI_REVIEWING", "IMPORTING").contains(status)) {
            classification = "in_progress"; action = "wait"; resume = "none";
            limitations.add("NO_HEARTBEAT_OR_LEASE_CANNOT_CONFIRM_WORKER_ALIVE");
            limitations.add("CANCEL_IS_COOPERATIVE_AND_DOES_NOT_REVERSE_COMMITTED_WRITES");
        } else {
            classification = "stale_state"; action = "none"; resume = "none";
            limitations.add("UNKNOWN_JOB_STATUS");
        }
        boolean writes = model || "start_process".equals(action) || "continue_ai_review".equals(action);
        if (writes) {
            preconditions.add("REFRESH_JOB_AND_DOCUMENT_BEFORE_EXECUTION");
            limitations.add("MODEL_CALLS_MAY_CONTINUE_AFTER_CHAT_CANCEL");
            limitations.add("PROCESSING_MAY_AUTOMATICALLY_REVIEW_AND_IMPORT_GRAPH_DATA");
        }
        boolean autoOrganize = writes && !"review_and_auto_import".equals(resume);
        String requiredImpact = impactDisclosure(model, autoOrganize, autoOrganize, writes, writes);
        return new Assessment(job.id(), job.documentId(), status, job.stage(), job.errorCode(),
                job.candidateCount(), job.stagedBatches(), classification, action, resume, model,
                autoOrganize, autoOrganize, writes, writes, requiredImpact,
                List.copyOf(preconditions), List.copyOf(limitations),
                token(job, document, visionConfigured, hasChunks, hasOcrGaps), false);
    }

    private static String impactDisclosure(boolean model, boolean libraries, boolean tags,
                                           boolean review, boolean graph) {
        List<String> effects = new java.util.ArrayList<>();
        if (model) effects.add("可能调用模型");
        if (libraries) effects.add("可能创建知识库");
        if (tags) effects.add("可能写入标签");
        if (review) effects.add("可能自动复审");
        if (graph) effects.add("可能写入图谱并入库");
        return effects.isEmpty() ? "未执行草稿；没有建议执行的写操作。"
                : "未执行草稿；若执行，" + String.join("、", effects) + "。";
    }

    private static String token(IngestionReadRepository.Job job, IngestionReadRepository.Document document,
                                boolean visionConfigured, boolean hasChunks, boolean hasOcrGaps) {
        String snapshot = job.id() + "|" + job.status() + "|" + job.stage() + "|" + job.errorCode()
                + "|" + job.retryCount() + "|" + job.candidateCount() + "|" + job.reviewable()
                + "|" + job.extractable() + "|" + job.stagedBatches() + "|" + job.finishedAt()
                + "|" + document.id() + "|" + document.status() + "|" + document.updatedAt()
                + "|" + document.filePresent() + "|" + visionConfigured + "|" + hasChunks + "|" + hasOcrGaps;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
