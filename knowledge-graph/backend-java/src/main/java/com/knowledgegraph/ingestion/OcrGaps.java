package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;

import java.util.ArrayList;
import java.util.List;

/**
 * 「图片存在但未被识别成文字」的归因与文案。
 *
 * <p>资料可以是 PDF、PPT 或纯图片 ZIP（§8.5），三者都先进 {@code document_units} 再分段成
 * {@code document_chunks}；问答检索只读 chunks。因此图片单元一旦没有转写文本，该部分知识就没有进入
 * 可复用语料。此时必须把两种原因分开说清楚，否则用户会把「没有启用视觉模型」误读成「我的文件里没有内容」：
 *
 * <ul>
 *   <li>{@code OCR_SKIPPED}：没有可用识别通道（未启用 vision=true 的模型档案）——配置后重试即可补全；</li>
 *   <li>{@code OCR_FAILED}：通道存在但调用失败或返回空文本——需要排查模型服务后重试；</li>
 *   <li>{@code NO_OCR}：该单元本来就没有文字（无文本的页/幻灯片），不属于缺口。</li>
 * </ul>
 */
public final class OcrGaps {

    /** {@code ingestion_jobs.error_message} 为 VARCHAR(500)，留出余量。 */
    static final int MAX_MESSAGE = 490;

    /** 上游报错可能很长。先单独截断原因，保证「原文已保留、可重试」这句一定不会被挤掉。 */
    static final int MAX_REASON = 300;

    private OcrGaps() {
    }

    /**
     * 全文分段为 0 时的失败归因。
     *
     * @param skippedUnits  状态为 OCR_SKIPPED / NEEDS_OCR 的图片单元数（没有识别通道）
     * @param failedUnits   状态为 OCR_FAILED 的图片单元数（识别被尝试但失败）
     * @param failureReason 最近一次识别失败的原因，可为 null
     */
    public static ApiException zeroChunkFailure(long skippedUnits, long failedUnits, String failureReason) {
        if (failedUnits > 0) {
            String reason = clipReason(failureReason);
            return new ApiException(422, ErrorCodes.DOCUMENT_OCR_FAILED, shorten(
                    "该资料的 " + failedUnits + " 张图片识别失败" + reason
                            + "，未产生可用原文。原始文件与图片单元已保留，可在处理中心重试本任务。"));
        }
        if (skippedUnits > 0) {
            return new ApiException(422, ErrorCodes.DOCUMENT_OCR_REQUIRED, shorten(
                    "该资料是纯图片或扫描内容，共 " + skippedUnits + " 张图片需要识别，但当前没有启用具备视觉能力的模型档案。"
                            + "请在「系统设置 → 模型档案」勾选「视觉能力」并启用后，回到处理中心重试本任务；"
                            + "原始文件与图片单元已保留，这部分知识不会丢失。"));
        }
        return new ApiException(422, ErrorCodes.DOCUMENT_NO_EXTRACTABLE_TEXT,
                "文档中没有可抽取的文本片段，无法进行知识抽取");
    }

    /**
     * 仍有分段、但有图片未识别时的告警（写入任务 error_message）。
     * 返回 null 表示没有缺口，本次入库的知识是完整的。
     */
    public static String partialWarning(long skippedUnits, long failedUnits, String failureReason) {
        List<String> parts = new ArrayList<>();
        if (skippedUnits > 0) {
            parts.add(skippedUnits + " 张图片未识别：未启用具备视觉能力的模型档案（系统设置 → 模型档案 → 视觉能力）");
        }
        if (failedUnits > 0) {
            String reason = clipReason(failureReason);
            parts.add(failedUnits + " 张图片识别失败" + reason);
        }
        if (parts.isEmpty()) {
            return null;
        }
        return shorten("部分图片内容未进入原文语料，本次入库的知识不完整：" + String.join("；", parts)
                + "。配置或修复模型后可重试本任务补全。");
    }

    private static String shorten(String message) {
        return message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message;
    }

    /** 把原因压到一行并截断，空原因返回空串（调用处负责加括号）。 */
    private static String clipReason(String reason) {
        if (reason == null) {
            return "";
        }
        String cleaned = reason.replaceAll("\\s+", " ").strip();
        if (cleaned.isEmpty()) {
            return "";
        }
        String clipped = cleaned.length() <= MAX_REASON ? cleaned : cleaned.substring(0, MAX_REASON) + "…";
        return "（" + clipped + "）";
    }
}
