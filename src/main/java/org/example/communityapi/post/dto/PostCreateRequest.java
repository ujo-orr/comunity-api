package org.example.communityapi.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @NotBlank(message = "제목은 필수 항목입니다.")
        @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
        String title,

        @NotBlank(message = "내용은 필수 항목입니다.")
        @Size(max = 10000, message = "내용은 10000자 이하로 입력해주세요.")
        String content,

        @NotNull(message = "카테고리는 필수 선택 항목입니다.")
        @Positive(message = "카테고리 번호는 1 이상이어야 합니다.")
        Long categoryId
) {}
