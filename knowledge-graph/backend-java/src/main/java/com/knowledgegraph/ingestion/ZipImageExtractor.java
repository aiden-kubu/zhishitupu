package com.knowledgegraph.ingestion;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ZIP 资料解析（§8.5）：安全解压后按文件名自然排序逐条生成单元——
 * 图片条目生成图片单元（走视觉 OCR 通道），PDF 条目按页提取文本（扫描页仍走 OCR）。
 */
@Component
public class ZipImageExtractor {

    private final ZipSafety zipSafety;
    private final PdfTextExtractor pdfTextExtractor;

    public ZipImageExtractor(ZipSafety zipSafety, PdfTextExtractor pdfTextExtractor) {
        this.zipSafety = zipSafety;
        this.pdfTextExtractor = pdfTextExtractor;
    }

    public List<ParsedUnit> extract(byte[] content) {
        List<ParsedUnit> units = new ArrayList<>();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kg-zip-");
            List<ZipSafety.ExtractedEntry> entries = zipSafety.safeExtractEntries(content, tempDir);
            int index = 1;
            for (ZipSafety.ExtractedEntry entry : entries) {
                String extension = entry.entryName().substring(entry.entryName().lastIndexOf('.') + 1).toLowerCase();
                if ("pdf".equals(extension)) {
                    // ZIP 内的 PDF：按页解析，定位信息带上 ZIP 内文件名，证据可追溯到具体分册与页码
                    for (ParsedUnit page : pdfTextExtractor.extract(entry.content())) {
                        units.add(new ParsedUnit(page.unitType(), index++, entry.entryName() + " " + page.sourceLocator(),
                                page.text(), page.needsOcr(), page.imageBytes(), page.imageFormat()));
                    }
                    continue;
                }
                // OcrProvider 约定的是裸格式名（png/jpeg/webp）。带 image/ 前缀会让 data URL 变成
                // data:image/image/png，视觉模型一律拒收，ZIP 图片就永远识别不了。
                String imageFormat = switch (extension) {
                    case "jpg", "jpeg" -> "jpeg";
                    case "png" -> "png";
                    default -> "webp";
                };
                units.add(new ParsedUnit("image", index, entry.entryName(), null, true,
                        entry.content(), imageFormat));
                index++;
            }
            return units;
        } catch (java.io.IOException ex) {
            throw new com.knowledgegraph.common.ApiException(500,
                    com.knowledgegraph.common.ErrorCodes.INTERNAL_ERROR, "临时目录创建失败：" + ex.getMessage());
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    private void deleteRecursively(Path dir) {
        if (!java.nio.file.Files.exists(dir)) {
            return;
        }
        try (var paths = java.nio.file.Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (java.io.IOException ignored) {
                    // 清理失败不阻断流程
                }
            });
        } catch (java.io.IOException ignored) {
            // 同上
        }
    }
}
