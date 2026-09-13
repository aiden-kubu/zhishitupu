package com.knowledgegraph.chat;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.settings.LlmProfileService;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 /chat/completions 调用（§13 LlmProvider 的最小实现）。
 * 只负责：拼请求体、映射上游错误、抽取 choices[0].message.content；
 * 模型接入参数（含解密后的 Key）来自默认模型档案，Key 只在内存中使用，绝不进入日志或异常消息。
 */
@Component
public class LlmClient {

    /** 发给模型的单条消息；role 为 system|user|assistant。 */
    public record LlmMessage(String role, String content) {
    }

    /**
     * 非流式补全：返回 choices[0].message.content。
     * 401/403 → LLM_AUTH_FAILED；超时/网络失败/上游 5xx → LLM_UNREACHABLE；
     * 404、其他 4xx 或响应缺少非空 content → LLM_BAD_RESPONSE。
     */
    @SuppressWarnings("unchecked")
    public String complete(LlmProfileService.DefaultModel model, List<LlmMessage> messages) {
        RestClient client = RestClient.builder()
                .baseUrl(model.baseUrl().endsWith("/") ? model.baseUrl().substring(0, model.baseUrl().length() - 1) : model.baseUrl())
                .requestFactory(requestFactory(model.timeoutSeconds()))
                .build();
        Map<?, ?> response;
        Map<String, Object> payload = new java.util.LinkedHashMap<>(Map.of(
                "model", model.model(),
                "messages", messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList(),
                "max_tokens", model.maxTokens(), "temperature", model.temperature(), "stream", false));
        if (model.thinkingEnabled() != null && "api.deepseek.com".equalsIgnoreCase(java.net.URI.create(model.baseUrl()).getHost())) {
            payload.put("thinking", Map.of("type", model.thinkingEnabled() ? "enabled" : "disabled"));
        }
        try {
            response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + model.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            throw redactApiKey(mapUpstreamError(ex), model.apiKey());
        } catch (ResourceAccessException ex) {
            throw unreachable();
        } catch (RestClientException ex) {
            // 响应体不是模型预期的 JSON（错误 Content-Type、解析失败、结构不符）；
            // 但底层若是超时/连接类 IO 错误，应归为不可达而非坏响应
            if (hasNetworkFailureCause(ex)) {
                throw unreachable();
            }
            throw new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE, "模型返回了无法解析的内容");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(502, ErrorCodes.LLM_UNREACHABLE, "调用模型服务失败，请稍后重试");
        }
        return extractContent(response);
    }

    private static ApiException unreachable() {
        return new ApiException(502, ErrorCodes.LLM_UNREACHABLE,
                "无法连接模型服务（连接超时或网络错误），请检查网络与 API 地址后重试");
    }

    private static boolean hasNetworkFailureCause(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException
                    || t instanceof java.net.ConnectException
                    || t instanceof java.net.UnknownHostException
                    || t instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** 异常消息中出现的密钥一律打码（上游错误体可能原样回显请求头）。 */
    public static ApiException redactApiKey(ApiException ex, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || ex.getMessage() == null || !ex.getMessage().contains(apiKey)) {
            return ex;
        }
        return new ApiException(ex.getHttpStatus(), ex.getCode(),
                ex.getMessage().replace(apiKey, "******"), ex.getDetails());
    }

    /** 校验响应结构并抽取回复文本；choices 缺失、为空或 content 非空白文本时抛 LLM_BAD_RESPONSE。 */
    public static String extractContent(Map<?, ?> response) {
        if (response == null) {
            throw new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE, "模型返回为空");
        }
        if (!(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE, "模型返回缺少 choices 内容");
        }
        Object content = null;
        if (choices.get(0) instanceof Map<?, ?> choice && "length".equals(choice.get("finish_reason"))) {
            throw new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE,
                    "模型输出达到长度上限，内容不完整，请减少单次输出后重试");
        }
        if (choices.get(0) instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> message) {
            content = message.get("content");
        }
        if (!(content instanceof String text) || text.isBlank()) {
            throw new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE, "模型返回内容为空或格式不符合预期");
        }
        return text.strip();
    }

    /** 上游 HTTP 错误 → 统一业务错误码；错误详情截断，且不得包含请求密钥。 */
    public static ApiException mapUpstreamError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new ApiException(502, ErrorCodes.LLM_AUTH_FAILED,
                    "模型服务认证失败（HTTP " + status + "），请检查 API Key 是否有效");
        }
        String detail = snippet(ex.getResponseBodyAsString());
        if (status == 404) {
            return new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE,
                    "模型服务接口不存在（HTTP 404），请检查 API 地址与模型名称" + detail);
        }
        if (status >= 500) {
            return new ApiException(502, ErrorCodes.LLM_UNREACHABLE,
                    "模型服务暂时不可用（HTTP " + status + "），请稍后重试" + detail);
        }
        return new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE,
                "模型服务返回异常（HTTP " + status + "）" + detail);
    }

    private static ClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    private static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.strip().replaceAll("\\s+", " ");
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }
}
