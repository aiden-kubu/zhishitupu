package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §8.2 上传校验：扩展名白名单 + 真实魔数 + 大小上限。 */
class DocumentValidatorTest {

    private final DocumentValidator validator = new DocumentValidator();

    private static final byte[] PDF_HEAD = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
    private static final byte[] ZIP_HEAD = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
    private static final byte[] OLE2_HEAD = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0};
    private static final byte[] TEXT_HEAD = "hello world".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsMatchingMagic() {
        assertDoesNotThrow(() -> validator.validate("a.pdf", PDF_HEAD, 100, 200));
        assertDoesNotThrow(() -> validator.validate("a.pptx", ZIP_HEAD, 100, 200));
        assertDoesNotThrow(() -> validator.validate("a.zip", ZIP_HEAD, 100, 200));
        assertDoesNotThrow(() -> validator.validate("a.ppt", OLE2_HEAD, 100, 200));
    }

    @Test
    void rejectsExtensionMismatch() {
        ApiException ex = assertThrows(ApiException.class,
                () -> validator.validate("fake.pdf", TEXT_HEAD, 100, 200));
        assertTrue(ex.getMessage().contains("不符"));
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThrows(ApiException.class, () -> validator.validate("a.rar", ZIP_HEAD, 100, 200));
        assertThrows(ApiException.class, () -> validator.validate("noext", ZIP_HEAD, 100, 200));
    }

    @Test
    void rejectsOversize() {
        ApiException ex = assertThrows(ApiException.class,
                () -> validator.validate("a.pdf", PDF_HEAD, 201L * 1024 * 1024, 200));
        assertTrue(ex.getMessage().contains("上限"));
    }

    @Test
    void extractsLowercaseExtension() {
        assertEquals("pdf", validator.extensionOf("课程.PDF"));
        assertEquals("pptx", validator.extensionOf("a.b.pptx"));
    }
}
