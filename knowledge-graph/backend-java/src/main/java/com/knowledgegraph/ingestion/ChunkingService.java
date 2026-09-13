package com.knowledgegraph.ingestion;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * 中文分段（§8.6）：目标 500–1000 字，重叠 80–150 字，优先在段落/句子边界切分；
 * 不跨文档单元合并；content_hash 去重（同一文档内重复片段只保留一份）。
 */
@Service
public class ChunkingService {

    public record Chunk(int index, String content, String contentHash, int startOffset, int endOffset) {
    }

    private static final int MAX_CHUNK = 1000;
    private static final int TARGET_CHUNK = 600;
    private static final int MIN_CHUNK = 400;
    private static final int OVERLAP = 100;

    public List<Chunk> split(String text) {
        List<Chunk> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String normalized = text.strip();
        Set<String> seenHashes = new HashSet<>();
        int cursor = 0;
        int index = 0;
        int length = normalized.length();
        while (cursor < length) {
            int end = Math.min(cursor + MAX_CHUNK, length);
            if (end < length) {
                // 优先在 [cursor+MIN_CHUNK, end) 内找最后一个换行或句号边界
                int boundary = lastBoundary(normalized, cursor + MIN_CHUNK, end);
                if (boundary > cursor) {
                    end = boundary;
                }
            }
            String content = normalized.substring(cursor, end).strip();
            if (!content.isEmpty()) {
                String hash = sha256(content);
                if (seenHashes.add(hash)) {
                    result.add(new Chunk(index++, content, hash, cursor, end));
                }
            }
            if (end >= length) {
                break;
            }
            cursor = Math.max(cursor + 1, end - OVERLAP);
        }
        return result;
    }

    private int lastBoundary(String text, int from, int to) {
        int best = -1;
        for (int i = Math.min(to, text.length()) - 1; i >= from && i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '.' || c == '；' || c == ';') {
                best = i + 1;
                break;
            }
        }
        return best;
    }

    public String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
