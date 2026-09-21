package com.knowledgegraph.settings;

import com.knowledgegraph.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统设置接口（§9.5 / §12.1）。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 掩码后的系统配置。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(settingsService.view());
    }

    /** 保存配置（secrets 加密存储）。 */
    @PutMapping
    public ApiResponse<Map<String, Object>> save(@RequestBody SettingsService.SaveRequest request) {
        return ApiResponse.ok(settingsService.save(request));
    }

    /** 测试 LLM 连接：未配置时 503 LLM_NOT_CONFIGURED。 */
    @PostMapping("/llm/test")
    public ApiResponse<Map<String, Object>> testLlm() {
        return ApiResponse.ok(settingsService.testLlm());
    }
}
