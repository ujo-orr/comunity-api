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
            // 응답 유실 등으로 업로드 성공 여부가 불명확한 경우에도 정리를 시도한다.
            deleteQuietly(key);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        try {
            // 기존 최대 파일 크기(10MB) 내에서 버퍼링하여 응답 전에 S3 오류를 처리한다.
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
            // DB 커밋/원래 예외를 덮어쓰지 않도록 로컬 저장소와 같은 best-effort 정책 사용.
            log.error("Failed to delete S3 attachment: {}", storageKey, e);
        }
    }
}
