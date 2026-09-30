package com.knowledgegraph.assistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;

import java.time.Instant;
import java.util.function.BooleanSupplier;

public interface AssistantTool {
    ToolDescriptor descriptor();

    /** Must validate the concrete argument schema AND resolve object ownership against context.scope(). */
    void validateArguments(JsonNode arguments, Scope scope);

    /** No hidden writes/model calls; scope is host-supplied, never parsed from model instructions. */
    ToolResult execute(Context context, JsonNode arguments) throws Exception;

    record Context(String runId, String taskId, Role role, Scope scope, Instant deadline,
                   BooleanSupplier cancellationRequested) {}
}
