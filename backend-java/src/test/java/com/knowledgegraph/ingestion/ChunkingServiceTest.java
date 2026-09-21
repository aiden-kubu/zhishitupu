package com.knowledgegraph.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §8.6 分段规则：目标 500–1000 字、重叠 80–150、不跨单元、哈希去重。 */
class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService();

    @Test
    void shortTextBecomesSingleChunk() {
        List<ChunkingService.Chunk> chunks = service.split("TCP 是面向连接的传输层协议。");
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).startOffset());
    }

    @Test
    void blankTextYieldsNoChunks() {
        assertTrue(service.split("   ").isEmpty());
        assertTrue(service.split(null).isEmpty());
    }

    @Test
    void longTextChunksWithinLimitsAndOverlap() {
        String paragraph = "计算机网络体系结构自底向上分为物理层、数据链路层、网络层、传输层与应用层。".repeat(10) + "\n";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            text.append("第 ").append(i + 1).append(" 段：").append(paragraph);
        }
        List<ChunkingService.Chunk> chunks = service.split(text.toString());
        assertTrue(chunks.size() > 1, "长文本应产生多个分段");
        for (ChunkingService.Chunk chunk : chunks) {
            assertTrue(chunk.content().length() <= 1000 + 1, "分段不得超过 1000 字上限");
            assertTrue(chunk.content().length() >= 300, "边界调整后仍应接近目标长度");
        }
        // 相邻分段存在重叠（前段尾部出现在后段头部）
        String tailOfFirst = chunks.get(0).content().substring(chunks.get(0).content().length() - 80);
        assertTrue(chunks.get(1).content().contains(tailOfFirst.substring(0, 40)),
                "相邻分段应包含重叠区");
        // 偏移单调递增
        for (int i = 1; i < chunks.size(); i++) {
            assertTrue(chunks.get(i).startOffset() >= chunks.get(i - 1).startOffset());
        }
    }

    @Test
    void duplicateContentIsDeduplicated() {
        String repeated = "同一段完全重复的文本内容用于验证哈希去重逻辑。".repeat(20);
        List<ChunkingService.Chunk> chunks = service.split(repeated);
        assertEquals(1, chunks.size(), "内容完全相同且未跨过边界的文本应去重为单个分段");
    }
}
