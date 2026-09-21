package com.knowledgegraph.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽取批次成果的增量落库（增量落库 + 断点续跑）。
 *
 * 每一批模型输出在校验通过后立即写入 {@code extraction_batch_results}，因此超时、限流、
 * 输出不合规或进程中断都只损失当前这一批：任务重试时按批指纹复用已落库的批次，只补齐缺失的部分。
 *
 * 指纹只由「批次内片段内容与顺序」决定，不含 chunkId，因为重新解析会让 chunkId 变化、
 * 但同一份原文的片段内容不变；指纹相同即内容相同，复用时按位置把证据编号映射到新 chunkId。
 *
 * 本类只负责存取与指纹，不做业务判断（能否复用、何时作废由 {@link ExtractionService} 决定）。
 */
@Component
public class ExtractionCheckpointStore {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public ExtractionCheckpointStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 一批已落库的抽取成果。 */
    public record StoredBatch(int batchIndex, String signature, List<Long> chunkIds, String payloadJson,
                              int entityCount, int relationCount, String model) {
    }

    /**
     * 批次指纹：片段条数 + 每条片段长度 + 内容，按顺序摘要。
     * 长度参与摘要，避免「ab」「c」与「a」「bc」这类拼接歧义。
     */
    public static String signature(List<String> contents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(4).putInt(contents.size()).array());
            for (String content : contents) {
                byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 摘要不可用", ex);
        }
    }

    /** 该任务已落库的批次，按 batch_index 索引。 */
    public Map<Integer, StoredBatch> load(long jobId) {
        Map<Integer, StoredBatch> result = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT batch_index, batch_signature, chunk_ids_json, payload_json,
                               entity_count, relation_count, model
                        FROM extraction_batch_results WHERE job_id = :jobId ORDER BY batch_index
                        """)
                .param("jobId", jobId)
                .query((rs, i) -> new StoredBatch(rs.getInt(1), rs.getString(2), ids(rs.getString(3)),
                        rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getString(7)))
                .list()
                .forEach(batch -> result.put(batch.batchIndex(), batch));
        return result;
    }

    /** 记录一批成果；同批重抽时覆盖旧值（例如指纹变化后重新抽取）。 */
    public void save(long jobId, int batchIndex, String signature, List<Long> chunkIds, String payloadJson,
                     int entityCount, int relationCount, String model) {
        jdbc.sql("""
                        INSERT INTO extraction_batch_results
                            (job_id, batch_index, batch_signature, chunk_ids_json, payload_json,
                             entity_count, relation_count, model)
                        VALUES (:jobId, :index, :signature, :chunkIds, :payload, :entities, :relations, :model)
                        ON DUPLICATE KEY UPDATE batch_signature = VALUES(batch_signature),
                              chunk_ids_json = VALUES(chunk_ids_json), payload_json = VALUES(payload_json),
                              entity_count = VALUES(entity_count), relation_count = VALUES(relation_count),
                              model = VALUES(model)
                        """)
                .param("jobId", jobId).param("index", batchIndex).param("signature", signature)
                .param("chunkIds", json(chunkIds)).param("payload", payloadJson)
                .param("entities", entityCount).param("relations", relationCount).param("model", model)
                .update();
    }

    public void delete(long jobId, int batchIndex) {
        jdbc.sql("DELETE FROM extraction_batch_results WHERE job_id = :jobId AND batch_index = :index")
                .param("jobId", jobId).param("index", batchIndex).update();
    }

    /** 批次计划变短时清掉多余的旧批次，避免残留结果被下一次复用。 */
    public void deleteBeyond(long jobId, int lastIndex) {
        jdbc.sql("DELETE FROM extraction_batch_results WHERE job_id = :jobId AND batch_index > :index")
                .param("jobId", jobId).param("index", lastIndex).update();
    }

    /** 任务进入 AWAITING_REVIEW 后不再需要断点数据（重抽时不应命中旧成果）。 */
    public void deleteAll(long jobId) {
        jdbc.sql("DELETE FROM extraction_batch_results WHERE job_id = :jobId").param("jobId", jobId).update();
    }

    public int count(long jobId) {
        return jdbc.sql("SELECT COUNT(*) FROM extraction_batch_results WHERE job_id = :jobId")
                .param("jobId", jobId).query(Integer.class).optional().orElse(0);
    }

    /** 片段编号列表；损坏时返回空列表，由业务侧按「不可复用」处理（宁可重抽，也不要卡住任务）。 */
    private List<Long> ids(String raw) {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        try {
            for (com.fasterxml.jackson.databind.JsonNode node : objectMapper.readTree(raw)) {
                if (node.canConvertToLong()) {
                    ids.add(node.longValue());
                }
            }
        } catch (Exception ex) {
            return new ArrayList<>();
        }
        return ids;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("断点批次片段编号无法序列化", ex);
        }
    }
}
