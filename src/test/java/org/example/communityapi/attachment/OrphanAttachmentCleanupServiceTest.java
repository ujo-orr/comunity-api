package org.example.communityapi.attachment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrphanAttachmentCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    private final S3Client s3 = mock(S3Client.class);
    private final PostAttachmentRepository repository = mock(PostAttachmentRepository.class);
    private OrphanAttachmentCleanupService service;

    @BeforeEach
    void setUp() {
        service = new OrphanAttachmentCleanupService(s3, repository, "test-bucket",
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.findExistingStorageKeys(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("데이터베이스에 등록된 첨부파일은 정리 대상에서 제외한다")
    void doesNotDeleteStorageKeyThatExistsInDatabase() {
        String key = "attachments/posts/1/kept";
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(object(key, NOW.minusSeconds(3601))));
        when(repository.findExistingStorageKeys(List.of(key))).thenReturn(List.of(key));

        var result = service.cleanup();

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        assertThat(result.deleted()).isZero();
    }

    @Test
    @DisplayName("데이터베이스에 없는 첨부파일은 저장 후 한 시간이 지나면 삭제한다")
    void deletesOldObjectMissingFromDatabase() {
        String key = "attachments/posts/1/orphan";
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(object(key, NOW.minusSeconds(3600))));

        var result = service.cleanup();

        verify(s3).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key(key).build());
        assertThat(result.deleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("저장한 지 한 시간이 지나지 않은 첨부파일은 삭제하지 않는다")
    void doesNotDeleteObjectWithinOneHourProtectionWindow() {
        String key = "attachments/posts/1/recent";
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(object(key, NOW.minusSeconds(3599))));

        var result = service.cleanup();

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(repository, never()).findExistingStorageKeys(any());
        assertThat(result.protectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("첨부파일 하나의 삭제에 실패해도 나머지는 계속 삭제하고 성공과 실패 건수를 집계한다")
    void continuesWhenOneDeletionFails() {
        String failedKey = "attachments/posts/1/fails";
        String nextKey = "attachments/posts/1/deletes";
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(
                object(failedKey, NOW.minusSeconds(3601)), object(nextKey, NOW.minusSeconds(3601))));
        doThrow(SdkClientException.create("network failure")).when(s3)
                .deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key(failedKey).build());

        var result = service.cleanup();

        verify(s3).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key(nextKey).build());
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("첨부파일 목록이 여러 페이지여도 모든 페이지의 미사용 파일을 삭제한다")
    void readsEveryS3Page() {
        String firstKey = "attachments/posts/1/first";
        String secondKey = "attachments/posts/2/second";
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(object(firstKey, NOW.minusSeconds(3601)))
                        .isTruncated(true).nextContinuationToken("next-page").build(),
                page(object(secondKey, NOW.minusSeconds(3601))));

        service.cleanup();

        verify(s3, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3).listObjectsV2(argThat((ListObjectsV2Request request) ->
                "next-page".equals(request.continuationToken())));
        verify(s3).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key(firstKey).build());
        verify(s3).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key(secondKey).build());
    }

    private static ListObjectsV2Response page(S3Object... objects) {
        return ListObjectsV2Response.builder().contents(objects).isTruncated(false).build();
    }

    private static S3Object object(String key, Instant lastModified) {
        return S3Object.builder().key(key).lastModified(lastModified).build();
    }
}
