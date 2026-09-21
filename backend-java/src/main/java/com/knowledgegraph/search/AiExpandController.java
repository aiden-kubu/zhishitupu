package com.knowledgegraph.search;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ApiResponse;
import com.knowledgegraph.search.dto.AiExpandRequest;
import com.knowledgegraph.search.dto.AiExpandResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索未命中时由 AI 补全知识图谱（任务约定 §13，第一版同步接口，无消息队列/新依赖）。
 * 只有该显式入口允许因搜索动作写库；顶部搜索联想与输入防抖绝不调用本接口（§18）。
 */
@RestController
@RequestMapping("/api/search")
public class AiExpandController {

    private final AiExpandService aiExpandService;

    public AiExpandController(AiExpandService aiExpandService) {
        this.aiExpandService = aiExpandService;
    }

    @PostMapping("/ai-expand")
    public ApiResponse<AiExpandResult> aiExpand(@RequestBody AiExpandRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw ApiException.badRequest("缺少搜索关键词 query");
        }
        return ApiResponse.ok(aiExpandService.expand(request.query(), request.libraryId()));
    }
}
