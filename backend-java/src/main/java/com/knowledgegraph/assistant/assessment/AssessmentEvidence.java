package com.knowledgegraph.assistant.assessment;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.learning.LearningReadRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

final class AssessmentEvidence {
    private AssessmentEvidence() {}

    static EvidenceRef path(LearningReadRepository.Path path, Scope scope) {
        return new EvidenceRef("assessment:path:" + path.pathId() + ":" + path.snapshotFingerprint()
                + ":scope:" + scopeFingerprint(scope), EvidenceKind.SYSTEM_STATE,
                new ObjectRef("learning_path", Long.toString(path.pathId()), path.snapshotFingerprint()),
                scope, "snapshotFingerprint=" + path.snapshotFingerprint() + ";kind=read_observation_not_revision",
                "当前路径与阶段的只读快照");
    }

    static EvidenceRef legacyAttempt(AssessmentReadRepository.LegacyAttempt attempt, Scope scope) {
        return new EvidenceRef("assessment:legacy-attempt:" + attempt.attemptId() + ":path:"
                + attempt.pathId() + ":scope:" + scopeFingerprint(scope), EvidenceKind.SYSTEM_STATE,
                new ObjectRef("legacy_assessment_attempt", Long.toString(attempt.attemptId()), null),
                scope, "path=" + attempt.pathId() + ";step=" + attempt.stepId()
                + ";validity=legacy_unverified", "客户端汇总成绩记录，未经服务端正式判分");
    }

    static String scopeFingerprint(Scope scope) {
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
