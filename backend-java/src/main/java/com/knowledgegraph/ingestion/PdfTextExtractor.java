package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 解析（§8.5）：按页提取文本并保留页码；文本过少的页面（<20 字符）判定为扫描页，
 * 渲染为 PNG 等待 OCR。
 */
@Component
public class PdfTextExtractor {

    private static final int MIN_TEXT_CHARS = 20;

    public List<ParsedUnit> extract(byte[] content) {
        List<ParsedUnit> units = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = document.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                boolean needsOcr = text == null || text.strip().length() < MIN_TEXT_CHARS;
                byte[] image = null;
                if (needsOcr) {
                    image = renderPage(renderer, page - 1);
                }
                units.add(new ParsedUnit("page", page, "第 " + page + " 页", text, needsOcr, image, "png"));
            }
            return units;
        } catch (IOException ex) {
            throw new ApiException(422, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "PDF 解析失败：" + ex.getMessage());
        }
    }

    private byte[] renderPage(PDFRenderer renderer, int pageIndex) {
        try {
            var image = renderer.renderImage(pageIndex, 1.5f);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                javax.imageio.ImageIO.write(image, "png", output);
                return output.toByteArray();
            }
        } catch (IOException ex) {
            // 渲染失败不阻断整个任务，该页稍后按无 OCR 处理
            return null;
        }
    }
}
