package com.knowledgegraph.assistant.host;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.runtime.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Owns original handles, plans and Context observations, never client-reconstructed artifacts. */
final class AssistantRunStore implements AutoCloseable {
    record Captured(AgentTask task, List<Artifact> inputs, List<ToolObservation> observations) {}
    static final class Entry {
        final String owner, query, goal;
        final RunPlan plan;
        final Map<String,Captured> contexts = new ConcurrentHashMap<>();
        volatile String runId;
        volatile AssistantRuntime.RunHandle handle;
        volatile Instant expiresAt;
        volatile boolean tooLarge;
        Entry(String owner, RunPlan plan, String query, String goal) {
            this.owner=owner; this.plan=plan; this.query=query; this.goal=goal;
        }
        RunSnapshot snapshot() { return handle.snapshot(); }
        List<ToolObservation> observations(String task) {
            Captured value=contexts.get(task); return value==null?List.of():value.observations();
        }
        List<Artifact> inputs(String task) {
            Captured value=contexts.get(task); return value==null?List.of():value.inputs();
        }
        Artifact artifact(String id) {
            return snapshot().tasks().stream().map(TaskSnapshot::artifact).filter(Objects::nonNull)
                    .filter(a->a.artifactId().equals(id)).findFirst().orElseThrow(()->HostJson.error(404,"NOT_FOUND"));
        }
    }
    private final Map<String,Entry> tasks = new HashMap<>(), runs = new LinkedHashMap<>();
    private final LinkedHashMap<String,Instant> expired = new LinkedHashMap<>();
    private final AssistantReadHostProperties properties;
    private final Clock clock;
    private int reservations;
    AssistantRunStore(AssistantReadHostProperties properties, Clock clock) { this.properties=properties; this.clock=clock; }
    synchronized Entry reserve(String owner, RunPlan plan, String query, String goal) {
        sweep();
        long active=runs.values().stream().filter(e->e.expiresAt==null).count();
        if (active+reservations>=properties.maxActive() || runs.size()+reservations>=properties.maxRetained())
            throw HostJson.error(429,"RUN_CAPACITY_EXCEEDED");
        Entry entry=new Entry(owner,plan,query,goal);
        if(plan.tasks().stream().anyMatch(t->tasks.containsKey(t.taskId()))) throw new IllegalArgumentException("Duplicate plan");
        reservations++; plan.tasks().forEach(t->tasks.put(t.taskId(),entry)); return entry;
    }
    synchronized void abandon(Entry entry) {
        entry.plan.tasks().forEach(t->tasks.remove(t.taskId(),entry)); reservations--;
    }
    synchronized void attach(Entry e, AssistantRuntime.RunHandle handle) {
        RunSnapshot snapshot=handle.snapshot();
        if(e.runId!=null && !e.runId.equals(snapshot.runId())) throw new IllegalStateException("Run identity mismatch");
        e.runId=snapshot.runId(); e.handle=handle; runs.put(e.runId,e); reservations--;
        if(e.tooLarge) handle.cancel();
        handle.completion().whenComplete((value,error)->complete(e,value));
    }
    private synchronized void complete(Entry e, RunSnapshot value) {
        if(value==null) return;
        Instant finished=value.tasks().stream().map(TaskSnapshot::finishedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(clock.instant());
        e.expiresAt=finished.plus(properties.retention());
        if(bytes(e)>properties.maxRunBytes()) { e.tooLarge=true; e.handle.cancel(); }
    }
    synchronized void capture(DecisionProvider.Context context) {
        Entry e=tasks.get(context.task().taskId());
        if(e==null || e.plan.tasks().stream().noneMatch(context.task()::equals)) throw new IllegalArgumentException("Unregistered context");
        if(e.runId!=null && !e.runId.equals(context.runId())) throw new IllegalArgumentException("Cross run context");
        e.runId=context.runId();
        Set<String> producers=new HashSet<>();
        for(Artifact input:context.inputs()) {
            if(!context.task().dependencyIds().contains(input.producerTaskId()) || !producers.add(input.producerTaskId())
                    || tasks.get(input.producerTaskId())!=e || !input.authorizedScope().isWithin(context.task().scope()))
                throw new IllegalArgumentException("Non direct artifact");
            if(e.handle!=null && e.snapshot().tasks().stream().noneMatch(t->t.artifact()==input))
                throw new IllegalArgumentException("Artifact is not the original runtime result");
        }
        Captured before=e.contexts.put(context.task().taskId(),new Captured(context.task(),context.inputs(),context.observations()));
        if(bytes(e)>properties.maxRunBytes()) {
            if(before==null)e.contexts.remove(context.task().taskId());else e.contexts.put(context.task().taskId(),before);
            e.tooLarge=true; if(e.handle!=null)e.handle.cancel();
            throw new IllegalStateException("Host content memory limit");
        }
    }
    synchronized Optional<Artifact> direct(String runId,String consumer,String artifactId) {
        Entry e=tasks.get(consumer);
        if(e==null || !runId.equals(e.runId)) return Optional.empty();
        Captured c=e.contexts.get(consumer);
        if(c==null || c.task().role()!=Role.QUALITY_GOVERNANCE) return Optional.empty();
        return c.inputs().stream().filter(a->a.artifactId().equals(artifactId)
                && c.task().dependencyIds().contains(a.producerTaskId()) && tasks.get(a.producerTaskId())==e).findFirst();
    }
    synchronized String ownerOf(String taskId) {
        Entry e=tasks.get(taskId);return e==null?null:e.owner;
    }
    synchronized Entry get(String id) {
        sweep(); Entry entry=runs.get(id);
        if(entry==null) throw HostJson.error(expired.containsKey(id)?410:404,expired.containsKey(id)?"RUN_EXPIRED":"NOT_FOUND");
        return entry;
    }
    synchronized void sweep() {
        Instant now=clock.instant();
        Iterator<Map.Entry<String,Entry>> it=runs.entrySet().iterator();
        while(it.hasNext()) {
            Entry e=it.next().getValue();
            if(e.expiresAt==null && e.handle!=null) {
                RunSnapshot s=e.snapshot();
                if(s.status()!=RunStatus.RUNNING && s.status()!=RunStatus.CANCELLING)complete(e,s);
            }
            if(e.expiresAt!=null && !now.isBefore(e.expiresAt)) {
                it.remove(); e.plan.tasks().forEach(t->tasks.remove(t.taskId(),e));
                expired.put(e.runId,now.plus(properties.retention()));
            }
        }
        expired.entrySet().removeIf(e->!now.isBefore(e.getValue()));
        while(expired.size()>64)expired.remove(expired.keySet().iterator().next());
    }
    private long bytes(Entry e) {
        long size=0;
        for(Captured c:e.contexts.values()) {
            // Count UTF-8 structured content conservatively, including repeated direct inputs.
            size+=c.toString().getBytes(StandardCharsets.UTF_8).length;
        }
        if(e.handle!=null)size+=e.snapshot().toString().getBytes(StandardCharsets.UTF_8).length;
        return size;
    }
    @Override public synchronized void close() {
        runs.values().forEach(e->e.handle.cancel()); tasks.clear();runs.clear();expired.clear();
    }
}
