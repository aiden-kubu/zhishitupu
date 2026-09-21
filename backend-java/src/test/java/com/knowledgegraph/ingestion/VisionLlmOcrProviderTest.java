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
    @org.junit.jupiter.api.Test void distinguishesBlankFromUnreadableAndRejectsNarration() {
        org.junit.jupiter.api.Assertions.assertEquals(OcrProvider.Outcome.BLANK,
                VisionLlmOcrProvider.parseResult("{\"status\":\"BLANK\",\"text\":\"\"}").outcome());
        org.junit.jupiter.api.Assertions.assertEquals(OcrProvider.Outcome.UNREADABLE,
                VisionLlmOcrProvider.parseResult("{\"status\":\"UNREADABLE\",\"text\":\"\"}").outcome());
        for(String raw:java.util.List.of("图片中没有可识别文字", "{\"status\":\"TEXT\",\"text\":\"\"}",
                "{\"status\":\"BLANK\",\"text\":\"实际文字\"}"))
            org.junit.jupiter.api.Assertions.assertThrows(com.knowledgegraph.common.ApiException.class,()->VisionLlmOcrProvider.parseResult(raw));
        // A legitimate transcription mentioning this phrase must not be removed by keyword heuristics.
        org.junit.jupiter.api.Assertions.assertEquals("图片中没有可识别文字",VisionLlmOcrProvider.parseResult(
                "{\"status\":\"TEXT\",\"text\":\"图片中没有可识别文字\"}").text());
    }

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
