package com.knowledgegraph.ingestion;

import com.knowledgegraph.chat.LlmClient;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.extraction.ExtractionService;
import com.knowledgegraph.settings.LlmProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;

/**
 * 增量落库 + 断点续跑集成测试（真实本地 MySQL + 隔离夹具，模型调用全部模拟）。
 *
 * 覆盖：批次成果按批落库、任务失败后重试只补齐缺失批次（已落库批次不再调用模型）、
 * 重新解析后 chunkId 变化时按位置复用并重映射证据、片段内容变化时该批自动作废重抽、
 * 明确 force 重抽时断点数据作废。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.sql.init.mode=never")
class ExtractionCheckpointResumeIntegrationTest {

    private static final Pattern BATCH_PATTERN = Pattern.compile("第 (\\d+)/(\\d+) 批");
    private static final Pattern CHUNK_ID_PATTERN = Pattern.compile("\\[chunkId:(\\d+)]");
    /** 单批最多 16 个片段，因此 17 个片段必然分成 2 批 */
    private static final int CHUNK_COUNT = 17;

    @MockitoBean private LlmClient llmClient;
    @MockitoBean private LlmProfileService llmProfileService;
    @MockitoBean private com.knowledgegraph.review.AiReviewService aiReview;

    @Autowired private ExtractionService extractionService;
    @Autowired private ProcessingService processingService;
    @Autowired private IngestionPipeline pipeline;
    @Autowired private JdbcClient jdbc;

    private String suffix;
    private long libraryId;
    private long documentId;
    private long jobId;
    private long unitId;

    private final List<Long> chunkIds = new ArrayList<>();
    private final List<String> chunkContents = new ArrayList<>();
    private final Map<Integer, AtomicInteger> callsByBatch = new ConcurrentHashMap<>();
    private final AtomicBoolean failSecondBatch = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        lenient().when(llmProfileService.requireDefaultEnabledProfile()).thenReturn(
                new LlmProfileService.DefaultModel(99, "http://mock.local", "mock-model", "sk-mock", 5, 512, 0.3));
        lenient().when(llmClient.complete(any(), anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<LlmClient.LlmMessage> messages = invocation.getArgument(1);
            return answerExtractionBatch(messages.get(1).content());
        });
        callsByBatch.clear();
        failSecondBatch.set(false);
        createFixture();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM entity_candidates WHERE job_id = :id").param("id", jobId).update();
        jdbc.sql("DELETE FROM relation_candidates WHERE job_id = :id").param("id", jobId).update();
        jdbc.sql("DELETE FROM ingestion_jobs WHERE id = :id").param("id", jobId).update();   // 批次断点随任务级联删除
        jdbc.sql("DELETE FROM knowledge_libraries WHERE id = :id").param("id", libraryId).update();
    }

    // ---------------------------------------------------------------- 夹具

    private void createFixture() {
        libraryId = insert("INSERT INTO knowledge_libraries (name, type, status) VALUES (:name, 'topic', 'active')",
                Map.of("name", "断点续跑测试库-" + suffix));
        documentId = insert("""
                        INSERT INTO documents (library_id, original_name, stored_name, mime_type, extension,
                                               size_bytes, sha256, storage_path, status)
                        VALUES (:lib, :name, :stored, 'application/pdf', 'pdf', 1024, :sha, :path, 'PROCESSING')
                        """,
                Map.of("lib", libraryId, "name", "resume-" + suffix + ".pdf", "stored", "stored-" + suffix + ".pdf",
                        "sha", "sha" + suffix, "path", "resume/" + suffix + ".pdf"));
        jobId = insert("""
                        INSERT INTO ingestion_jobs (document_id, stage, status, progress)
                        VALUES (:doc, 'CHUNKING', 'CHUNKING', 70)
                        """, Map.of("doc", documentId));
        unitId = insert("""
                        INSERT INTO document_units (document_id, unit_type, unit_index, source_locator, extracted_text, status)
                        VALUES (:doc, 'page', 1, '第 1 页', '断点续跑夹具正文。', 'READY')
                        """, Map.of("doc", documentId));
        insertChunks();
    }

    /** 写入 17 个片段（16 + 1 两批）；重解析场景用同样内容生成新的 chunkId。 */
    private void insertChunks() {
        chunkIds.clear();
        chunkContents.clear();
        for (int i = 0; i < CHUNK_COUNT; i++) {
            String content = "片段 " + i + " 内容 " + suffix + "：栈是一种后进先出的线性数据结构。";
            chunkContents.add(content);
            chunkIds.add(insert("""
                            INSERT INTO document_chunks (unit_id, chunk_index, content, content_hash, start_offset, end_offset)
                            VALUES (:unit, :idx, :content, :hash, 0, 0)
                            """,
                    Map.of("unit", unitId, "idx", i, "content", content, "hash", "hash-" + suffix + "-" + i)));
        }
    }

    private long insert(String sql, Map<String, ?> params) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql(sql).params(params).update(keys);
        return keys.getKey().longValue();
    }

    // ---------------------------------------------------------------- 模型模拟

    private String answerExtractionBatch(String prompt) {
        Matcher batch = BATCH_PATTERN.matcher(prompt);
        if (!batch.find()) {
            throw new IllegalStateException("抽取提示词必须标明批次序号：" + prompt);
        }
        int index = Integer.parseInt(batch.group(1));
        callsByBatch.computeIfAbsent(index, key -> new AtomicInteger()).incrementAndGet();
        List<Long> ids = new ArrayList<>();
        Matcher chunkId = CHUNK_ID_PATTERN.matcher(prompt);
        while (chunkId.find()) {
            ids.add(Long.parseLong(chunkId.group(1)));
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("抽取提示词必须携带真实 chunkId");
        }
        if (index == 2 && failSecondBatch.get()) {
            throw new ApiException(502, ErrorCodes.LLM_BAD_RESPONSE, "模拟第 2 批输出不可用");
        }
        return """
                {"entities":[
                  {"tempKey":"entity_1","name":"批次%d知识点甲%s","nameEn":"Batch%dA%s","aliases":[],
                   "nodeType":"concept","definition":"第 %d 批抽取的知识点甲。","confidence":0.9,
                   "evidenceChunkIds":[%d]},
                  {"tempKey":"entity_2","name":"批次%d知识点乙%s","nameEn":"Batch%dB%s","aliases":[],
                   "nodeType":"concept","definition":"第 %d 批抽取的知识点乙。","confidence":0.8,
                   "evidenceChunkIds":[%d]}
                ],
                "relations":[
                  {"sourceTempKey":"entity_1","targetTempKey":"entity_2","relationType":"关联",
                   "confidence":0.8,"evidenceChunkIds":[%d]}
                ]}
                """.formatted(index, suffix, index, suffix, index, ids.get(0),
                index, suffix, index, suffix, index, ids.get(ids.size() - 1), ids.get(0));
    }

    // ---------------------------------------------------------------- 断言辅助

    /** 第一次运行：第 2 批连续 3 次输出不可用 → 任务 FAILED，但第 1 批成果已落库。 */
    private void failOnSecondBatch() {
        failSecondBatch.set(true);
        pipeline.finalizeChunkingAndExtract(documentId, jobId, 1);
        assertEquals("FAILED", jobStatus());
        assertEquals(1, stagedBatches(), "第 1 批成果必须已增量落库，第 2 批失败不得清空它");
        assertEquals(1, calls(1));
        assertEquals(3, calls(2), "同一批输出不合规会重试 3 次后放弃");
        // 之后的计数只统计续跑这一次运行，便于断言「哪些批次真的又调了模型」
        callsByBatch.clear();
    }

    private String jobStatus() {
        return jdbc.sql("SELECT status FROM ingestion_jobs WHERE id = :id").param("id", jobId).query(String.class).single();
    }

    private long stagedBatches() {
        return jdbc.sql("SELECT COUNT(*) FROM extraction_batch_results WHERE job_id = :id")
                .param("id", jobId).query(Long.class).single();
    }

    private int calls(int batchIndex) {
        AtomicInteger counter = callsByBatch.get(batchIndex);
        return counter == null ? 0 : counter.get();
    }

    private String awaitStatus(String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            String status = jobStatus();
            if (expected.equals(status)) {
                return status;
            }
            Thread.sleep(50);
        }
        return jobStatus();
    }

    private long unitCount() {
        return jdbc.sql("SELECT COUNT(*) FROM document_units WHERE document_id = :id")
                .param("id", documentId).query(Long.class).single();
    }

    private long chunkCount() {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM document_chunks c JOIN document_units u ON u.id = c.unit_id
                        WHERE u.document_id = :id
                        """).param("id", documentId).query(Long.class).single();
    }

    private Set<Long> candidateEvidenceIds() {
        Set<Long> ids = new LinkedHashSet<>();
        jdbc.sql("SELECT evidence_chunk_ids_json FROM entity_candidates WHERE job_id = :id UNION ALL "
                        + "SELECT evidence_chunk_ids_json FROM relation_candidates WHERE job_id = :id")
                .param("id", jobId)
                .query((rs, i) -> rs.getString(1))
                .list()
                .forEach(json -> {
                    Matcher matcher = Pattern.compile("\\d+").matcher(json);
                    while (matcher.find()) {
                        ids.add(Long.parseLong(matcher.group()));
                    }
                });
        return ids;
    }

    // ---------------------------------------------------------------- 用例

    @Test
    void retryResumesFromStagedBatchesWithoutReextractingThem() throws Exception {
        failOnSecondBatch();

        failSecondBatch.set(false);
        processingService.retry(jobId);

        // 续跑不重新解析：单元与片段保持原样（第 2 批只补调 1 次模型）
        assertEquals(1, unitCount(), "续跑不应重新解析文件");
        assertEquals(CHUNK_COUNT, chunkCount());
        assertEquals("AWAITING_REVIEW", awaitStatus("AWAITING_REVIEW"));
        assertEquals(0, calls(1), "已落库的第 1 批不得再次调用模型");
        assertEquals(1, calls(2), "第 2 批只补齐一次");
        assertEquals(0, stagedBatches(), "候选就绪后断点数据必须清理");
        assertEquals(4, jdbc.sql("SELECT COUNT(*) FROM entity_candidates WHERE job_id = :id")
                .param("id", jobId).query(Long.class).single());
        assertEquals(2, jdbc.sql("SELECT COUNT(*) FROM relation_candidates WHERE job_id = :id")
                .param("id", jobId).query(Long.class).single());
    }

    @Test
    void reusesStagedBatchAndRemapsEvidenceAfterReparse() {
        failOnSecondBatch();

        // 模拟重新解析：内容一字不变、chunkId 全部换新
        jdbc.sql("DELETE FROM document_chunks WHERE unit_id = :id").param("id", unitId).update();
        List<Long> previousIds = new ArrayList<>(chunkIds);
        insertChunks();
        assertFalse(chunkIds.contains(previousIds.get(0)), "重新解析后必须生成新的 chunkId");

        failSecondBatch.set(false);
        extractionService.runExtraction(jobId);

        assertEquals("AWAITING_REVIEW", jobStatus());
        assertEquals(0, calls(1), "内容未变的批次即使 chunkId 变化也必须复用");
        assertEquals(1, calls(2));
        Set<Long> evidence = candidateEvidenceIds();
        assertFalse(evidence.isEmpty());
        assertTrue(chunkIds.containsAll(evidence), "复用批次的证据必须重映射到本次真实 chunkId：" + evidence);
        assertTrue(evidence.stream().noneMatch(previousIds::contains), "旧的 chunkId 不得残留");
    }

    @Test
    void reextractsBatchWhoseChunkContentChanged() {
        failOnSecondBatch();

        // 只改动第 1 批中的一个片段：指纹变化，该批必须重抽
        jdbc.sql("UPDATE document_chunks SET content = CONCAT(content, '补充说明。') WHERE id = :id")
                .param("id", chunkIds.get(0)).update();

        failSecondBatch.set(false);
        extractionService.runExtraction(jobId);

        assertEquals("AWAITING_REVIEW", jobStatus());
        assertEquals(1, calls(1), "片段内容变化的批次必须重新抽取");
        assertEquals(1, calls(2));
    }

    @Test
    void forceReextractionDiscardsStagedBatches() throws Exception {
        failOnSecondBatch();

        failSecondBatch.set(false);
        processingService.requestExtraction(jobId, true);

        assertEquals(0, stagedBatches(), "明确重抽时断点数据必须立刻作废");
        assertEquals("AWAITING_REVIEW", awaitStatus("AWAITING_REVIEW"));
        assertEquals(1, calls(1), "force 重抽必须两批都重新调用模型");
        assertEquals(1, calls(2));
    }
}
