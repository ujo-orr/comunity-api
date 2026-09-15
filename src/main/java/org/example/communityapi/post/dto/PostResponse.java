package org.example.communityapi.post.dto;

import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        String title,
        String content,
        String categoryName,
        String writerName,
        int viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}