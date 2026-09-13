package com.knowledgegraph.settings;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 模型档案（多模型支持）：
 * 每个档案对应一个 OpenAI 兼容模型接入；密钥 AES-GCM 加密存储、接口只返回掩码；
 * is_default 供后续抽取/问答任务选择默认模型，vision 标记视觉能力（OCR 路由依据）。
 */
@Service
public class LlmProfileService {

    public record LlmProfileView(
            long id, String name, String baseUrl, String model,
            boolean apiKeyConfigured, String apiKeyMasked,
            int timeoutMs, int maxOutputTokens,
            boolean vision, boolean enabled, boolean isDefault) {
    }

    public record LlmProfileSaveRequest(
            String name, String baseUrl, String model, String apiKey,
            Integer timeoutMs, Integer maxOutputTokens, Boolean vision, Boolean enabled) {
    }

    private final JdbcClient jdbc;
    private final SecretCipher cipher;

    public LlmProfileService(JdbcClient jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    public List<LlmProfileView> list() {
        return jdbc.sql("""
                        SELECT id, name, base_url, model, encrypted_api_key, timeout_ms,
                               max_output_tokens, vision, enabled, is_default
                        FROM llm_profiles ORDER BY is_default DESC, id ASC
                        """)
                .query((rs, i) -> new LlmProfileView(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("base_url"),
                        rs.getString("model"),
                        rs.getString("encrypted_api_key") != null,
                        mask(Optional.ofNullable(rs.getString("encrypted_api_key")).map(cipher::decrypt).orElse("")),
                        rs.getInt("timeout_ms"),
                        rs.getInt("max_output_tokens"),
                        rs.getBoolean("vision"),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("is_default")))
                .list();
    }

    @Transactional
    public LlmProfileView create(LlmProfileSaveRequest req) {
        boolean first = count() == 0;
        String name = req.name().trim();
        try {
            jdbc.sql("""
                            INSERT INTO llm_profiles
                                (name, base_url, model, encrypted_api_key, timeout_ms, max_output_tokens, vision, enabled, is_default)
                            VALUES (:name, :baseUrl, :model, :apiKey, :timeoutMs, :maxOutputTokens, :vision, :enabled, :isDefault)
                            """)
                    .param("name", name)
                    .param("baseUrl", req.baseUrl().trim())
                    .param("model", req.model().trim())
                    .param("apiKey", encryptKey(req.apiKey()))
                    .param("timeoutMs", req.timeoutMs() != null ? req.timeoutMs() : 30000)
                    .param("maxOutputTokens", req.maxOutputTokens() != null ? req.maxOutputTokens() : 2048)
                    .param("vision", Boolean.TRUE.equals(req.vision()))
                    .param("enabled", req.enabled() == null || req.enabled())
                    .param("isDefault", first)
                    .update();
            // name 有唯一约束，回查生成的主键
            Long id = jdbc.sql("SELECT id FROM llm_profiles WHERE name = :name")
                    .param("name", name)
                    .query(Long.class)
                    .single();
            return get(id);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(409, ErrorCodes.CONFLICT, "模型名称「" + name + "」已存在");
        }
    }

    @Transactional
    public LlmProfileView update(long id, LlmProfileSaveRequest req) {
        requireExists(id);
        try {
            int updated = jdbc.sql("""
                            UPDATE llm_profiles
                            SET name = :name, base_url = :baseUrl, model = :model,
                                encrypted_api_key = CASE
                                    WHEN :apiKey IS NULL THEN encrypted_api_key
                                    WHEN :apiKey = '' THEN NULL
                                    ELSE :apiKey END,
                                timeout_ms = :timeoutMs, max_output_tokens = :maxOutputTokens,
                                vision = :vision, enabled = :enabled
                            WHERE id = :id
                            """)
                    .param("name", req.name().trim())
                    .param("baseUrl", req.baseUrl().trim())
                    .param("model", req.model().trim())
                    // apiKey==null 保持原值；空字符串表示清除密钥；非空则加密覆盖
                    .param("apiKey", req.apiKey() == null ? null
                            : req.apiKey().isBlank() ? "" : cipher.encrypt(req.apiKey().trim()))
                    .param("timeoutMs", req.timeoutMs() != null ? req.timeoutMs() : 30000)
                    .param("maxOutputTokens", req.maxOutputTokens() != null ? req.maxOutputTokens() : 2048)
                    .param("vision", Boolean.TRUE.equals(req.vision()))
                    .param("enabled", req.enabled() == null || req.enabled())
                    .param("id", id)
                    .update();
            if (updated != 1) {
                throw new ApiException(404, ErrorCodes.NOT_FOUND, "模型档案不存在");
            }
            return get(id);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(409, ErrorCodes.CONFLICT, "模型名称「" + req.name().trim() + "」已存在");
        }
    }

    @Transactional
    public void delete(long id) {
        requireExists(id);
        boolean wasDefault = get(id).isDefault();
        jdbc.sql("DELETE FROM llm_profiles WHERE id = :id").param("id", id).update();
        if (wasDefault) {
            // 删除默认档案后，提升剩余最早一个为默认
            jdbc.sql(
                    "UPDATE llm_profiles SET is_default = 1 WHERE id = (SELECT id FROM (SELECT id FROM llm_profiles ORDER BY id ASC LIMIT 1) t)")
                    .update();
        }
    }

    @Transactional
    public LlmProfileView setDefault(long id) {
        requireExists(id);
        jdbc.sql("UPDATE llm_profiles SET is_default = 0 WHERE is_default = 1").update();
        jdbc.sql("UPDATE llm_profiles SET is_default = 1, enabled = 1 WHERE id = :id").param("id", id).update();
        return get(id);
    }

    /** 用该档案的接入参数做连通性测试；未配置密钥返回 503。必须拿到非空 choices[0].message.content 才算成功。 */
    public Map<String, Object> test(long id) {
        LlmProfileView profile = get(id);
        String apiKey = readApiKey(id);
        if (profile.baseUrl().isBlank() || apiKey.isBlank()) {
            throw new ApiException(503, ErrorCodes.LLM_NOT_CONFIGURED,
                    "该模型尚未配置 API Key，请编辑档案填写后重试");
        }
        long start = System.currentTimeMillis();
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(profile.baseUrl().endsWith("/") ? profile.baseUrl().substring(0, profile.baseUrl().length() - 1) : profile.baseUrl())
                    .build();
            Map<?, ?> response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", profile.model(),
                            // 思考型模型（如 GLM-5.x）会先消耗 token 于思维链，预算过小会导致 content 为空
                            "max_tokens", 512,
                            "messages", List.of(Map.of("role", "user", "content", "ping"))))
                    .retrieve()
                    .body(Map.class);
            long latency = System.currentTimeMillis() - start;
            // 响应体非空不等于可用：必须存在非空 choices[0].message.content
            com.knowledgegraph.chat.LlmClient.extractContent(response);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("model", profile.model());
            result.put("latencyMs", latency);
            result.put("message", "连接成功");
            return result;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            throw com.knowledgegraph.chat.LlmClient.redactApiKey(
                    com.knowledgegraph.chat.LlmClient.mapUpstreamError(ex), apiKey);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(502, ErrorCodes.LLM_UNREACHABLE,
                    "无法连接 LLM 服务，请检查网络与 API 地址");
        }
    }

    /** 供内部任务使用的视觉模型接入参数（解密后的 Key 只留在服务端，绝不返回前端）。 */
    public record VisionModel(long profileId, String baseUrl, String model, String apiKey,
                              int timeoutMs, int maxOutputTokens) {
    }

    /**
     * 供内部问答/抽取使用的默认模型接入参数：解密后的 Key 只在后端内存中使用，
     * 绝不通过 Controller 返回。temperature 无档案字段，问答场景固定低温度。
     */
    public record DefaultModel(long profileId, String baseUrl, String model, String apiKey,
                               int timeoutSeconds, int maxTokens, double temperature) {
    }

    private record DefaultProfileRow(long id, String baseUrl, String model, String encryptedKey,
                                     int timeoutMs, int maxOutputTokens, boolean enabled) {
    }

    /**
     * 取「默认且启用」的模型档案（节点问答与文本抽取的取用规则，2026-09-13 用户确认）。
     * 没有默认 / 默认被禁用 / Key 缺失或解密失败 → 统一 LLM_NOT_CONFIGURED 业务错误。
     */
    public DefaultModel requireDefaultEnabledProfile() {
        DefaultProfileRow row = jdbc.sql("""
                        SELECT id, base_url, model, encrypted_api_key, timeout_ms, max_output_tokens, enabled
                        FROM llm_profiles WHERE is_default = 1 ORDER BY id ASC LIMIT 1
                        """)
                .query((rs, i) -> new DefaultProfileRow(
                        rs.getLong("id"), rs.getString("base_url"), rs.getString("model"),
                        rs.getString("encrypted_api_key"), rs.getInt("timeout_ms"),
                        rs.getInt("max_output_tokens"), rs.getBoolean("enabled")))
                .optional()
                .orElse(null);
        if (row == null) {
            throw new ApiException(503, ErrorCodes.LLM_NOT_CONFIGURED,
                    "尚未配置默认模型，请在「系统设置 → 模型档案」中添加模型并设为默认");
        }
        if (!row.enabled()) {
            throw new ApiException(503, ErrorCodes.LLM_NOT_CONFIGURED, "默认模型已被禁用，请先启用后再试");
        }
        String apiKey = row.encryptedKey() == null ? "" : cipher.decrypt(row.encryptedKey());
        if (row.baseUrl() == null || row.baseUrl().isBlank() || apiKey.isBlank()) {
            throw new ApiException(503, ErrorCodes.LLM_NOT_CONFIGURED,
                    "默认模型未配置有效的 API 地址或 API Key");
        }
        return new DefaultModel(row.id(), row.baseUrl(), row.model(), apiKey,
                Math.max(1, row.timeoutMs() / 1000), row.maxOutputTokens(), 0.3);
    }

    /** 取一个已启用且具备视觉能力的模型档案（默认优先，其次按创建顺序）。 */
    public VisionModel findEnabledVisionModel() {
        return jdbc.sql("""
                        SELECT id, base_url, model, encrypted_api_key, timeout_ms, max_output_tokens
                        FROM llm_profiles WHERE enabled = 1 AND vision = 1
                        ORDER BY is_default DESC, id ASC LIMIT 1
                        """)
                .query((rs, i) -> new VisionModel(
                        rs.getLong("id"),
                        rs.getString("base_url"),
                        rs.getString("model"),
                        java.util.Optional.ofNullable(rs.getString("encrypted_api_key")).map(cipher::decrypt).orElse(""),
                        rs.getInt("timeout_ms"),
                        rs.getInt("max_output_tokens")))
                .optional()
                .orElse(null);
    }

    private long count() {
        Long value = jdbc.sql("SELECT COUNT(*) FROM llm_profiles").query(Long.class).single();
        return value == null ? 0 : value;
    }

    private void requireExists(long id) {
        Long value = jdbc.sql("SELECT COUNT(*) FROM llm_profiles WHERE id = :id").param("id", id)
                .query(Long.class).single();
        if (value == null || value != 1) {
            throw new ApiException(404, ErrorCodes.NOT_FOUND, "模型档案不存在");
        }
    }

    private LlmProfileView get(long id) {
        return list().stream().filter(profile -> profile.id() == id).findFirst()
                .orElseThrow(() -> new ApiException(404, ErrorCodes.NOT_FOUND, "模型档案不存在"));
    }

    private String readApiKey(long id) {
        return jdbc.sql("SELECT encrypted_api_key FROM llm_profiles WHERE id = :id").param("id", id)
                .query((rs, i) -> Optional.ofNullable(rs.getString(1)).map(cipher::decrypt).orElse(""))
                .single();
    }

    private String encryptKey(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        return apiKey.isBlank() ? null : cipher.encrypt(apiKey.trim());
    }

    private static String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        if (secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 3) + "****" + secret.substring(secret.length() - 4);
    }
}
