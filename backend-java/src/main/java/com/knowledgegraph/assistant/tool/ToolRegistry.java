package com.knowledgegraph.assistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.runtime.DecisionParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Clock;

/** Only trusted, explicitly registered adapters can be invoked. W1 does not support write tools. */
public final class ToolRegistry {
    private record Entry(AssistantTool adapter, ToolDescriptor descriptor) {}
    private final Map<String, Entry> entries;
    private final DecisionParser parser = new DecisionParser();
    private final Clock clock;

    public ToolRegistry(List<AssistantTool> tools) {
        this(tools, Clock.systemUTC());
    }

    public ToolRegistry(List<AssistantTool> tools, Clock clock) {
        this.clock = clock;
        Map<String, Entry> registered = new LinkedHashMap<>();
        for (AssistantTool tool : tools) {
            ToolDescriptor descriptor = tool.descriptor();
            if (descriptor.effect() == ToolEffect.WRITE) throw new IllegalArgumentException("Write tools are disabled");
            if (!parser.json(descriptor.argumentsSchemaJson(), 8000).isObject()) {
                throw new IllegalArgumentException("Tool schema must be an object");
            }
            if (registered.putIfAbsent(descriptor.name(), new Entry(tool, descriptor)) != null) {
                throw new IllegalArgumentException("Duplicate tool name");
            }
        }
        entries = Map.copyOf(registered);
    }

    public void validate(AgentTask task) {
        for (String name : task.permittedTools()) {
            if (!allowed(task, name)) throw new IllegalArgumentException("Unknown or forbidden tool in plan");
        }
    }

    public List<ToolDescriptor> available(AgentTask task) {
        return task.permittedTools().stream().sorted().map(name -> entries.get(name).descriptor()).toList();
    }

    public boolean allowed(AgentTask task, String name) {
        Entry entry = entries.get(name);
        return entry != null && task.permittedTools().contains(name) && entry.descriptor().roles().contains(task.role());
    }

    /** Returns sanitized error codes, not adapter exception messages (which can contain credentials). */
    public ToolResult invoke(AgentTask task, String name, JsonNode arguments, AssistantTool.Context context) {
        if (!allowed(task, name)) return ToolResult.error(task.scope(), "TOOL_NOT_ALLOWED");
        Entry entry = entries.get(name);
        try {
            entry.adapter().validateArguments(arguments.deepCopy(), task.scope());
        } catch (RuntimeException ignored) {
            return ToolResult.error(task.scope(), "INVALID_TOOL_ARGUMENTS");
        }
        if (context.cancellationRequested().getAsBoolean()) return ToolResult.error(task.scope(), "CANCELLED");
        if (!clock.instant().isBefore(context.deadline())) return ToolResult.error(task.scope(), "DEADLINE_EXCEEDED");
        try {
            ToolResult result = entry.adapter().execute(context, arguments.deepCopy());
            validateResult(task, result);
            return result;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return ToolResult.error(task.scope(), "TOOL_INTERRUPTED");
        } catch (Exception ignored) {
            return ToolResult.error(task.scope(), "TOOL_RESULT_REJECTED_OR_FAILED");
        }
    }

    private void validateResult(AgentTask task, ToolResult result) {
        if (result == null || !result.effectiveScope().isWithin(task.scope()) || result.toString().length() > 64000
                || !parser.json(result.dataJson(), 32000).isObject()) {
            throw new IllegalArgumentException("Invalid tool result");
        }
        Map<String, EvidenceRef> evidence = new LinkedHashMap<>();
        for (EvidenceRef ref : result.evidence()) {
            if (!ref.scope().isWithin(result.effectiveScope()) || evidence.putIfAbsent(ref.sourceId(), ref) != null) {
                throw new IllegalArgumentException("Invalid evidence scope or duplicate source ID");
            }
        }
    }
}
