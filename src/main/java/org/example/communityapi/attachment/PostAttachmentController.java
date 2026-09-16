package org.example.communityapi.attachment;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.attachment.dto.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/posts/{postId}/attachments")
@RequiredArgsConstructor
public class PostAttachmentController {

    private final PostAttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AttachmentResponse>> upload(
            @PathVariable Long postId,
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) {
        return ResponseEntity.ok(attachmentService.upload(postId, files, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<AttachmentResponse>> getAttachments(@PathVariable Long postId) {
        return ResponseEntity.ok(attachmentService.getAttachments(postId));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long postId, @PathVariable Long attachmentId) {
        PostAttachmentService.DownloadedAttachment downloaded = attachmentService.download(postId, attachmentId);
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (downloaded.attachment().getContentType() != null) {
            try {
                contentType = MediaType.parseMediaType(downloaded.attachment().getContentType());
            } catch (IllegalArgumentException ignored) {
                // 클라이언트가 보낸 MIME 타입은 신뢰하지 않고 안전한 기본값으로 내려준다.
            }
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(downloaded.attachment().getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(downloaded.attachment().getOriginalFileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(downloaded.resource());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable Long postId, @PathVariable Long attachmentId,
                                       Authentication authentication) {
        attachmentService.delete(postId, attachmentId, authentication.getName());
        return ResponseEntity.ok().build();
    }
}
