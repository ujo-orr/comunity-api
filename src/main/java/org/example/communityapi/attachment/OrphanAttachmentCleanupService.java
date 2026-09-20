package org.example.communityapi.attachment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

// S3 업로드 후 DB 트랜잭션이 실패했을 때 남는 오래된 객체를 정리한다.
@Slf4j
@Service
@Profile("prod")
public class OrphanAttachmentCleanupService {

    private static final String ATTACHMENT_PREFIX = "attachments/posts/";
    private static final Duration PROTECTION_WINDOW = Duration.ofHours(1);

    private final S3Client s3;
    private final PostAttachmentRepository attachmentRepository;
    private final String bucket;
    private final Clock clock;

    @Autowired
    public OrphanAttachmentCleanupService(S3Client s3,
                                          PostAttachmentRepository attachmentRepository,
                                          @Value("${file.s3.bucket}") String bucket) {
        this(s3, attachmentRepository, bucket, Clock.systemUTC());
    }

    OrphanAttachmentCleanupService(S3Client s3,
                                   PostAttachmentRepository attachmentRepository,
                                   String bucket,
                                   Clock clock) {
        Assert.hasText(bucket, "S3_BUCKET must not be blank");
        this.s3 = s3;
        this.attachmentRepository = attachmentRepository;
        this.bucket = bucket;
        this.clock = clock;
    }

    public CleanupResult cleanup() {
        Instant cutoff = clock.instant().minus(PROTECTION_WINDOW);
        String continuationToken = null;
        CleanupResult result = new CleanupResult(0, 0, 0, 0);

        do {
            var response = s3.listObjectsV2(listRequest(continuationToken));
            result = result.add(cleanupPage(response.contents(), cutoff));
            continuationToken = Boolean.TRUE.equals(response.isTruncated())
                    ? response.nextContinuationToken() : null;
        } while (continuationToken != null);

        if (result.failed() > 0) {
            log.warn("Orphan attachment cleanup completed with deletion failures: {}", result);
        } else {
            log.info("Orphan attachment cleanup completed: {}", result);
        }
        return result;
    }

    private ListObjectsV2Request listRequest(String continuationToken) {
        var request = ListObjectsV2Request.builder().bucket(bucket).prefix(ATTACHMENT_PREFIX);
        if (continuationToken != null) {
            request.continuationToken(continuationToken);
        }
        return request.build();
    }

    private CleanupResult cleanupPage(List<S3Object> objects, Instant cutoff) {
        List<S3Object> oldObjects = objects.stream()
                .filter(object -> object.lastModified() != null && !object.lastModified().isAfter(cutoff))
                .toList();
        int protectedCount = objects.size() - oldObjects.size();
        if (oldObjects.isEmpty()) {
            return new CleanupResult(objects.size(), protectedCount, 0, 0);
        }

        Set<String> existingKeys = Set.copyOf(attachmentRepository.findExistingStorageKeys(
                oldObjects.stream().map(S3Object::key).toList()));
        return new CleanupResult(objects.size(), protectedCount, 0, 0)
                .add(deleteMissingObjects(oldObjects, existingKeys));
    }

    private CleanupResult deleteMissingObjects(List<S3Object> oldObjects, Set<String> existingKeys) {
        int deleted = 0;
        int failed = 0;
        for (S3Object object : oldObjects) {
            if (existingKeys.contains(object.key())) {
                continue;
            }
            try {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(object.key()).build());
                deleted++;
            } catch (SdkException e) {
                failed++;
                log.error("Failed to delete orphan S3 attachment: {}", object.key(), e);
            }
        }
        return new CleanupResult(0, 0, deleted, failed);
    }

    public record CleanupResult(int scanned, int protectedCount, int deleted, int failed) {
        CleanupResult add(CleanupResult other) {
            return new CleanupResult(scanned + other.scanned, protectedCount + other.protectedCount,
                    deleted + other.deleted, failed + other.failed);
        }
    }
}
