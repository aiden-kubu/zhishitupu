package com.knowledgegraph.graph;

import com.knowledgegraph.common.ApiResponse;
import com.knowledgegraph.graph.dto.EdgeCreateRequest;
import com.knowledgegraph.graph.dto.EdgeDetail;
import com.knowledgegraph.graph.dto.EdgeUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 关系 CRUD 接口（§12.2）。
 */
@RestController
@RequestMapping("/api/edges")
public class EdgeController {

    private final EdgeService edgeService;

    public EdgeController(EdgeService edgeService) {
        this.edgeService = edgeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EdgeDetail> create(@Valid @RequestBody EdgeCreateRequest request) {
        return ApiResponse.ok(edgeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<EdgeDetail> update(@PathVariable long id, @Valid @RequestBody EdgeUpdateRequest request) {
        return ApiResponse.ok(edgeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id) {
        return ApiResponse.ok(edgeService.delete(id));
    }
}
