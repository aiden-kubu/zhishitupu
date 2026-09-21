package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiResponse;
import com.knowledgegraph.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理任务接口（§12.4）：列表 / 详情 / 重试 / 取消。前端每 2 秒轮询活动任务。
 */
@RestController
@RequestMapping("/api/processing")
public class ProcessingController {

    private final ProcessingService processingService;
    private final com.knowledgegraph.review.AiReviewService aiReview;

    public ProcessingController(ProcessingService processingService, com.knowledgegraph.review.AiReviewService aiReview) {
        this.processingService = processingService;
        this.aiReview = aiReview;
    }

    @GetMapping("/jobs")
    public ApiResponse<PageResponse<ProcessingService.IngestionJobRow>> jobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(processingService.list(status, page, pageSize));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<ProcessingService.IngestionJobRow> get(@PathVariable long id) {
        return ApiResponse.ok(processingService.get(id));
    }

    @PostMapping("/jobs/{id}/retry")
    public ApiResponse<ProcessingService.IngestionJobRow> retry(@PathVariable long id) {
        if (processingService.get(id).candidateCount() > 0) {
            aiReview.start(id);
            return ApiResponse.ok(processingService.get(id));
        }
        return ApiResponse.ok(processingService.retry(id));
    }

    @PostMapping("/jobs/{id}/ai-review")
    public ApiResponse<ProcessingService.IngestionJobRow> aiReview(@PathVariable long id) {
        aiReview.start(id);
        return ApiResponse.ok(processingService.get(id));
    }

    @PostMapping("/jobs/{id}/cancel")
    public ApiResponse<ProcessingService.IngestionJobRow> cancel(@PathVariable long id) {
        return ApiResponse.ok(processingService.cancel(id));
    }

    /** 恢复入口：对已解析但无候选结果的旧任务发起 AI 抽取（复用既有 chunks，不重新解析） */
    @PostMapping("/jobs/{id}/extract")
    public ApiResponse<ProcessingService.IngestionJobRow> extract(
            @PathVariable long id,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean force) {
        return ApiResponse.ok(processingService.requestExtraction(id, Boolean.TRUE.equals(force)));
    }
}
