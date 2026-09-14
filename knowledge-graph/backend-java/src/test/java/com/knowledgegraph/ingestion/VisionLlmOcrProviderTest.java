package com.knowledgegraph.ingestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 视觉通道的 data URL 构造。
 * 回归点：MIME 必须是裸格式名（png/jpeg/webp），一旦拼成 data:image/image/png，
 * 视觉模型会一律判为「不支持的图片」，ZIP 图片就永远识别不了。
 */
class VisionLlmOcrProviderTest {

    @Test
    void dataUrlUsesBareMimeType() {
        String url = VisionLlmOcrProvider.buildDataUrl(new byte[]{1, 2, 3}, "png");
        assertTrue(url.startsWith("data:image/png;base64,"), url);
    }

    @Test
    void alreadyPrefixedFormatIsNormalisedNotDoubled() {
        assertEquals("data:image/png;base64,AQID", VisionLlmOcrProvider.buildDataUrl(new byte[]{1, 2, 3}, "image/png"));
        assertEquals("data:image/jpeg;base64,AQID", VisionLlmOcrProvider.buildDataUrl(new byte[]{1, 2, 3}, "image/jpeg"));
        assertEquals("data:image/webp;base64,AQID", VisionLlmOcrProvider.buildDataUrl(new byte[]{1, 2, 3}, "image/webp"));
    }

    @Test
    void jpgIsWrittenAsCanonicalJpeg() {
        assertEquals("jpeg", VisionLlmOcrProvider.normalizeFormat("jpg"));
        assertEquals("jpeg", VisionLlmOcrProvider.normalizeFormat("JPG"));
        assertEquals("jpeg", VisionLlmOcrProvider.normalizeFormat(" image/jpeg "));
    }

    @Test
    void unknownOrMissingFormatFallsBackToPng() {
        assertEquals("png", VisionLlmOcrProvider.normalizeFormat(null));
        assertEquals("png", VisionLlmOcrProvider.normalizeFormat(""));
        assertEquals("png", VisionLlmOcrProvider.normalizeFormat("bmp"));
    }
}
