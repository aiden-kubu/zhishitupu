package com.knowledgegraph.settings;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置（§13 SettingsService）：
 * 读取时对密钥只返回掩码；保存时密钥加密写入 system_settings；
 * 连接测试在未配置时返回 503 LLM_NOT_CONFIGURED，绝不回显完整 Key。
 */
@Service
public class SettingsService {

    public static final String KEY_LLM_BASE_URL = "llm.baseUrl";
    public static final String KEY_LLM_MODEL = "llm.model";
    public static final String KEY_LLM_API_KEY = "llm.apiKey";
    public static final String KEY_LLM_TIMEOUT_MS = "llm.timeoutMs";
    public static final String KEY_LLM_MAX_OUTPUT_TOKENS = "llm.maxOutputTokens";
    public static final String KEY_LLM_VISION = "llm.vision";
    public static final String KEY_OCR_MODE = "ocr.mode";
    public static final String KEY_OCR_DATA_PATH = "ocr.dataPath";
    public static final String KEY_STORAGE_MAX_UPLOAD_MB = "storage.maxUploadSizeMb";
    public static final String KEY_GRAPH_DEFAULT_DEPTH = "graph.defaultDepth";
    public static final String KEY_GRAPH_MAX_NODES = "graph.maxNodes";

    public record SaveRequest(Llm llm, Ocr ocr, Storage storage, Graph graph) {
        public record Llm(String baseUrl, String model, String apiKey, Integer timeoutMs,
                          Integer maxOutputTokens, Boolean vision) {
        }

        public record Ocr(String mode, String dataPath) {
        }

        public record Storage(Integer maxUploadSizeMb) {
        }

        public record Graph(Integer defaultDepth, Integer maxNodes) {
        }
    }

    private final JdbcClient jdbc;
    private final SecretCipher cipher;
    private final AppProperties appProperties;

    @Value("${kg.llm.base-url:}")
    private String envLlmBaseUrl;
    @Value("${kg.llm.api-key:}")
    private String envLlmApiKey;
    @Value("${kg.llm.model:}")
    private String envLlmModel;
    @Value("${kg.llm.timeout-ms:30000}")
    private long envLlmTimeoutMs;
    @Value("${kg.llm.max-output-tokens:2048}")
    private int envLlmMaxOutputTokens;
    @Value("${kg.ocr.mode:}")
    private String envOcrMode;
    @Value("${kg.ocr.data-path:}")
    private String envOcrDataPath;

    public SettingsService(JdbcClient jdbc, SecretCipher cipher, AppProperties appProperties) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.appProperties = appProperties;
    }

    /** 掩码后的系统配置视图。 */
    public Map<String, Object> view() {
        Map<String, Object> llm = new LinkedHashMap<>();
        String baseUrl = effective(KEY_LLM_BASE_URL, envLlmBaseUrl);
        String model = effective(KEY_LLM_MODEL, envLlmModel);
        String apiKey = effectiveSecret(KEY_LLM_API_KEY, envLlmApiKey);
        llm.put("baseUrl", baseUrl);
        llm.put("model", model);
        llm.put("apiKeyConfigured", !apiKey.isBlank());
        llm.put("apiKeyMasked", apiKey.isBlank() ? null : mask(apiKey));
        llm.put("timeoutMs", parseInt(effective(KEY_LLM_TIMEOUT_MS, String.valueOf(envLlmTimeoutMs)), (int) envLlmTimeoutMs));
        llm.put("maxOutputTokens", parseInt(effective(KEY_LLM_MAX_OUTPUT_TOKENS, String.valueOf(envLlmMaxOutputTokens)), envLlmMaxOutputTokens));
        llm.put("vision", Boolean.parseBoolean(effective(KEY_LLM_VISION, "false")));

        Map<String, Object> ocr = new LinkedHashMap<>();
        String ocrMode = effective(KEY_OCR_MODE, envOcrMode);
        ocr.put("mode", ocrMode);
        ocr.put("dataPath", effective(KEY_OCR_DATA_PATH, envOcrDataPath));
        ocr.put("configured", !ocrMode.isBlank());

        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("root", appProperties.getStorage().getRoot());
        storage.put("maxUploadSizeMb", parseInt(effective(KEY_STORAGE_MAX_UPLOAD_MB, "200"), 200));

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("defaultDepth", parseInt(effective(KEY_GRAPH_DEFAULT_DEPTH, "1"), 1));
        graph.put("maxNodes", parseInt(effective(KEY_GRAPH_MAX_NODES, "300"), 300));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("llm", llm);
        view.put("ocr", ocr);
        view.put("storage", storage);
        view.put("graph", graph);
        return view;
    }

    /** 上传大小上限（MB），供上传校验动态读取。 */
    public int maxUploadSizeMb() {
        return parseInt(effective(KEY_STORAGE_MAX_UPLOAD_MB, "200"), 200);
    }

    /** 保存配置；apiKey 为空/未提供时保持原值，非空时加密存储。 */
    @Transactional
    public Map<String, Object> save(SaveRequest req) {
        if (req.llm() != null) {
            upsert(KEY_LLM_BASE_URL, req.llm().baseUrl(), false);
            upsert(KEY_LLM_MODEL, req.llm().model(), false);
            if (req.llm().apiKey() != null && !req.llm().apiKey().isBlank()) {
                upsert(KEY_LLM_API_KEY, req.llm().apiKey().trim(), true);
            } else if (req.llm().apiKey() != null) {
                deleteKey(KEY_LLM_API_KEY); // 空字符串表示清除已保存密钥
            }
            if (req.llm().timeoutMs() != null) {
                upsert(KEY_LLM_TIMEOUT_MS, String.valueOf(req.llm().timeoutMs()), false);
            }
            if (req.llm().maxOutputTokens() != null) {
                upsert(KEY_LLM_MAX_OUTPUT_TOKENS, String.valueOf(req.llm().maxOutputTokens()), false);
            }
            if (req.llm().vision() != null) {
                upsert(KEY_LLM_VISION, String.valueOf(req.llm().vision()), false);
            }
        }
        if (req.ocr() != null) {
            upsert(KEY_OCR_MODE, req.ocr().mode(), false);
            upsert(KEY_OCR_DATA_PATH, req.ocr().dataPath(), false);
        }
        if (req.storage() != null && req.storage().maxUploadSizeMb() != null) {
            upsert(KEY_STORAGE_MAX_UPLOAD_MB, String.valueOf(req.storage().maxUploadSizeMb()), false);
        }
        if (req.graph() != null) {
            if (req.graph().defaultDepth() != null) {
                upsert(KEY_GRAPH_DEFAULT_DEPTH, String.valueOf(req.graph().defaultDepth()), false);
            }
            if (req.graph().maxNodes() != null) {
                upsert(KEY_GRAPH_MAX_NODES, String.valueOf(req.graph().maxNodes()), false);
            }
        }
        return view();
    }

    /** 测试 LLM 连接：未配置返回 503 LLM_NOT_CONFIGURED。 */
    public Map<String, Object> testLlm() {
        String baseUrl = effective(KEY_LLM_BASE_URL, envLlmBaseUrl);
        String apiKey = effectiveSecret(KEY_LLM_API_KEY, envLlmApiKey);
        String model = effective(KEY_LLM_MODEL, envLlmModel);
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new ApiException(503, ErrorCodes.LLM_NOT_CONFIGURED,
                    "LLM 尚未配置：请在系统设置中填写 API 地址、模型与密钥");
        }
        long start = System.currentTimeMillis();
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                    .build();
            String response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "max_tokens", 8,
                            "messages", List.of(Map.of("role", "user", "content", "ping"))))
                    .retrieve()
                    .body(String.class);
            long latency = System.currentTimeMillis() - start;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("model", model);
            result.put("latencyMs", latency);
            result.put("message", "连接成功");
            result.put("responseBytes", response == null ? 0 : response.length());
            return result;
        } catch (Exception ex) {
            String reason = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
            if (reason.length() > 200) {
                reason = reason.substring(0, 200);
            }
            throw new ApiException(502, ErrorCodes.LLM_UNREACHABLE, "无法连接 LLM 服务（" + reason + "）");
        }
    }

    private String effective(String key, String envFallback) {
        String stored = readStored(key, false);
        return stored != null && !stored.isBlank() ? stored : envFallback;
    }

    private String effectiveSecret(String key, String envFallback) {
        String stored = readStored(key, true);
        return stored != null && !stored.isBlank() ? stored : envFallback;
    }

    private String readStored(String key, boolean secret) {
        Map<String, Object> row = jdbc.sql("SELECT encrypted_value, is_secret FROM system_settings WHERE setting_key = :key")
                .param("key", key)
                .query((rs, i) -> Map.<String, Object>of(
                        "value", rs.getString("encrypted_value") == null ? "" : rs.getString("encrypted_value"),
                        "secret", rs.getBoolean("is_secret")))
                .optional()
                .orElse(null);
        if (row == null) {
            return "";
        }
        String value = (String) row.get("value");
        if (Boolean.TRUE.equals(row.get("secret")) || secret) {
            return cipher.decrypt(value);
        }
        return value;
    }

    private void upsert(String key, String value, boolean secret) {
        if (value == null) {
            return;
        }
        String stored = secret ? cipher.encrypt(value.trim()) : value.trim();
        jdbc.sql("""
                        INSERT INTO system_settings (setting_key, encrypted_value, is_secret)
                        VALUES (:key, :value, :secret)
                        ON DUPLICATE KEY UPDATE encrypted_value = VALUES(encrypted_value), is_secret = VALUES(is_secret)
                        """)
                .param("key", key)
                .param("value", stored)
                .param("secret", secret)
                .update();
    }

    private void deleteKey(String key) {
        jdbc.sql("DELETE FROM system_settings WHERE setting_key = :key").param("key", key).update();
    }

    private static String mask(String secret) {
        if (secret.length() <= 4) {
            return "****";
        }
        return secret.substring(0, 2) + "****" + secret.substring(secret.length() - 2);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
