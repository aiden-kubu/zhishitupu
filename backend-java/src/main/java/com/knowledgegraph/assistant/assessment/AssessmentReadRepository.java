package com.knowledgegraph.assistant.assessment;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;

import java.time.Instant;
import java.util.List;

/** Legacy attempts are observations of client-submitted totals, never formal grades. */
public interface AssessmentReadRepository {
    record LegacyAttempt(long attemptId, long pathId, Long stepId, Long actualStepPathId,
                         int score, int correctCount, int totalCount, String weakTopicsJson,
                         Instant createdAt) {}
    record Page(List<LegacyAttempt> rows, boolean hasMore) {
        public Page { rows = List.copyOf(rows); }
    }

    Page legacyAttempts(Scope scope, long pathId, Long stepId, int limit, Long beforeAttemptId);
}
