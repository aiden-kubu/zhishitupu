package com.knowledgegraph.review;

import com.knowledgegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审核中心接口（§12.5）：候选查询、逐条/批量审核、确认入库。
 * Controller 只做参数接收与响应映射，业务与 SQL 在 ReviewService。
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;
    private final AiReviewService aiReview;

    public ReviewController(ReviewService reviewService, AiReviewService aiReview) {
        this.reviewService = reviewService;
        this.aiReview = aiReview;
    }

    @GetMapping("/jobs/{jobId}/ai-decisions")
    public ApiResponse<List<AiReviewService.Audit>> decisions(@PathVariable long jobId) {
        return ApiResponse.ok(aiReview.audits(jobId));
    }

    @GetMapping("/jobs/{jobId}/entities")
    public ApiResponse<List<ReviewService.EntityCandidateView>> entities(@PathVariable long jobId) {
        return ApiResponse.ok(reviewService.listEntities(jobId));
    }

    @GetMapping("/jobs/{jobId}/relations")
    public ApiResponse<List<ReviewService.RelationCandidateView>> relations(@PathVariable long jobId) {
        return ApiResponse.ok(reviewService.listRelations(jobId));
    }

    @PutMapping("/entities/{id}")
    public ApiResponse<ReviewService.EntityCandidateView> updateEntity(
            @PathVariable long id, @RequestBody ReviewService.UpdateEntityRequest request) {
        throw com.knowledgegraph.common.ApiException.conflict("已改为 AI 复审并自动入库，请在处理中心查看或重试任务");
    }

    @PutMapping("/relations/{id}")
    public ApiResponse<ReviewService.RelationCandidateView> updateRelation(
            @PathVariable long id, @RequestBody ReviewService.UpdateRelationRequest request) {
        throw com.knowledgegraph.common.ApiException.conflict("已改为 AI 复审并自动入库，请在处理中心查看或重试任务");
    }

    @PostMapping("/jobs/{jobId}/bulk-action")
    public ApiResponse<Map<String, Object>> bulkAction(
            @PathVariable long jobId, @RequestBody ReviewService.BulkActionRequest request) {
        throw com.knowledgegraph.common.ApiException.conflict("已改为 AI 复审并自动入库，请在处理中心查看或重试任务");
    }

    @PostMapping("/jobs/{jobId}/commit")
    public ApiResponse<ReviewService.CommitResult> commit(@PathVariable long jobId) {
        throw com.knowledgegraph.common.ApiException.conflict("已改为 AI 复审并自动入库，请在处理中心查看或重试任务");
    }
}
