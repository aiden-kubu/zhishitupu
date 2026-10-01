package com.knowledgegraph.assistant.host;

import java.time.Duration;

/** Explicit server construction, not a configuration binding target. */
public record AssistantReadHostProperties(boolean enabled, boolean databaseEnabled, boolean modelEnabled,
                                         int maxActive, int maxRetained, Duration retention, long maxRunBytes) {
    public AssistantReadHostProperties {
        if (maxActive < 1 || maxActive > 8 || maxRetained < maxActive || maxRetained > 32
                || retention.isNegative() || retention.isZero() || retention.compareTo(Duration.ofSeconds(1800)) > 0
                || maxRunBytes < 1024 || maxRunBytes > 2L*1024*1024) throw new IllegalArgumentException("Invalid host limits");
    }
    public static AssistantReadHostProperties disabled() {
        return new AssistantReadHostProperties(false,false,false,8,32,Duration.ofSeconds(1800),2L*1024*1024);
    }
}
