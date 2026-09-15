package org.example.communityapi.comment.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        String content,
        String writerName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
