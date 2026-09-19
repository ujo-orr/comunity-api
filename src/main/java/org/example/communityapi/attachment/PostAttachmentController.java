package org.example.communityapi.attachment;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.communityapi.attachment.dto.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/posts/{postId}/attachments")
@RequiredArgsConstructor
public class PostAttachmentController {

    private final PostAttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AttachmentResponse>> upload(
            @Positive @PathVariable Long postId,
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) {
        return ResponseEntity.ok(attachmentService.upload(postId, files, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<AttachmentResponse>> getAttachments(@Positive @PathVariable Long postId) {
        return ResponseEntity.ok(attachmentService.getAttachments(postId));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@Positive @PathVariable Long postId, @Positive @PathVariable Long attachmentId) {
        PostAttachmentService.DownloadedAttachment downloaded = attachmentService.download(postId, attachmentId);
        // 올린 파일이 브라우저에서 실행되지 않도록 다운로드 파일로 내려준다.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(downloaded.attachment().getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(downloaded.attachment().getOriginalFileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(downloaded.resource());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(@Positive @PathVariable Long postId, @Positive @PathVariable Long attachmentId,
                                       Authentication authentication) {
        attachmentService.delete(postId, attachmentId, authentication.getName());
        return ResponseEntity.ok().build();
    }
}
