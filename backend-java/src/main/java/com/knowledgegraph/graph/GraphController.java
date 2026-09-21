package com.knowledgegraph.graph;

import com.knowledgegraph.common.ApiResponse;
import com.knowledgegraph.graph.dto.SubgraphData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图谱子图接口（§12.2）。
 */
@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    /** 全局总览：按关联度取前 N 个代表性节点（默认 300，上限 300）。 */
    @GetMapping("/overview")
    public ApiResponse<SubgraphData> overview(@RequestParam(required = false) Long libraryId,
                                              @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(graphService.overview(libraryId, limit));
    }

    /** 邻域子图：depth 1 或 2，节点/边上限 500/1200。 */
    @GetMapping("/nodes/{id}/neighbors")
    public ApiResponse<SubgraphData> neighbors(@PathVariable("id") long id,
                                               @RequestParam(required = false) Integer depth,
                                               @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(graphService.neighbors(id, depth, limit));
    }
}
