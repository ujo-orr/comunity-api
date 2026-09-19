package org.example.communityapi.attachment;

import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
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
    @ValueSource(strings = {"../secret.txt", "folder/file.txt", "folder\\file.txt", "file\r\nheader.txt", ".."})
    void rejectsUnsafeNamesBeforeCallingS3(String filename) {
        assertThatThrownBy(() -> storage.store(1L,
                new MockMultipartFile("files", filename, "text/plain", new byte[]{1})))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE));
        verifyNoInteractions(s3);
    }

    @Test
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
    void loadsBytesUsingBucketAndKey() throws Exception {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{4, 5}));
        assertThat(storage.load("attachments/posts/1/key").getContentAsByteArray()).containsExactly(4, 5);
        verify(s3).getObjectAsBytes(GetObjectRequest.builder().bucket("test-bucket")
                .key("attachments/posts/1/key").build());
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 500})
    void mapsS3ErrorsToExistingApiErrors(int status) {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(status).message("internal AWS detail").build());
        assertThatThrownBy(() -> storage.load("key"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(status == 404 ? ErrorCode.ATTACHMENT_NOT_FOUND : ErrorCode.FILE_STORAGE_ERROR));
    }

    @Test
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
    void deletionUsesBucketAndKey() {
        storage.deleteQuietly("key");
        verify(s3).deleteObject(DeleteObjectRequest.builder().bucket("test-bucket").key("key").build());
    }
}
