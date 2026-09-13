package com.knowledgegraph.graph;

import com.knowledgegraph.common.ApiResponse;
import com.knowledgegraph.graph.dto.NodeCreateRequest;
import com.knowledgegraph.graph.dto.NodeDetail;
import com.knowledgegraph.graph.dto.NodeUpdateRequest;
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

import java.util.Map;

/**
 * 节点 CRUD 接口（§12.2）。
 */
@RestController
@RequestMapping("/api/nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @GetMapping("/{id}")
    public ApiResponse<NodeDetail> get(@PathVariable long id) {
        return ApiResponse.ok(nodeService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NodeDetail> create(@Valid @RequestBody NodeCreateRequest request) {
        return ApiResponse.ok(nodeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<NodeDetail> update(@PathVariable long id, @Valid @RequestBody NodeUpdateRequest request) {
        return ApiResponse.ok(nodeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id) {
        return ApiResponse.ok(nodeService.delete(id));
    }
}
