package com.knowledgegraph.assistant.host;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import java.util.Objects;
import java.util.function.Predicate;

/** Trusted server state only. No request/profile/header can construct this authority. */
public record AssistantReadAccessContext(String owner, Scope grant, Predicate<Scope> currentPermission) {
    public AssistantReadAccessContext {
        com.knowledgegraph.assistant.contract.AssistantContracts.identifier(owner);
        Objects.requireNonNull(grant); Objects.requireNonNull(currentPermission);
        if (grant.mode()!=ScopeMode.SELECTED || grant.libraryIds().isEmpty() || !grant.nodeIds().isEmpty())
            throw new IllegalArgumentException("An explicit read grant is required");
    }
    public boolean allows(Scope scope) { return scope.isWithin(grant) && currentPermission.test(scope); }
}
