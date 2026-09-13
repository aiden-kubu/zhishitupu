package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP 安全解压（§14.1）：
 * - 条目路径规范化后必须仍位于任务临时目录（防 Zip Slip）；
 * - 限制条目数量、解压后总体积与压缩比；
 * - 仅接受 JPG/JPEG/PNG/WebP 图片条目；
 * - 图片按文件名自然排序（书本照片页序）。
 */
@Component
public class ZipSafety {

    public static final int MAX_ENTRIES = 500;
    public static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;
    public static final long MAX_RATIO = 100;
    private static final java.util.Set<String> ALLOWED_IMAGE_EXTENSIONS = java.util.Set.of("jpg", "jpeg", "png", "webp");

    public record ExtractedImage(String entryName, byte[] content) {
    }

    /** 上传时的轻量结构预扫：路径穿越 / 非法类型 / 条目数，全部合格才允许入库。 */
    public void validateStructure(byte[] zipBytes) {
        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                            "ZIP 内图片超过 " + MAX_ENTRIES + " 张上限");
                }
                String entryName = entry.getName();
                Path target = Path.of("scan-root").resolve(entryName).normalize();
                if (!target.startsWith("scan-root")) {
                    throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                            "ZIP 含非法路径条目（路径穿越攻击已拦截）：" + entryName);
                }
                String extension = extensionOf(entryName);
                if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
                    throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                            "ZIP 仅支持 JPG/JPEG/PNG/WebP 图片，发现非法条目：" + entryName);
                }
            }
        } catch (IOException ex) {
            throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "ZIP 读取失败：" + ex.getMessage());
        }
        if (entries == 0) {
            throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                    "ZIP 内没有找到 JPG/JPEG/PNG/WebP 图片");
        }
    }

    /** 校验并解压到临时目录；返回按自然顺序排列的图片内容。 */
    public List<ExtractedImage> safeExtractImages(byte[] zipBytes, Path tempDir) {
        List<ExtractedImage> images = new ArrayList<>();
        int entries = 0;
        long totalBytes = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                            "ZIP 内图片超过 " + MAX_ENTRIES + " 张上限");
                }
                String entryName = entry.getName();
                Path target = tempDir.resolve(entryName).normalize();
                if (!target.startsWith(tempDir)) {
                    throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                            "ZIP 含非法路径条目（路径穿越攻击已拦截）：" + entryName);
                }
                String extension = extensionOf(entryName);
                if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
                    throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                            "ZIP 仅支持 JPG/JPEG/PNG/WebP 图片，发现非法条目：" + entryName);
                }
                long compressed = entry.getCompressedSize();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = zis.read(chunk)) != -1) {
                    totalBytes += read;
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                                "ZIP 解压后总大小超过 1GB 上限");
                    }
                    buffer.write(chunk, 0, read);
                }
                long uncompressed = buffer.size();
                if (compressed > 0 && uncompressed / compressed > MAX_RATIO) {
                    throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                            "ZIP 压缩比异常（疑似压缩炸弹）：" + entryName);
                }
                images.add(new ExtractedImage(entryName, buffer.toByteArray()));
                Files.createDirectories(target.getParent());
                Files.write(target, buffer.toByteArray());
            }
        } catch (IOException ex) {
            throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "ZIP 解压失败：" + ex.getMessage());
        }
        if (images.isEmpty()) {
            throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                    "ZIP 内没有找到 JPG/JPEG/PNG/WebP 图片");
        }
        images.sort(Comparator.comparing(ExtractedImage::entryName, naturalOrderComparator()));
        return images;
    }

    /** 文件名自然排序：数字段按数值比较（p2 在 p10 之前）。 */
    public static Comparator<String> naturalOrderComparator() {
        return (a, b) -> {
            String lowerA = a.toLowerCase(Locale.ROOT);
            String lowerB = b.toLowerCase(Locale.ROOT);
            int i = 0;
            int j = 0;
            while (i < lowerA.length() && j < lowerB.length()) {
                char ca = lowerA.charAt(i);
                char cb = lowerB.charAt(j);
                if (Character.isDigit(ca) && Character.isDigit(cb)) {
                    int si = i;
                    int sj = j;
                    while (i < lowerA.length() && Character.isDigit(lowerA.charAt(i))) i++;
                    while (j < lowerB.length() && Character.isDigit(lowerB.charAt(j))) j++;
                    long na = Long.parseLong(lowerA, si, i, 10);
                    long nb = Long.parseLong(lowerB, sj, j, 10);
                    if (na != nb) {
                        return Long.compare(na, nb);
                    }
                } else {
                    if (ca != cb) {
                        return Character.compare(ca, cb);
                    }
                    i++;
                    j++;
                }
            }
            return Integer.compare(lowerA.length() - i, lowerB.length() - j);
        };
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
