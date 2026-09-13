package com.knowledgegraph.insight;

import com.knowledgegraph.common.ApiResponse;
import com.knowledgegraph.insight.dto.InsightSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/summary")
    public ApiResponse<InsightSummary> summary() {
        return ApiResponse.ok(insightService.summary());
    }
}
