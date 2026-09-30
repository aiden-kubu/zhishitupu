package com.knowledgegraph.assistant.runtime;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;

import java.util.List;

/**
 * Trusted, read-only domain validation of allowed actions, parameter schema, target ownership and scope.
 * No validator registered for a role means ALL its action proposals are rejected.
 * Validation never grants execution permission; W1 artifacts remain drafts.
 */
@FunctionalInterface
public interface ProposalValidator {
    void validate(AgentTask task, ActionProposal proposal, List<EvidenceRef> referencedEvidence);
}
