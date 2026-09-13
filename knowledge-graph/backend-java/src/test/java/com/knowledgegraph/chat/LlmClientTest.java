package com.knowledgegraph.chat;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.settings.LlmProfileService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LlmClient 契约测试：用 JDK 内置 HttpServer 模拟 OpenAI 兼容上游，
 * 覆盖响应解析、401/403/404/500、超时、网络失败与密钥泄露防护（不依赖真实模型服务）。
 */
class LlmClientTest {

    private static final String API_KEY = "sk-secret-test-key-abc123";
    private static final String AUTH_HEADER = "Bearer " + API_KEY;

    private static HttpServer server;
    private static int port;
    private static final AtomicReference<HttpHandler> HANDLER = new AtomicReference<>();
    private static volatile String lastPath;
    private static volatile String lastAuthHeader;
    private static volatile String lastRequestBody;

    private final LlmClient client = new LlmClient();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            HttpHandler handler = HANDLER.get();
            if (handler == null) {
                respond(exchange, 500, "{\"error\":{\"message\":\"no handler\"}}");
                return;
            }
            handler.handle(exchange);
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private LlmProfileService.DefaultModel model() {
        return model(30);
    }

    private LlmProfileService.DefaultModel model(int timeoutSeconds) {
        return new LlmProfileService.DefaultModel(1, "http://127.0.0.1:" + port,
                "test-model", API_KEY, timeoutSeconds, 64, 0.3);
    }

    private List<LlmClient.LlmMessage> messages() {
        return List.of(new LlmClient.LlmMessage("system", "系统提示"),
                new LlmClient.LlmMessage("user", "HTTP是什么"));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 记录请求并按预设响应。 */
    private void setUpstream(int status, String body) {
        HANDLER.set(exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, status, body);
        });
    }

    // ------------------------------------------------------------ 正常解析

    @Test
    void parsesChoicesFirstMessageContent() {
        setUpstream(200, """
                {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"HTTP 是基于 TCP 的应用层协议。"},"finish_reason":"stop"}],"usage":{"total_tokens":10}}
                """);
        String answer = client.complete(model(), messages());
        assertEquals("HTTP 是基于 TCP 的应用层协议。", answer);
        assertEquals("/chat/completions", lastPath);
        assertEquals(AUTH_HEADER, lastAuthHeader);
        assertTrue(lastRequestBody.contains("test-model"));
        assertTrue(lastRequestBody.contains("HTTP是什么"));
    }

    @Test
    void stripsSurroundingWhitespaceOfContent() {
        setUpstream(200, "{\"choices\":[{\"message\":{\"content\":\"  带空白的回答  \"}}]}");
        assertEquals("带空白的回答", client.complete(model(), messages()));
    }

    // ------------------------------------------------------------ 非预期 JSON

    @Test
    void emptyChoicesThrowsBadResponse() {
        setUpstream(200, "{\"choices\":[]}");
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()));
        assertEquals(ErrorCodes.LLM_BAD_RESPONSE, ex.getCode());
    }

    @Test
    void missingChoicesFieldThrowsBadResponse() {
        setUpstream(200, "{\"id\":\"x\",\"object\":\"chat.completion\"}");
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()));
        assertEquals(ErrorCodes.LLM_BAD_RESPONSE, ex.getCode());
    }

    @Test
    void blankContentThrowsBadResponse() {
        setUpstream(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"   \"}}]}");
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()));
        assertEquals(ErrorCodes.LLM_BAD_RESPONSE, ex.getCode());
    }

    @Test
    void missingMessageThrowsBadResponse() {
        setUpstream(200, "{\"choices\":[{\"index\":0}]}");
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()));
        assertEquals(ErrorCodes.LLM_BAD_RESPONSE, ex.getCode());
    }

    @Test
    void malformedJsonBodyThrowsBadResponse() {
        setUpstream(200, "not-json-at-all");
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()));
        assertEquals(ErrorCodes.LLM_BAD_RESPONSE, ex.getCode());
    }

    // ------------------------------------------------------------ 上游 HTTP 错误

    @Test
    void upstream401ThrowsAuthFailedWithoutKeyLeak() {
        setUpstream(401, "{\"error\":{\"message\":\"invalid api key: " + API_KEY + "\"}}");
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()));
        assertEquals(ErrorCodes.LLM_AUTH_FAILED, ex.getCode());
        assertNoKeyLeak(ex);
    }

    @Test
    void upstream403ThrowsAuthFailed() {
        setUpstream(403, "{\"error\":{\"message\":\"forbidden\"}}");
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()));
        assertEquals(ErrorCodes.LLM_AUTH_FAILED, ex.getCode());
    }

    @Test
    void upstream404ThrowsBadResponseAndRedactsEchoedKey() {
        // 上游把请求头里的 Key 原样回显进错误体：响应消息中必须被打码
        setUpstream(404, "{\"error\":{\"message\":\"model not found, request used " + AUTH_HEADER + "\"}}");
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()));
        assertEquals(ErrorCodes.LLM_BAD_RESPONSE, ex.getCode());
        assertNoKeyLeak(ex);
    }

    @Test
    void upstream500ThrowsUnreachable() {
        setUpstream(500, "{\"error\":{\"message\":\"internal server error\"}}");
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()));
        assertEquals(ErrorCodes.LLM_UNREACHABLE, ex.getCode());
    }

    // ------------------------------------------------------------ 超时与网络失败

    @Test
    void readTimeoutThrowsUnreachable() {
        HANDLER.set(exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"too late\"}}]}");
        });
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(1), messages()));
        assertEquals(ErrorCodes.LLM_UNREACHABLE, ex.getCode());
    }

    @Test
    void connectionRefusedThrowsUnreachable() throws IOException {
        // 启动后立即停止：端口不再监听，模拟网络失败
        HttpServer dead = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int deadPort = dead.getAddress().getPort();
        dead.stop(0);
        LlmProfileService.DefaultModel deadModel = new LlmProfileService.DefaultModel(
                1, "http://127.0.0.1:" + deadPort, "test-model", API_KEY, 5, 64, 0.3);
        ApiException ex = assertThrows(ApiException.class, () -> client.complete(deadModel, messages()));
        assertEquals(ErrorCodes.LLM_UNREACHABLE, ex.getCode());
        assertNoKeyLeak(ex);
    }

    // ------------------------------------------------------------ 密钥不泄露

    @Test
    void everyBusinessErrorCarriesHttp502AndNoKey() {
        for (int status : new int[]{400, 401, 403, 404, 429, 500, 503}) {
            setUpstream(status, "{\"error\":{\"message\":\"echo " + AUTH_HEADER + "\"}}");
            ApiException ex = assertThrows(ApiException.class, () -> client.complete(model(), messages()),
                    "status=" + status);
            assertEquals(502, ex.getHttpStatus(), "status=" + status);
            assertNoKeyLeak(ex);
        }
    }

    private static void assertNoKeyLeak(ApiException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        assertFalse(message.contains(API_KEY), "异常消息不得包含 API Key: " + message);
        assertFalse(message.contains(AUTH_HEADER), "异常消息不得包含 Authorization 头: " + message);
    }
}
