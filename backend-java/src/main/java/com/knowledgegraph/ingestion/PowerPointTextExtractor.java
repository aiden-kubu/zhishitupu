package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PPT/PPTX 解析（§8.5）：提取每页文字与备注并保留幻灯片号。
 * V1 边界：幻灯片内嵌图片不逐张 OCR（备注与正文优先），在交付说明中注明。
 */
@Component
public class PowerPointTextExtractor {

    public List<ParsedUnit> extract(byte[] content, String extension) {
        return switch (extension) {
            case "pptx" -> extractPptx(content);
            case "ppt" -> extractPpt(content);
            default -> throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                    "不支持的演示文稿格式：" + extension);
        };
    }

    private List<ParsedUnit> extractPptx(byte[] content) {
        List<ParsedUnit> units = new ArrayList<>();
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(content))) {
            int index = 1;
            for (XSLFSlide slide : show.getSlides()) {
                StringBuilder text = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape && !textShape.getText().isBlank()) {
                        text.append(textShape.getText().strip()).append('\n');
                    }
                }
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    for (XSLFShape shape : notes.getShapes()) {
                        if (shape instanceof XSLFTextShape textShape && !textShape.getText().isBlank()) {
                            text.append(textShape.getText().strip()).append('\n');
                        }
                    }
                }
                String value = text.toString().strip();
                units.add(new ParsedUnit("slide", index, "第 " + index + " 页（幻灯片）",
                        value.isEmpty() ? null : value, false, null, null));
                index++;
            }
            return units;
        } catch (IOException ex) {
            throw new ApiException(422, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "PPTX 解析失败：" + ex.getMessage());
        }
    }

    private List<ParsedUnit> extractPpt(byte[] content) {
        List<ParsedUnit> units = new ArrayList<>();
        try (HSLFSlideShow show = new HSLFSlideShow(new ByteArrayInputStream(content))) {
            int index = 1;
            for (HSLFSlide slide : show.getSlides()) {
                StringBuilder text = new StringBuilder();
                slide.getTextParagraphs().forEach(paragraphs -> {
                    String raw = HSLFTextParagraph.getRawText(paragraphs);
                    if (raw != null && !raw.isBlank()) {
                        text.append(raw.strip()).append('\n');
                    }
                });
                if (slide.getNotes() != null) {
                    slide.getNotes().getTextParagraphs().forEach(paragraphs -> {
                        String raw = HSLFTextParagraph.getRawText(paragraphs);
                        if (raw != null && !raw.isBlank()) {
                            text.append(raw.strip()).append('\n');
                        }
                    });
                }
                String value = text.toString().strip();
                units.add(new ParsedUnit("slide", index, "第 " + index + " 页（幻灯片）",
                        value.isEmpty() ? null : value, false, null, null));
                index++;
            }
            return units;
        } catch (IOException ex) {
            throw new ApiException(422, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "PPT 解析失败：" + ex.getMessage());
        }
    }
}
