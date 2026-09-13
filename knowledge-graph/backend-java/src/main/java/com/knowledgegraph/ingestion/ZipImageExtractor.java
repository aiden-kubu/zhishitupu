package com.knowledgegraph.ingestion;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 书本照片 ZIP 解析（§8.5）：安全解压后逐张生成图片单元，按文件名自然排序。
 */
@Component
public class ZipImageExtractor {

    private final ZipSafety zipSafety;

    public ZipImageExtractor(ZipSafety zipSafety) {
        this.zipSafety = zipSafety;
    }

    public List<ParsedUnit> extract(byte[] content) {
        List<ParsedUnit> units = new ArrayList<>();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kg-zip-");
            List<ZipSafety.ExtractedImage> images = zipSafety.safeExtractImages(content, tempDir);
            int index = 1;
            for (ZipSafety.ExtractedImage image : images) {
                String extension = image.entryName().substring(image.entryName().lastIndexOf('.') + 1).toLowerCase();
                String mime = switch (extension) {
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    default -> "image/webp";
                };
                units.add(new ParsedUnit("image", index, image.entryName(), null, true,
                        image.content(), mime));
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
