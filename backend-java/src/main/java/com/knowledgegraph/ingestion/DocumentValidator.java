package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 上传校验（§8.2）：只看扩展名不够，校验真实魔数 MIME。
 */
@Component
public class DocumentValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "ppt", "pptx", "zip");

    /** PDF = %PDF-；pptx/zip = PK\x03\x04；ppt = OLE2 头 D0 CF 11 E0 */
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    public void validate(String originalName, byte[] head, long sizeBytes, int maxUploadSizeMb) {
        if (originalName == null || originalName.isBlank()) {
            throw new ApiException(400, ErrorCodes.INVALID_ARGUMENT, "文件名不能为空");
        }
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                    "仅支持 .pdf / .ppt / .pptx / .zip（书本照片）文件");
        }
        if (sizeBytes > (long) maxUploadSizeMb * 1024 * 1024) {
            throw new ApiException(413, ErrorCodes.PAYLOAD_TOO_LARGE,
                    "文件超过 " + maxUploadSizeMb + "MB 上限");
        }
        if (!magicMatches(extension, head)) {
            throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                    "文件内容与扩展名不符（已校验真实文件头）");
        }
    }

    public String extensionOf(String originalName) {
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            throw new ApiException(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "文件缺少扩展名");
        }
        return originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean magicMatches(String extension, byte[] head) {
        return switch (extension) {
            case "pdf" -> startsWith(head, PDF_MAGIC);
            case "pptx", "zip" -> startsWith(head, ZIP_MAGIC);
            case "ppt" -> startsWith(head, OLE2_MAGIC);
            default -> false;
        };
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
