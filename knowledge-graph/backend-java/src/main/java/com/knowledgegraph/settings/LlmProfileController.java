package com.knowledgegraph.settings;

import com.knowledgegraph.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 模型档案 CRUD（多模型支持）：新增/编辑/删除/设为默认/连通性测试。
 */
@RestController
@RequestMapping("/api/settings/llm-profiles")
public class LlmProfileController {

    private final LlmProfileService llmProfileService;

    public LlmProfileController(LlmProfileService llmProfileService) {
        this.llmProfileService = llmProfileService;
    }

    public record LlmProfileRequest(
            @NotBlank(message = "名称不能为空") String name,
            @NotBlank(message = "API 地址不能为空") String baseUrl,
            @NotBlank(message = "模型名称不能为空") String model,
            String apiKey,
            Integer timeoutMs,
            Integer maxOutputTokens,
            Boolean vision,
            Boolean enabled) {
    }

    @GetMapping
    public ApiResponse<List<LlmProfileService.LlmProfileView>> list() {
        return ApiResponse.ok(llmProfileService.list());
    }

    @PostMapping
    public ApiResponse<LlmProfileService.LlmProfileView> create(@Valid @RequestBody LlmProfileRequest request) {
        return ApiResponse.ok(llmProfileService.create(toServiceRequest(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmProfileService.LlmProfileView> update(
            @PathVariable long id, @Valid @RequestBody LlmProfileRequest request) {
        return ApiResponse.ok(llmProfileService.update(id, toServiceRequest(request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id) {
        llmProfileService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping("/{id}/default")
    public ApiResponse<LlmProfileService.LlmProfileView> setDefault(@PathVariable long id) {
        return ApiResponse.ok(llmProfileService.setDefault(id));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable long id) {
        return ApiResponse.ok(llmProfileService.test(id));
    }

    private LlmProfileService.LlmProfileSaveRequest toServiceRequest(LlmProfileRequest request) {
        return new LlmProfileService.LlmProfileSaveRequest(
                request.name(), request.baseUrl(), request.model(), request.apiKey(),
                request.timeoutMs(), request.maxOutputTokens(), request.vision(), request.enabled());
    }
}
