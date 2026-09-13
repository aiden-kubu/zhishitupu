package com.knowledgegraph.ingestion;

/**
 * OCR Provider（§8.5/§13）：屏蔽本地或云端（视觉模型）差异。
 * V1 默认实现为视觉模型通道（用户多模型决策：勾选「视觉能力」的档案承担图片识别），
 * 本地 Tesseract 等实现位置保留，安装前需用户确认。
 */
public interface OcrProvider {

    /** 当前是否有可用的识别通道（如已配置具备视觉能力的模型档案）。 */
    boolean isAvailable();

    /**
     * 转写图片中的全部文字。
     *
     * @param image        图片字节
     * @param imageFormat  png / jpeg / webp
     * @return 识别出的文本
     * @throws com.knowledgegraph.common.ApiException 通道不可用或调用失败
     */
    String transcribe(byte[] image, String imageFormat);
}
