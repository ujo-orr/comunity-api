package org.example.communityapi.attachment;

import lombok.extern.slf4j.Slf4j;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final Path uploadDirectory;

    public FileStorageService(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        try {
            this.uploadDirectory = Path.of(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    public String store(Long postId, MultipartFile file) {
        validate(file);
        String storageKey = "attachments/posts/" + postId + "/" + UUID.randomUUID();
        Path target = resolve(storageKey);
        try (var inputStream = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target);
            return storageKey;
        } catch (IOException e) {
            deleteQuietly(storageKey);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            // DB 트랜잭션은 이미 완료된 뒤일 수 있으므로, 재시도 가능한 로그를 남긴다.
            log.error("Failed to delete attachment file: {}", storageKey, e);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())
                || file.getOriginalFilename().length() > 255
                || (file.getContentType() != null && file.getContentType().length() > 100)
                || file.getSize() > 10L * 1024 * 1024) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
    }

    private Path resolve(String storageKey) {
        Path path = uploadDirectory.resolve(storageKey).normalize();
        if (!path.startsWith(uploadDirectory)) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        return path;
    }
}
