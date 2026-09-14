package com.knowledgegraph.extraction;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抽取分批策略（用户上传整本教材时触发的回归）：
 * 批次按字符预算与条数上限切分；超过批次上限时错误信息必须给出实际需求量与可调项。
 */
class ExtractionBatchLimitTest {

    private static ExtractionService.ChunkRow chunk(long id, int chars) {
        return new ExtractionService.ChunkRow(id, "内".repeat(chars));
    }

    @Test
    void largeDocumentsAreNoLongerRejectedAtTheOldSixtyBatchCeiling() {
        // 约 34 万字（整本教材量级片段数按 12000 字/批折算）
        List<ExtractionService.ChunkRow> chunks = new ArrayList<>();
        for (int i = 1; i <= 300; i++) {
            chunks.add(chunk(i, 1000));
        }
        List<List<ExtractionService.ChunkRow>> batches =
                ExtractionService.buildBatches(chunks, ExtractionService.DEFAULT_MAX_BATCHES);
        assertEquals(25, batches.size(), "300 个 1000 字片段按 12000 字预算应为 25 批");
        assertTrue(batches.size() <= ExtractionService.DEFAULT_MAX_BATCHES);
    }

    @Test
    void chunkCountCapStillAppliesWithinABatch() {
        List<ExtractionService.ChunkRow> chunks = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            chunks.add(chunk(i, 10)); // 字数很小，应由条数上限切分
        }
        List<List<ExtractionService.ChunkRow>> batches =
                ExtractionService.buildBatches(chunks, ExtractionService.DEFAULT_MAX_BATCHES);
        assertEquals(3, batches.size());
        assertTrue(batches.stream().allMatch(b -> b.size() <= 16));
    }

    @Test
    void emptyEntityBatchIsAcceptedInsteadOfFailingTheWholeJob() throws Exception {
        // 附录 / 索引 / 目录页确实可能不含知识点：模型返回 {"entities":[]} 属正常结果，
        // 此前被当作错误会让整本资料在最后一批失败（用户任务 #938 的实际故障）。
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var parsed = ExtractionPayloadParser.parse(mapper,
                "{\"entities\":[],\"relations\":[]}", java.util.Set.of(1L));
        assertEquals(0, parsed.entities().size());
        assertEquals(0, parsed.relations().size());
    }

    @Test
    void missingEntitiesArrayIsStillRejected() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ApiException ex = assertThrows(ApiException.class, () -> ExtractionPayloadParser.parse(
                mapper, "{\"relations\":[]}", java.util.Set.of(1L)));
        assertEquals(ErrorCodes.LLM_BAD_RESPONSE, ex.getCode());
    }

    @Test
    void exceedingConfiguredLimitReportsNeededBatchesAndAdjustableOption() {
        List<ExtractionService.ChunkRow> chunks = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            chunks.add(chunk(i, 10));
        }
        ApiException ex = assertThrows(ApiException.class, () -> ExtractionService.buildBatches(chunks, 2));
        assertEquals(413, ex.getHttpStatus());
        assertEquals(ErrorCodes.PAYLOAD_TOO_LARGE, ex.getCode());
        assertTrue(ex.getMessage().contains("3"), "必须给出实际需要的批次数：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("kg.extraction.max-batches"),
                "必须提示可调整的配置项：" + ex.getMessage());
    }
}
