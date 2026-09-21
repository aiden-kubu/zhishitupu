package com.knowledgegraph.search.dto;

import java.util.List;

/**
 * POST /api/search/ai-expand 响应（任务约定 §13）。
 * created=false 表示请求到达时节点已存在，本次未调用模型、未写库。
 */
public record AiExpandResult(NodeView node, boolean created, int relationsCreated) {

    public record NodeView(long id, String name, String nameEn, String type,
                           String definition, List<String> aliases) {
    }
}
