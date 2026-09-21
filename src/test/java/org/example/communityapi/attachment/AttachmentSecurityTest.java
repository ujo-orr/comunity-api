package org.example.communityapi.attachment;

import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.post.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AttachmentSecurityTest {
    @TempDir Path uploadDirectory;

    @ParameterizedTest
    @DisplayName("경로나 제어 문자가 포함된 파일명은 유효하지 않은 파일로 거부한다")
    @ValueSource(strings = {"../secret.txt", "folder/file.txt", "folder\\file.txt", "file\r\nheader.txt", ".."})
    void rejectsPathsAndControlCharactersInFilename(String filename) {
        FileStorageService storage = new FileStorageService(uploadDirectory.toString());
        var file = new MockMultipartFile("files", filename, "text/plain", new byte[]{1});

        assertThatThrownBy(() -> storage.store(1L, file))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE));
    }

    @Test
    @DisplayName("작은따옴표가 포함된 일반 파일명도 원본 내용 그대로 저장할 수 있다")
    void ordinaryFilenameWithApostropheCanBeStored() throws Exception {
        FileStorageService storage = new FileStorageService(uploadDirectory.toString());
        var file = new MockMultipartFile("files", "admin'--.txt", "text/plain", new byte[]{1, 2, 3});

        String key = storage.store(1L, file);

        assertThat(storage.load(key).getContentAsByteArray()).containsExactly(1, 2, 3);
        assertThat(key).startsWith("attachments/posts/1/").doesNotContain("admin");
    }

    @Test
    @DisplayName("첨부파일 다운로드는 업로더가 지정한 콘텐츠 타입 대신 바이너리 첨부파일로 응답한다")
    void downloadDoesNotUseUploaderContentType() {
        PostAttachmentService service = mock(PostAttachmentService.class);
        PostAttachmentController controller = new PostAttachmentController(service);
        var attachment = PostAttachment.builder().originalFileName("page.html")
                .contentType("text/html").fileSize(1).build();
        when(service.download(1L, 2L)).thenReturn(new PostAttachmentService.DownloadedAttachment(
                attachment, new ByteArrayResource(new byte[]{1})));

        var response = controller.download(1L, 2L);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("page.html");
    }

    @Test
    @DisplayName("첨부파일을 10개 넘게 업로드하면 파일을 저장하지 않고 거부한다")
    void tooManyFilesAreRejectedBeforeWritingAnything() {
        var attachments = mock(PostAttachmentRepository.class);
        var posts = mock(PostRepository.class);
        var storage = mock(FileStorageService.class);
        var service = new PostAttachmentService(attachments, posts, storage);
        var file = new MockMultipartFile("files", "file.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> service.upload(1L, Collections.nCopies(11, file), "writer@test.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE));
        verifyNoInteractions(attachments, posts, storage);
    }
}
