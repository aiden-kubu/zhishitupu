package com.knowledgegraph.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.chat.LlmClient;
import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.graph.NodeService;
import com.knowledgegraph.graph.dto.NodeDetail;
import com.knowledgegraph.search.AiExpandPayloadParser.NormalizedPayload;
import com.knowledgegraph.search.dto.AiExpandResult;
import com.knowledgegraph.settings.LlmProfileService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 搜索未命中时由 AI 补全知识图谱（任务约定 §12-§16）。
 * 流程：本地查重（命中绝不调模型）→ 进程内 single-flight 合并同词并发 → 默认模型档案生成
 * → 结构化解析校验 → 同一事务入库（写库前再查重）。
 * 这是唯一允许因搜索动作修改知识库的入口；联想与输入防抖绝不调用本服务（§18）。
 */
@Service
public class AiExpandService {

    static final String ORIGIN = "ai_search_generation";
    static final String REVIEW_STATUS_PENDING = "PENDING";

    static final String SYSTEM_PROMPT = """
            你是知识图谱系统的知识建模助手。用户会给出一个知识点名称，请为它生成结构化资料。
            只输出一个合法的 JSON 对象：第一个字符必须是 {，最后一个字符必须是 }。
            禁止输出 Markdown 代码块、注释、解释文字或任何前后缀。

            JSON 结构（固定如下，不要增删字段）：
            {
              "node": {
                "canonicalName": "节点标准名称",
                "nameEn": "英文名称或 null",
                "aliases": ["别名"],
                "type": "course|chapter|knowledge|concept|method|application|other 之一",
                "definition": "简明、客观的定义"
              },
              "relations": [
                {
                  "targetName": "关联节点名称",
                  "targetNameEn": "英文名称或 null",
                  "targetType": "course|chapter|knowledge|concept|method|application|other 之一",
                  "targetDefinition": "简短定义",
                  "relationType": "简短明确的关系名称"
                }
              ]
            }

            严格要求：
            1. canonicalName 必须对应用户查询的知识点本身，不得擅自换成其他主题；
            2. definition 必须非空，长度 20～1000 字；
            3. relations 最多 8 条，只列最相关的知识点；
            4. relationType 不超过 100 字；
            5. 所有内容均为纯文本，禁止生成 SQL、HTML、Markdown 或任何可执行代码。
            """;

    private static final int MAX_QUERY_CHARS = 200;

    private final AiExpandStore store;
    private final NodeService nodeService;
    private final LlmProfileService llmProfileService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /** single-flight（§15.7/8）：同一进程内相同关键词的并发请求只执行一次模型生成，不引入 Redis。 */
    private final ConcurrentHashMap<String, CompletableFuture<AiExpandResult>> inFlight = new ConcurrentHashMap<>();

    public AiExpandService(AiExpandStore store, NodeService nodeService,
                           LlmProfileService llmProfileService, LlmClient llmClient, ObjectMapper objectMapper) {
        this.store = store;
        this.nodeService = nodeService;
        this.llmProfileService = llmProfileService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public AiExpandResult expand(String rawQuery, Long libraryId) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            throw ApiException.badRequest("缺少搜索关键词 query");
        }
        if (query.length() > MAX_QUERY_CHARS) {
            throw ApiException.badRequest("搜索关键词过长，请控制在 " + MAX_QUERY_CHARS + " 字以内");
        }
        if (libraryId != null) {
            store.requireLibrary(libraryId);
        }

        // 1. 请求到达即查本地库：命中则直接返回，绝不调用模型（§12.2、§13）
        Long existingId = store.findExistingNodeId(query);
        if (existingId != null) {
            return buildResult(existingId, false, 0);
        }

        // 2. single-flight：同词并发只放行一个执行者，其余等待其结果
        String key = query.toLowerCase(Locale.ROOT);
        CompletableFuture<AiExpandResult> myFuture = new CompletableFuture<>();
        CompletableFuture<AiExpandResult> leader = inFlight.putIfAbsent(key, myFuture);
        if (leader != null) {
            return joinLeader(leader);
        }
        try {
            AiExpandResult result = generateAndStore(query, libraryId);
            myFuture.complete(result);
            return result;
        } catch (RuntimeException ex) {
            myFuture.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(key, myFuture);
        }
    }

    private AiExpandResult joinLeader(CompletableFuture<AiExpandResult> leader) {
        try {
            return leader.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(500, ErrorCodes.INTERNAL_ERROR, "AI 生成失败，请稍后重试");
        }
    }

    private AiExpandResult generateAndStore(String query, Long libraryId) {
        // 3. 拿到执行权后再查一次库：同词请求可能刚在前一毫秒完成写入（§15.9）
        Long existingId = store.findExistingNodeId(query);
        if (existingId != null) {
            return buildResult(existingId, false, 0);
        }

        // 4. 默认且启用的模型档案；未配置/被禁用/缺 Key → LLM_NOT_CONFIGURED
        LlmProfileService.DefaultModel model = llmProfileService.requireDefaultEnabledProfile();

        // 5. 调模型并解析校验（等待模型期间不持有数据库事务）
        String answer = llmClient.complete(model, List.of(
                new LlmClient.LlmMessage("system", SYSTEM_PROMPT),
                new LlmClient.LlmMessage("user",
                        "知识点名称：" + query + "\n请按系统指令返回该知识点的结构化 JSON。")));
        NormalizedPayload payload = AiExpandPayloadParser.parse(objectMapper, answer, query);

        // 6. 同一事务入库（persist 内部会再做一次写库前查重）
        AiExpandStore.PersistOutcome outcome = store.persist(query, libraryId, payload,
                nodeProperties(query, model), targetNodeProperties(model), edgeProperties(model));
        return buildResult(outcome.nodeId(), outcome.created(), outcome.relationsCreated());
    }

    private AiExpandResult buildResult(long nodeId, boolean created, int relationsCreated) {
        NodeDetail detail = nodeService.getById(nodeId);
        return new AiExpandResult(
                new AiExpandResult.NodeView(detail.id(), detail.name(), detail.nameEn(),
                        detail.type(), detail.definition(), detail.aliases()),
                created, relationsCreated);
    }

    /** 主节点来源标记（§16）：至少含 origin/generatedQuery/modelProfileId/model/generatedAt/reviewStatus。 */
    private Map<String, Object> nodeProperties(String query, LlmProfileService.DefaultModel model) {
        Map<String, Object> props = baseProperties(model);
        props.put("generatedQuery", query);
        return props;
    }

    /** 关系目标侧新建节点的来源标记（不含 generatedQuery：查询词不属于该节点）。 */
    private Map<String, Object> targetNodeProperties(LlmProfileService.DefaultModel model) {
        return baseProperties(model);
    }

    /** 关系的来源标记（§16）。 */
    private Map<String, Object> edgeProperties(LlmProfileService.DefaultModel model) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("origin", ORIGIN);
        props.put("modelProfileId", model.profileId());
        props.put("reviewStatus", REVIEW_STATUS_PENDING);
        return props;
    }

    private Map<String, Object> baseProperties(LlmProfileService.DefaultModel model) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("origin", ORIGIN);
        props.put("modelProfileId", model.profileId());
        props.put("model", model.model());
        props.put("generatedAt", LocalDateTime.now().withNano(0).toString());
        props.put("reviewStatus", REVIEW_STATUS_PENDING);
        return props;
    }
}
