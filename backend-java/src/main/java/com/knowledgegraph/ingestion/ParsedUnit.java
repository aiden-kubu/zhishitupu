package com.knowledgegraph.ingestion;

/**
 * 解析产物单元：页 / 幻灯片 / 图片。
 * needsOcr=true 时携带 imageBytes（PNG/JPEG）等待视觉模型转写；text 为已提取文本（可为空）。
 */
public record ParsedUnit(
        String unitType,
        int unitIndex,
        String sourceLocator,
        String text,
        boolean needsOcr,
        byte[] imageBytes,
        String imageFormat) {
}
