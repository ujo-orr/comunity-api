package org.example.communityapi.attachment.dto;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id,
        String originalFileName,
        String contentType,
        long fileSize,
        String downloadUrl,
        LocalDateTime createdAt
) {}
