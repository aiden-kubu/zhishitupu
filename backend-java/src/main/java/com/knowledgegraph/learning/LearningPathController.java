package com.knowledgegraph.learning;

import com.knowledgegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning-path")
public class LearningPathController {

    private final LearningPathService service;

    public LearningPathController(LearningPathService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<LearningPathService.PathView> current() {
        return ApiResponse.ok(service.current().orElse(null));
    }

    @PutMapping
    public ApiResponse<LearningPathService.PathView> save(@RequestBody LearningPathService.SaveRequest request) {
        return ApiResponse.ok(service.save(request));
    }

    @PostMapping("/steps/{stepKey}/visited")
    public ApiResponse<LearningPathService.PathView> markVisited(@PathVariable String stepKey) {
        return ApiResponse.ok(service.markVisited(stepKey));
    }

    @PostMapping("/steps/{stepKey}/complete")
    public ApiResponse<LearningPathService.PathView> complete(@PathVariable String stepKey) {
        return ApiResponse.ok(service.complete(stepKey));
    }

    @PostMapping("/assessment")
    public ApiResponse<LearningPathService.PathView> assessment(@RequestBody LearningPathService.AssessmentRequest request) {
        return ApiResponse.ok(service.recordAssessment(request));
    }
}
