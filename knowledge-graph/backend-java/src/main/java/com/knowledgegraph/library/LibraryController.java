package com.knowledgegraph.library;

import com.knowledgegraph.common.ApiResponse;
import com.knowledgegraph.library.dto.LibraryCreateRequest;
import com.knowledgegraph.library.dto.LibraryDetail;
import com.knowledgegraph.library.dto.LibraryUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库接口（§12.3）。
 */
@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping
    public ApiResponse<List<LibraryDetail>> list() {
        return ApiResponse.ok(libraryService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<LibraryDetail> get(@PathVariable long id) {
        return ApiResponse.ok(libraryService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LibraryDetail> create(@Valid @RequestBody LibraryCreateRequest request) {
        return ApiResponse.ok(libraryService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<LibraryDetail> update(@PathVariable long id, @Valid @RequestBody LibraryUpdateRequest request) {
        return ApiResponse.ok(libraryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id) {
        return ApiResponse.ok(libraryService.delete(id));
    }

    public record LibraryAliasesRequest(java.util.List<String> aliases) {
    }

    /** 维护知识库别名（整体替换；AI 自动整理按别名匹配同主题）。 */
    @PutMapping("/{id}/aliases")
    public ApiResponse<LibraryDetail> updateAliases(
            @PathVariable long id, @jakarta.validation.Valid @RequestBody LibraryAliasesRequest request) {
        return ApiResponse.ok(libraryService.replaceAliases(id, request.aliases()));
    }
}
