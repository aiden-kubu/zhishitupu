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
                () -> zipSafety.safeExtractEntries(malicious, tempDir));
        assertTrue(ex.getMessage().contains("路径穿越"));
    }

    @Test
    void rejectsUnsupportedEntries() {
        byte[] archive = zipOf("page1.png", "page2.txt");
        ApiException ex = assertThrows(ApiException.class,
                () -> zipSafety.safeExtractEntries(archive, tempDir));
        assertTrue(ex.getMessage().contains("仅支持"));
    }

    @Test
    void rejectsEmptyArchive() {
        ApiException ex = assertThrows(ApiException.class,
                () -> zipSafety.safeExtractEntries(new byte[0], tempDir));
        assertTrue(ex.getMessage().contains("没有找到") || ex.getMessage().contains("解压失败"));
    }

    @Test
    void allowsPdfEntriesAlongsideImages() throws Exception {
        // 用户反馈：ZIP 内的 PDF 曾被判为非法条目
        byte[] archive = zipOf("book/dsacpp-3rd-edn.pdf", "book/cover.png");
        zipSafety.validateStructure(archive); // 上传预扫必须放行
        List<ZipSafety.ExtractedEntry> entries = zipSafety.safeExtractEntries(archive, tempDir);
        assertEquals(2, entries.size());
        assertEquals("book/cover.png", entries.get(0).entryName());
        assertEquals("book/dsacpp-3rd-edn.pdf", entries.get(1).entryName());
    }

    @Test
    void stillRejectsOtherDocumentTypes() {
        ApiException ex = assertThrows(ApiException.class,
                () -> zipSafety.validateStructure(zipOf("notes.docx")));
        assertTrue(ex.getMessage().contains("仅支持"));
        assertTrue(ex.getMessage().contains("PDF"));
    }

    @Test
    void extractsImagesInNaturalOrder() throws Exception {
        byte[] archive = zipOf("p10.jpg", "p2.jpg", "p1.jpg", "cover.png");
        List<ZipSafety.ExtractedEntry> images = zipSafety.safeExtractEntries(archive, tempDir);
        assertEquals(4, images.size());
        assertEquals("cover.png", images.get(0).entryName());
        assertEquals("p1.jpg", images.get(1).entryName());
        assertEquals("p2.jpg", images.get(2).entryName());
        assertEquals("p10.jpg", images.get(3).entryName());
        // 临时目录确实写入了文件且仍在受控目录内
        assertTrue(Files.exists(tempDir.resolve("p2.jpg")));
    }
}
