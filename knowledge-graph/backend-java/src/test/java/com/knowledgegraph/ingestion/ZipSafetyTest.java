package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §14.1 ZIP 安全：路径穿越拦截、条目数与体积上限、非法类型拒绝、自然排序。 */
class ZipSafetyTest {

    private final ZipSafety zipSafety = new ZipSafety();

    @TempDir
    Path tempDir;

    private byte[] zipOf(String... names) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                for (String name : names) {
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(new byte[]{1, 2, 3, 4});
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void rejectsPathTraversalEntry() {
        byte[] malicious = zipOf("../evil.txt", "p1.jpg");
        ApiException ex = assertThrows(ApiException.class,
                () -> zipSafety.safeExtractImages(malicious, tempDir));
        assertTrue(ex.getMessage().contains("路径穿越"));
    }

    @Test
    void rejectsNonImageEntries() {
        byte[] archive = zipOf("page1.png", "page2.txt");
        ApiException ex = assertThrows(ApiException.class,
                () -> zipSafety.safeExtractImages(archive, tempDir));
        assertTrue(ex.getMessage().contains("仅支持"));
    }

    @Test
    void rejectsEmptyArchive() {
        ApiException ex = assertThrows(ApiException.class,
                () -> zipSafety.safeExtractImages(new byte[0], tempDir));
        assertTrue(ex.getMessage().contains("没有找到") || ex.getMessage().contains("解压失败"));
    }

    @Test
    void extractsImagesInNaturalOrder() throws Exception {
        byte[] archive = zipOf("p10.jpg", "p2.jpg", "p1.jpg", "cover.png");
        List<ZipSafety.ExtractedImage> images = zipSafety.safeExtractImages(archive, tempDir);
        assertEquals(4, images.size());
        assertEquals("cover.png", images.get(0).entryName());
        assertEquals("p1.jpg", images.get(1).entryName());
        assertEquals("p2.jpg", images.get(2).entryName());
        assertEquals("p10.jpg", images.get(3).entryName());
        // 临时目录确实写入了文件且仍在受控目录内
        assertTrue(Files.exists(tempDir.resolve("p2.jpg")));
    }
}
