package tools;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 阶段 D 验收测试夹具生成器（§18，无版权风险的自制样例）：
 *  1. sample-text.pdf   3 页文本 PDF（含中文，使用本机黑体）
 *  2. sample-deck.pptx  3 页幻灯片（标题+正文+备注）
 *  3. sample-book.zip   3 张中文页面图片（扫描件模拟，供视觉 OCR）
 *  4. evil.zip          含 ../ 路径穿越条目的恶意压缩包
 * 运行：java -cp target/test-classes;target/classes;<deps> tools.FixtureGen <输出目录>
 */
public final class FixtureGen {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/fixtures");
        Files.createDirectories(out);

        // 1. 文本 PDF
        try (PDDocument doc = new PDDocument()) {
            var font = org.apache.pdfbox.pdmodel.font.PDType0Font.load(doc,
                    new java.io.FileInputStream("C:/Windows/Fonts/simhei.ttf"), true);
            String[] pages = {
                    "计算机网络 第一章 概述。计算机网络是互连的、自治的计算机系统的集合。"
                            + "网络把多台计算机连接在一起，互联网则把多个网络连接在一起。"
                            + "本课程覆盖从物理层到应用层的全部核心协议。",
                    "第二章 物理层与数据链路层。物理层关心比特如何在传输媒体上移动，"
                            + "数据链路层负责成帧、差错检测与流量控制。以太网是最常见的有线链路层协议。",
                    "第三章 传输层。传输层为主机间的进程提供端到端通信。"
                            + "TCP 提供可靠的、面向字节流的传输服务；UDP 提供无连接的尽力而为服务。"
                            + "三次握手是 TCP 建立连接的必要步骤。"
            };
            for (String pageText : pages) {
                var page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                doc.addPage(page);
                try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(font, 14);
                    stream.newLineAtOffset(50, 750);
                    // 手工按宽度换行（简化：每 30 字符一行）
                    for (int i = 0; i < pageText.length(); i += 30) {
                        stream.showText(pageText.substring(i, Math.min(i + 30, pageText.length())));
                        stream.newLineAtOffset(0, -22);
                    }
                    stream.endText();
                }
            }
            doc.save(out.resolve("sample-text.pdf").toFile());
        }

        // 2. PPTX
        try (XMLSlideShow show = new XMLSlideShow()) {
            show.setPageSize(new java.awt.Dimension(960, 540));
            String[][] slides = {
                    {"课程导入", "本讲介绍计算机网络的整体框架，建立分层思维模型。", "备注：先提问学生对网络的直观认识。"},
                    {"分层模型", "OSI 七层与 TCP/IP 四层对照；每一层的职责与代表协议。", "备注：重点讲清分层解耦的意义。"},
                    {"传输层速览", "TCP 面向连接、可靠传输；UDP 无连接、低开销。", "备注：为下一讲三次握手做铺垫。"}
            };
            for (String[] slideData : slides) {
                XSLFSlide slide = show.createSlide();
                XSLFTextBox title = slide.createTextBox();
                title.setAnchor(new java.awt.Rectangle(40, 30, 880, 60));
                title.setText(slideData[0]);
                XSLFTextBox body = slide.createTextBox();
                body.setAnchor(new java.awt.Rectangle(40, 110, 880, 300));
                body.setText(slideData[1]);
                XSLFTextBox note = slide.createTextBox();
                note.setAnchor(new java.awt.Rectangle(40, 430, 880, 60));
                note.setText(slideData[2]);
            }
            try (OutputStream output = Files.newOutputStream(out.resolve("sample-deck.pptx"))) {
                show.write(output);
            }
        }

        // 3. 书本照片 ZIP（3 张中文页面图片，模拟扫描件）
        String[] pageTexts = {
                "第 1 页  传输控制协议 TCP 是一种面向连接的、可靠的传输层协议，通过三次握手建立连接。",
                "第 2 页  用户数据报协议 UDP 提供无连接的传输服务，开销小、时延低，适用于实时应用。",
                "第 3 页  三次握手：客户端发送 SYN，服务器回复 SYN+ACK，客户端再发送 ACK，连接建立。"
        };
        Path zipPath = out.resolve("sample-book.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (int i = 1; i <= pageTexts.length; i++) {
                BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 800, 600);
                g.setColor(Color.BLACK);
                g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 24));
                String text = pageTexts[i - 1];
                int line = 80;
                for (int start = 0; start < text.length(); start += 26) {
                    g.drawString(text.substring(start, Math.min(start + 26, text.length())), 60, line);
                    line += 40;
                }
                g.dispose();
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(image, "png", png);
                zip.putNextEntry(new ZipEntry("book-page-" + i + ".png"));
                zip.write(png.toByteArray());
                zip.closeEntry();
            }
        }

        // 4. 恶意 ZIP（路径穿越夹具）
        Path evilPath = out.resolve("evil.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(evilPath))) {
            zip.putNextEntry(new ZipEntry("../evil.txt"));
            zip.write("malicious".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("p1.jpg"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }

        System.out.println("fixtures generated at " + out.toAbsolutePath());
        for (String name : new String[]{"sample-text.pdf", "sample-deck.pptx", "sample-book.zip", "evil.zip"}) {
            System.out.println(name + " -> " + Files.size(out.resolve(name)) + " bytes");
        }
    }

    private FixtureGen() {
    }
}
