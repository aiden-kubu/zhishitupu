package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.common.ErrorCodes;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 文档存储（§14.1）：磁盘名使用 UUID、不信任原始文件名；上传时计算 SHA-256 用于去重。
 */
@Component
public class DocumentStorage {

    private final Path root;

    public DocumentStorage(@org.springframework.beans.factory.annotation.Value("${kg.storage.root}") String root) {
        this.root = Path.of(root);
    }

    public record StoredFile(String storedName, Path path, String sha256) {
    }

    /** 保存上传文件到 {root}/documents/{uuid}.{ext}，返回存储信息与哈希。 */
    public StoredFile save(InputStream content, String extension) {
        try {
            Path directory = root.resolve("documents");
            Files.createDirectories(directory);
            String storedName = java.util.UUID.randomUUID() + "." + extension;
            Path target = directory.resolve(storedName);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (content; var output = Files.newOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = content.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            return new StoredFile(storedName, target, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new ApiException(500, ErrorCodes.INTERNAL_ERROR, "文件存储失败：" + ex.getMessage());
        }
    }

    public byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new ApiException(404, ErrorCodes.NOT_FOUND, "文件已不存在，无法继续解析");
        }
    }

    public void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 删除失败不阻断流程，孤儿文件由运维清理
        }
    }
}
