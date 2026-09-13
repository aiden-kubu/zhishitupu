package com.knowledgegraph.search;

import com.knowledgegraph.common.ApiResponse;
import com.knowledgegraph.graph.dto.SubgraphData;
import com.knowledgegraph.search.dto.SuggestionItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索接口（§12.2）。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** 联想：按「完全命中&gt;前缀&gt;别名&gt;名称包含&gt;定义包含」排序。 */
    @GetMapping("/suggestions")
    public ApiResponse<List<SuggestionItem>> suggestions(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) Long libraryId,
                                                         @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(searchService.suggestions(q, libraryId, limit));
    }

    /** 关键词搜索：以最佳命中节点为中心的子图。 */
    @GetMapping
    public ApiResponse<SubgraphData> search(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Long libraryId,
                                            @RequestParam(required = false) Integer depth,
                                            @RequestParam(required = false) Integer limit) {
        if (q == null || q.isBlank()) {
            throw com.knowledgegraph.common.ApiException.badRequest("缺少搜索关键词 q");
        }
        return ApiResponse.ok(searchService.search(q, libraryId, depth, limit));
    }
}
