package com.knowledgegraph.search.dto;

import java.util.List;

/**
 * 联想项。sourceCount 语义按任务约定为「直接关联数」（与 degree 相同）。
 */
public record SuggestionItem(long id, String name, String nameEn, List<String> aliases, String type,
                             int sourceCount, int degree, int tier) {
}
