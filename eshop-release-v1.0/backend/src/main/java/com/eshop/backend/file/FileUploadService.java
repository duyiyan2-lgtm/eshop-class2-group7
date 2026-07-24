package com.eshop.backend.file;

import com.eshop.backend.admin.operationlog.OperationLogAction;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class FileUploadService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private final Path uploadRoot;

    public FileUploadService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @OperationLogAction(module = "文件管理", action = "上传商品图片")
    public FileUploadResponse uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        try {
            byte[] content = file.getBytes();
            String extension = detectImageExtension(content);
            if (extension == null) {
                throw new BusinessException(ErrorCode.INVALID_FILE);
            }
            Files.createDirectories(uploadRoot);
            String filename = UUID.randomUUID().toString().replace("-", "") + extension;
            Path target = uploadRoot.resolve(filename).normalize();
            if (!target.getParent().equals(uploadRoot)) {
                throw new BusinessException(ErrorCode.INVALID_FILE);
            }
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return new FileUploadResponse("/api/uploads/" + filename, filename, content.length);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED);
        }
    }

    private String detectImageExtension(byte[] content) {
        if (content.length >= 8
                && (content[0] & 0xff) == 0x89
                && content[1] == 0x50
                && content[2] == 0x4e
                && content[3] == 0x47
                && content[4] == 0x0d
                && content[5] == 0x0a
                && content[6] == 0x1a
                && content[7] == 0x0a) {
            return ".png";
        }
        if (content.length >= 3
                && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) {
            return ".jpg";
        }
        if (content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P') {
            return ".webp";
        }
        return null;
    }
}
