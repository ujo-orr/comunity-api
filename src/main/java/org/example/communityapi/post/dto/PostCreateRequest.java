package org.example.communityapi.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostCreateRequest(
        @NotBlank(message = "제목은 필수 항목입니다.")
        String title,

        @NotBlank(message = "내용은 필수 항목입니다.")
        String content,

        @NotNull(message = "카테고리는 필수 선택 항목입니다.")
        Long categoryId
) {}
