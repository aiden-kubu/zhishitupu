package com.knowledgegraph.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ZIP 内条目单元化：
 * - 图片单元保持 OcrProvider 约定的裸格式名（不能带 image/ 前缀）；
 * - PDF 条目按页解析，生成 page 单元，定位信息带上 ZIP 内文件名（回归用户反馈的「ZIP 里的 PDF 被拒」）。
 */
class ZipImageExtractorTest {

    private final ZipImageExtractor extractor = new ZipImageExtractor(new ZipSafety(), new PdfTextExtractor());

    @Test
    void imageUnitsCarryBareFormatsAcceptedByTheOcrContract() {
        List<ParsedUnit> units = extractor.extract(zip("book-page-2.png", "book-page-1.png", "scan.jpeg", "shot.webp"));

        // 文件名自然排序，保证书本照片页序
        assertEquals(List.of("book-page-1.png", "book-page-2.png", "scan.jpeg", "shot.webp"),
                units.stream().map(ParsedUnit::sourceLocator).toList());
        assertEquals(List.of("png", "png", "jpeg", "webp"),
                units.stream().map(ParsedUnit::imageFormat).toList());
        assertTrue(units.stream().allMatch(ParsedUnit::needsOcr));
        assertTrue(units.stream().allMatch(unit -> "image".equals(unit.unitType())));
    }

    @Test
    void pdfEntryInsideZipBecomesPageUnitsWithEntryNameInLocator() {
        byte[] archive = zipWithPdf("textbook/dsacpp-3rd-edn.pdf", "textbook/cover.png");

        List<ParsedUnit> units = extractor.extract(archive);

        // 封面图片在前（自然排序），PDF 的每一页在后
        assertEquals("textbook/cover.png", units.get(0).sourceLocator());
        assertEquals("image", units.get(0).unitType());
        List<ParsedUnit> pages = units.stream().filter(u -> "page".equals(u.unitType())).toList();
        assertEquals(2, pages.size(), "两页 PDF 应生成两个 page 单元");
        assertTrue(pages.stream().allMatch(p -> p.sourceLocator().startsWith("textbook/dsacpp-3rd-edn.pdf ")),
                "PDF 页定位必须带上 ZIP 内文件名，证据才能追溯到具体分册");
        assertTrue(pages.stream().allMatch(p -> p.text() != null && p.text().contains("ZIP PDF page")),
                "PDF 页文本必须被解析出来");
        assertTrue(pages.stream().noneMatch(ParsedUnit::needsOcr), "文本页不需要 OCR");
    }

    private static byte[] zip(String... entryNames) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (String name : entryNames) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
                zip.closeEntry();
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        return out.toByteArray();
    }

    private static byte[] zipWithPdf(String pdfEntryName, String... imageEntries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (String name : imageEntries) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(pdfEntryName));
            zip.write(twoPagePdf());
            zip.closeEntry();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        return out.toByteArray();
    }

    private static byte[] twoPagePdf() {
        try (PDDocument document = new PDDocument()) {
            for (int page = 1; page <= 2; page++) {
                PDPage pdfPage = new PDPage(PDRectangle.A4);
                document.addPage(pdfPage);
                try (PDPageContentStream content = new PDPageContentStream(document, pdfPage)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText("ZIP PDF page " + page + " text for extraction regression");
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
