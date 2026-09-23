package org.example.communityapi.attachment;

import lombok.extern.slf4j.Slf4j;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "file.storage-type", havingValue = "s3")
public class S3AttachmentStorage implements AttachmentStorage {
    private final S3Client s3;
    private final String bucket;

    public S3AttachmentStorage(S3Client s3, @Value("${file.s3.bucket}") String bucket) {
        Assert.hasText(bucket, "S3_BUCKET must not be blank");
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String store(Long postId, MultipartFile file) {
        AttachmentFileValidator.validate(file);
        String key = "attachments/posts/" + postId + "/" + UUID.randomUUID();
        try (var input = file.getInputStream()) {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                            .contentType("application/octet-stream").build(),
                    RequestBody.fromInputStream(input, file.getSize()));
            return key;
        } catch (IOException | SdkException e) {
            // 업로드 오류 시 S3에 파일이 남아 있을 수 있어 삭제 시도
            deleteQuietly(key);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        try {
            // 응답 전에 S3 오류를 확인하도록 파일 전체 읽기(최대 10MB)
            return new ByteArrayResource(s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(storageKey).build()).asByteArray());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND, e);
            }
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, e);
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, e);
        }
    }

    @Override
    public void deleteQuietly(String storageKey) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
        } catch (SdkException e) {
            // 기존 처리에 영향을 주지 않도록 파일 삭제 실패는 로그만 기록
            log.error("Failed to delete S3 attachment: {}", storageKey, e);
        }
    }
}
