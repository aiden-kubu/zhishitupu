package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「图片存在但没被识别成文字」的归因文案（纯逻辑，不依赖 Spring 与数据库）。
 * 关键约束：缺视觉模型与识别失败必须给出不同错误码，且两者都不能被误报成「文档里没有文本」。
 */
class OcrGapsTest {

    @Test
    void imagesWithoutVisionChannelAskForVisionModel() {
        ApiException ex = OcrGaps.zeroChunkFailure(3, 0, null);
        assertEquals(422, ex.getHttpStatus());
        assertEquals(ErrorCodes.DOCUMENT_OCR_REQUIRED, ex.getCode());
        assertTrue(ex.getMessage().contains("3 张图片"), ex.getMessage());
        assertTrue(ex.getMessage().contains("视觉能力"), ex.getMessage());
        assertTrue(ex.getMessage().contains("模型档案"), ex.getMessage());
        assertTrue(ex.getMessage().contains("重试"), ex.getMessage());
    }

    @Test
    void attemptedButFailedOcrKeepsTheReason() {
        ApiException ex = OcrGaps.zeroChunkFailure(0, 2, "连接超时");
        assertEquals(ErrorCodes.DOCUMENT_OCR_FAILED, ex.getCode());
        assertTrue(ex.getMessage().contains("2 张图片识别失败"), ex.getMessage());
        assertTrue(ex.getMessage().contains("连接超时"), ex.getMessage());
    }

    @Test
    void failureOutranksMissingChannelBecauseItNeedsOtherAction() {
        assertEquals(ErrorCodes.DOCUMENT_OCR_FAILED, OcrGaps.zeroChunkFailure(5, 1, null).getCode());
    }

    @Test
    void trulyEmptyDocumentKeepsTheOriginalErrorCode() {
        ApiException ex = OcrGaps.zeroChunkFailure(0, 0, null);
        assertEquals(ErrorCodes.DOCUMENT_NO_EXTRACTABLE_TEXT, ex.getCode());
        assertTrue(ex.getMessage().contains("没有可抽取的文本片段"), ex.getMessage());
    }

    @Test
    void partialWarningNamesBothKindsOfGap() {
        String warning = OcrGaps.partialWarning(2, 1, "密钥无效");
        assertTrue(warning.contains("2 张图片未识别"), warning);
        assertTrue(warning.contains("视觉能力"), warning);
        assertTrue(warning.contains("1 张图片识别失败"), warning);
        assertTrue(warning.contains("密钥无效"), warning);
        assertTrue(warning.contains("不完整"), warning);
    }

    @Test
    void partialWarningIsNullWhenNothingWasMissed() {
        assertNull(OcrGaps.partialWarning(0, 0, null));
    }

    @Test
    void messagesStayWithinTheJobColumnLimit() {
        String longReason = "错误".repeat(400);
        assertTrue(OcrGaps.zeroChunkFailure(0, 1, longReason).getMessage().length() <= OcrGaps.MAX_MESSAGE);
        assertTrue(OcrGaps.partialWarning(9, 9, longReason).length() <= OcrGaps.MAX_MESSAGE);
    }

    /** 上游报错很长时只截断原因本身，「原文已保留、可重试」这句必须留在消息里。 */
    @Test
    void longUpstreamReasonIsClippedWithoutLosingTheActionableTail() {
        String longReason = "视觉模型调用失败：模型服务返回异常（HTTP 400）" + "很长的上游原始响应".repeat(40);

        String failure = OcrGaps.zeroChunkFailure(0, 3, longReason).getMessage();
        assertTrue(failure.length() <= OcrGaps.MAX_MESSAGE, failure.length() + " 超出列宽");
        assertTrue(failure.contains("可在处理中心重试本任务"), failure);
        assertTrue(failure.contains("原始文件与图片单元已保留"), failure);

        String warning = OcrGaps.partialWarning(0, 3, longReason);
        assertTrue(warning.length() <= OcrGaps.MAX_MESSAGE, warning.length() + " 超出列宽");
        assertTrue(warning.contains("配置或修复模型后可重试本任务补全"), warning);
    }

    /** 原因里的换行与多余空白压成一行，避免消息在界面里被拆散。 */
    @Test
    void multilineReasonIsFlattened() {
        String failure = OcrGaps.zeroChunkFailure(0, 1, "第一行\n\n第二行    第三行").getMessage();
        assertTrue(failure.contains("（第一行 第二行 第三行）"), failure);
    }
}
