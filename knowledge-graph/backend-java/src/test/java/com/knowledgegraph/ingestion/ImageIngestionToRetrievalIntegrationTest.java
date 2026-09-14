package com.knowledgegraph.ingestion;

import com.knowledgegraph.chat.LlmClient;
import com.knowledgegraph.chat.RetrievalService;
import com.knowledgegraph.graph.NodeService;
import com.knowledgegraph.settings.LlmProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 纯图片 ZIP 的完整链路：解压 → 视觉识别 → 原文分段 → 问答检索。
 *
 * <p>这是「知识不认格式、只认解析后的原文」这一机制的回归证据：ZIP 里的图片经视觉模型转写后，
 * 必须和 PDF 一样进入 {@code document_units} / {@code document_chunks}，并被问答检索真实命中。
 * 使用真实本地 MySQL + 隔离夹具，视觉模型与文本模型均以 {@code @MockitoBean} 模拟，不调用真实模型。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.sql.init.mode=never")
class ImageIngestionToRetrievalIntegrationTest {

    private static final String MOCK_KEY = "sk-mock-key-not-real-123";
    private static final Pattern CHUNK_ID_PATTERN = Pattern.compile("\\[chunkId:(\\d+)]");
    private static final String PAGE_ONE_TEXT = "第 1 页原文：栈（Stack）是一种后进先出（LIFO）的线性数据结构，只允许在栈顶插入和弹出元素。";
    private static final String PAGE_TWO_TEXT = "第 2 页原文：队列（Queue）是一种先进先出（FIFO）的线性数据结构，从队尾入队、队头出队。";

    @MockitoBean
    private OcrProvider ocrProvider;

    @MockitoBean
    private LlmClient llmClient;

    @MockitoBean
    private LlmProfileService llmProfileService;

    @MockitoBean
    private com.knowledgegraph.review.AiReviewService aiReview;

    @Autowired
    private IngestionPipeline pipeline;

    @Autowired
    private DocumentStorage storage;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private com.knowledgegraph.review.ReviewService reviewService;

    private String suffix;
    private long libraryId;
    private long documentId;
    private long jobId;
    private long nodeId;
    private java.nio.file.Path storedFile;
    private final AtomicInteger ocrCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        suffix = Long.toString(System.nanoTime());
        lenient().when(ocrProvider.isAvailable()).thenReturn(true);
        when(ocrProvider.transcribe(any(), any())).thenAnswer(invocation ->
                ocrCalls.incrementAndGet() == 1 ? PAGE_ONE_TEXT : PAGE_TWO_TEXT);
        lenient().when(llmProfileService.requireDefaultEnabledProfile()).thenReturn(
                new LlmProfileService.DefaultModel(99, "http://mock.local", "mock-model", MOCK_KEY, 5, 512, 0.3));
        lenient().when(llmClient.complete(any(), any())).thenAnswer(invocation -> {
            List<LlmClient.LlmMessage> messages = invocation.getArgument(1);
            Matcher matcher = CHUNK_ID_PATTERN.matcher(messages.get(1).content());
            assertTrue(matcher.find(), "抽取提示词必须携带真实 chunkId");
            long chunkId = Long.parseLong(matcher.group(1));
            return """
                    {"entities":[{"tempKey":"entity_1","name":"栈%s","aliases":[],"nodeType":"concept",
                      "definition":"后进先出的线性数据结构。","confidence":0.9,"evidenceChunkIds":[%d]}],
                     "relations":[]}
                    """.formatted(suffix, chunkId);
        });
        ocrCalls.set(0);
        createImageZipFixture();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM entity_candidates WHERE job_id = :id").param("id", jobId).update();
        jdbc.sql("DELETE FROM relation_candidates WHERE job_id = :id").param("id", jobId).update();
        // commit 产生的正式节点（properties_json.jobId 标记）级联清理别名/证据/关系
        jdbc.sql("DELETE FROM knowledge_nodes WHERE JSON_EXTRACT(properties_json, '$.jobId') = :id")
                .param("id", jobId).update();
        jdbc.sql("DELETE FROM knowledge_nodes WHERE id = :id").param("id", nodeId).update();
        jdbc.sql("DELETE FROM ingestion_jobs WHERE id = :id").param("id", jobId).update();
        jdbc.sql("DELETE FROM documents WHERE id = :id").param("id", documentId).update();
        jdbc.sql("DELETE FROM knowledge_libraries WHERE id = :id").param("id", libraryId).update();
        if (storedFile != null) {
            storage.delete(storedFile);
        }
    }

    /** 生成一个只有图片条目的 ZIP，并落库为「已上传待解析」的资料。 */
    private void createImageZipFixture() {
        byte[] zip = imageOnlyZip("book-page-1.png", "book-page-2.png");
        DocumentStorage.StoredFile stored = storage.save(new ByteArrayInputStream(zip), "zip");
        storedFile = stored.path();

        libraryId = insertAndGet("INSERT INTO knowledge_libraries (name, type, status) VALUES (:name, 'topic', 'active')",
                Map.of("name", "图片入库测试库-" + suffix));
        documentId = insertAndGet("""
                        INSERT INTO documents (library_id, original_name, stored_name, mime_type, extension,
                                               size_bytes, sha256, storage_path, status)
                        VALUES (:lib, :name, :stored, 'application/zip', 'zip', :size, :sha, :path, 'UPLOADED')
                        """,
                Map.of("lib", libraryId, "name", "book-" + suffix + ".zip", "stored", stored.storedName(),
                        "size", zip.length, "sha", stored.sha256(), "path", stored.path().toString()));
        jdbc.sql("""
                        INSERT INTO document_metadata (document_id, title, lifecycle_status, verification_json)
                        VALUES (:doc, :title, 'active', JSON_OBJECT())
                        """).param("doc", documentId).param("title", "book-" + suffix + ".zip").update();
        jobId = insertAndGet("""
                        INSERT INTO ingestion_jobs (document_id, stage, status) VALUES (:doc, 'UPLOADED', 'UPLOADED')
                        """, Map.of("doc", documentId));
        nodeId = insertAndGet("""
                        INSERT INTO knowledge_nodes (canonical_name, node_type, definition, status)
                        VALUES (:name, 'concept', '测试节点。', 'active')
                        """, Map.of("name", "栈" + suffix));
        jdbc.sql("INSERT INTO library_nodes (library_id, node_id) VALUES (:lib, :node)")
                .param("lib", libraryId).param("node", nodeId).update();
    }

    private static byte[] imageOnlyZip(String... entryNames) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (String name : entryNames) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(fakePng());
                zip.closeEntry();
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        return out.toByteArray();
    }

    /** 仅需 PNG 签名 + 占位字节：本测试验证链路与路由，不做真实图像解码。 */
    private static byte[] fakePng() {
        byte[] bytes = new byte[64];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    @Test
    void imagesInZipBecomeSearchableSourceText() throws InterruptedException {
        pipeline.run(documentId, jobId);
        String status = awaitTerminalJobStatus();

        assertEquals("AWAITING_REVIEW", status, () -> "任务未进入待复审：" + jobError());
        assertEquals(2, ocrCalls.get(), "两张图片都应经过视觉识别");

        // 图片单元被真实转写，并据此产生原文分段（PDF 之外的形式同样进入可复用语料）
        List<Map<String, Object>> units = jdbc.sql("""
                        SELECT unit_type, source_locator, status, extracted_text FROM document_units
                        WHERE document_id = :id ORDER BY unit_index
                        """).param("id", documentId)
                .query((rs, i) -> Map.<String, Object>of(
                        "type", rs.getString("unit_type"), "locator", rs.getString("source_locator"),
                        "status", rs.getString("status"), "text", rs.getString("extracted_text"))).list();
        assertEquals(2, units.size());
        for (Map<String, Object> unit : units) {
            assertEquals("image", unit.get("type"));
            assertEquals("OCR_OK", unit.get("status"));
            assertFalse(((String) unit.get("text")).isBlank());
        }

        long imageChunks = jdbc.sql("""
                        SELECT COUNT(*) FROM document_chunks c JOIN document_units u ON u.id = c.unit_id
                        WHERE u.document_id = :id AND u.unit_type = 'image'
                        """).param("id", documentId).query(Long.class).single();
        assertTrue(imageChunks >= 2, "每张图片都应产生原文分段，实际 " + imageChunks);

        // 走真实入库：模拟 AI 复审判定为接受，再调用 AI 复审内部使用的同一 commit 事务
        jdbc.sql("UPDATE entity_candidates SET review_status = 'ACCEPTED' WHERE job_id = :id")
                .param("id", jobId).update();
        reviewService.commit(jobId);
        assertEquals("COMPLETED", jdbc.sql("SELECT status FROM documents WHERE id = :id")
                .param("id", documentId).query(String.class).single());
        long evidenceOnImageChunk = jdbc.sql("""
                        SELECT COUNT(*) FROM node_evidence ne
                        JOIN document_chunks c ON c.id = ne.chunk_id
                        JOIN document_units u ON u.id = c.unit_id
                        WHERE u.document_id = :id AND u.unit_type = 'image'
                        """).param("id", documentId).query(Long.class).single();
        assertTrue(evidenceOnImageChunk >= 1, "图片原文应成为入库节点的证据");

        // 问答检索能命中图片里的原文，并指回具体图片文件
        List<RetrievalService.RetrievedChunk> hits =
                retrievalService.retrieve(nodeId, nodeService.getById(nodeId), "栈的后进先出是什么意思");
        assertFalse(hits.isEmpty(), "图片转写出的原文必须可用于问答检索");
        assertTrue(hits.stream().anyMatch(hit -> "image".equals(hit.unitType())
                        && "book-page-1.png".equals(hit.sourceLocator())
                        && hit.documentId() == documentId
                        && hit.content().contains("后进先出")),
                () -> "未命中图片原文：" + hits);
    }

    private String awaitTerminalJobStatus() throws InterruptedException {
        List<String> terminal = List.of("AWAITING_REVIEW", "FAILED", "COMPLETED", "CANCELLED");
        long deadline = System.currentTimeMillis() + 20_000;
        String status = "";
        while (System.currentTimeMillis() < deadline) {
            status = jdbc.sql("SELECT status FROM ingestion_jobs WHERE id = :id").param("id", jobId)
                    .query(String.class).single();
            if (terminal.contains(status)) {
                return status;
            }
            Thread.sleep(100);
        }
        return status;
    }

    private String jobError() {
        List<String> parts = jdbc.sql("SELECT COALESCE(error_code, ''), COALESCE(error_message, '') FROM ingestion_jobs WHERE id = :id")
                .param("id", jobId).query((rs, i) -> rs.getString(1) + " " + rs.getString(2)).list();
        return parts.isEmpty() ? "" : parts.get(0);
    }

    private long insertAndGet(String sql, Map<String, Object> params) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        var spec = jdbc.sql(sql);
        params.forEach(spec::param);
        spec.update(keys);
        return keys.getKey().longValue();
    }
}
