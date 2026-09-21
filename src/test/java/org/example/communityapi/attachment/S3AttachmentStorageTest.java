package org.example.communityapi.attachment;

import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class S3AttachmentStorageTest {
    private final S3Client s3 = mock(S3Client.class);
    private final AttachmentStorage storage = new S3AttachmentStorage(s3, "test-bucket");

    @Test
    @DisplayName("첨부파일은 UUID 저장 키로 원본 바이트를 유지하며 공개 ACL 없이 저장된다")
    void storesUuidKeyAndOriginalBytesWithoutPublicAcl() throws Exception {
        var file = new MockMultipartFile("files", "test.html", "text/html", new byte[]{1, 2, 3});
        String key = storage.store(7L, file);
        assertThat(key).matches("attachments/posts/7/[0-9a-f-]{36}");
        var request = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = org.mockito.ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("application/octet-stream");
        assertThat(request.getValue().acl()).isNull();
        try (var input = body.getValue().contentStreamProvider().newStream()) {
            assertThat(input.readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    @ParameterizedTest
    @DisplayName("경로나 제어 문자가 포함된 파일명은 S3에 저장하지 않고 거부한다")
    @ValueSource(strings = {"../secret.txt", "folder/file.txt", "folder\\file.txt", "file\r\nheader.txt", ".."})
    void rejectsUnsafeNamesBeforeCallingS3(String filename) {
        assertThatThrownBy(() -> storage.store(1L,
                new MockMultipartFile("files", filename, "text/plain", new byte[]{1})))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE));
        verifyNoInteractions(s3);
    }

    @Test
    @DisplayName("빈 파일이나 10MB를 초과한 파일은 저장하지 않고 거부한다")
    void rejectsEmptyAndOversizedFiles() {
        for (int size : new int[]{0, 10 * 1024 * 1024 + 1}) {
            assertThatThrownBy(() -> storage.store(1L,
                    new MockMultipartFile("files", "file.txt", "text/plain", new byte[size])))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE));
        }
        verifyNoInteractions(s3);
    }

    @Test
    @DisplayName("지정한 버킷과 저장 키의 첨부파일을 조회하면 원본 바이트를 반환한다")
    void loadsBytesUsingBucketAndKey() throws Exception {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{4, 5}));
        assertThat(storage.load("attachments/posts/1/key").getContentAsByteArray()).containsExactly(4, 5);
        verify(s3).getObjectAsBytes(GetObjectRequest.builder().bucket("test-bucket")
                .key("attachments/posts/1/key").build());
    }

    @ParameterizedTest
    @DisplayName("S3 조회 시 404는 첨부파일 없음 오류로 반환하고 403과 500은 파일 저장소 오류로 반환한다")
    @ValueSource(ints = {403, 404, 500})
    void mapsS3ErrorsToExistingApiErrors(int status) {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(status).message("internal AWS detail").build());
        assertThatThrownBy(() -> storage.load("key"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(status == 404 ? ErrorCode.ATTACHMENT_NOT_FOUND : ErrorCode.FILE_STORAGE_ERROR));
    }

    @Test
    @DisplayName("업로드에 실패하면 잔여 파일 삭제를 시도하고 삭제도 실패해도 파일 저장소 오류를 유지한다")
    void failedUploadAttemptsCleanupAndPreservesStorageError() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("network failure"));
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("cleanup failure"));
        assertThatThrownBy(() -> storage.store(1L,
                new MockMultipartFile("files", "file.txt", "text/plain", new byte[]{1})))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_STORAGE_ERROR));
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("첨부파일 삭제 시 지정한 버킷과 저장 키의 파일을 삭제한다")
    void deletionUsesBucketAndKey() {
        storage.deleteQuietly("key");
        verify(s3).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key("key").build());
    }
}
