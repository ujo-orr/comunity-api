package org.example.communityapi.attachment;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.attachment.dto.AttachmentResponse;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.post.Post;
import org.example.communityapi.post.PostRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostAttachmentService {

    private final PostAttachmentRepository attachmentRepository;
    private final PostRepository postRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public List<AttachmentResponse> upload(Long postId, List<MultipartFile> files, String email) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        Post post = postRepository.findByIdWithMember(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        post.validateWriter(email);

        List<String> storageKeys = new ArrayList<>();
        try {
            List<PostAttachment> attachments = files.stream().map(file -> {
                String storageKey = fileStorageService.store(postId, file);
                storageKeys.add(storageKey);
                return PostAttachment.builder()
                        .post(post)
                        .originalFileName(file.getOriginalFilename())
                        .storageKey(storageKey)
                        .contentType(file.getContentType())
                        .fileSize(file.getSize())
                        .build();
            }).toList();
            return attachmentRepository.saveAll(attachments).stream()
                    .map(attachment -> toResponse(postId, attachment))
                    .toList();
        } catch (RuntimeException e) {
            storageKeys.forEach(fileStorageService::deleteQuietly);
            throw e;
        }
    }

    public List<AttachmentResponse> getAttachments(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return attachmentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(attachment -> toResponse(postId, attachment))
                .toList();
    }

    public DownloadedAttachment download(Long postId, Long attachmentId) {
        PostAttachment attachment = attachmentRepository.findByIdAndPostId(attachmentId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
        return new DownloadedAttachment(attachment, fileStorageService.load(attachment.getStorageKey()));
    }

    @Transactional
    public void delete(Long postId, Long attachmentId, String email) {
        PostAttachment attachment = attachmentRepository.findByIdAndPostId(attachmentId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
        attachment.getPost().validateWriter(email);
        attachmentRepository.delete(attachment);
        deleteFileAfterCommit(attachment.getStorageKey());
    }

    private AttachmentResponse toResponse(Long postId, PostAttachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getOriginalFileName(),
                attachment.getContentType(), attachment.getFileSize(),
                "/api/posts/" + postId + "/attachments/" + attachment.getId() + "/download",
                attachment.getCreatedAt());
    }

    private void deleteFileAfterCommit(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.deleteQuietly(storageKey);
            }
        });
    }

    public record DownloadedAttachment(PostAttachment attachment, Resource resource) {}
}
