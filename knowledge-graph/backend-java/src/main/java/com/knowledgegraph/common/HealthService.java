package com.knowledgegraph.common;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：应用 UP 且数据库可达时返回 200。
 */
@Service
public class HealthService {

    private final JdbcClient jdbc;

    public HealthService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> health() {
        String dbState = "UP";
        try {
            jdbc.sql("SELECT 1").query((rs, i) -> 1).single();
        } catch (Exception ex) {
            dbState = "DOWN";
        }
        if ("DOWN".equals(dbState)) {
            throw new ApiException(503, ErrorCodes.DB_UNAVAILABLE, "数据库不可用");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "knowledge-graph-backend");
        data.put("database", dbState);
        data.put("timestamp", OffsetDateTime.now().toString());
        return data;
    }
}
