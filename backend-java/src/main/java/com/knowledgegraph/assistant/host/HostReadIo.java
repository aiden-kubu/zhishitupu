package com.knowledgegraph.assistant.host;

import java.time.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import com.knowledgegraph.common.ApiException;

/** Bounded blocking reads; no unbounded executor queue or implicit retry. */
final class HostReadIo implements AutoCloseable {
    private final Semaphore permits = new Semaphore(2);
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r,"assistant-read-io"); t.setDaemon(true); return t;
    });
    private volatile boolean closed;
    <T> T read(Instant deadline, BooleanSupplier cancelled, Callable<T> operation) {
        if (closed || !permits.tryAcquire()) throw HostJson.error(429,"RUN_CAPACITY_EXCEEDED");
        // Released by the executing job, not a timed-out caller: slow IO retains its slot.
        FutureTask<T> future = new FutureTask<>(operation) {
            @Override public void run() { try { super.run(); } finally { permits.release(); } }
        };
        try {
            pool.execute(future);
        } catch (RejectedExecutionException ex) { permits.release(); throw HostJson.error(503,"CAPABILITY_UNAVAILABLE"); }
        try {
            while (true) {
                if (cancelled.getAsBoolean()) { future.cancel(true); throw HostJson.error(403,"SCOPE_NOT_ALLOWED"); }
                long millis = Duration.between(Instant.now(),deadline).toMillis();
                if (millis <= 0) { future.cancel(true); throw HostJson.error(504,"DEADLINE_EXCEEDED"); }
                try { return future.get(Math.min(millis,50),TimeUnit.MILLISECONDS); }
                catch (TimeoutException ignored) { /* bounded polling for cancellation */ }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); future.cancel(true); throw HostJson.error(504,"DEADLINE_EXCEEDED");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof ApiException api) throw api;
            throw HostJson.error(500,"INTERNAL_ERROR");
        }
    }
    @Override public void close() { closed=true; pool.shutdownNow(); }
}
