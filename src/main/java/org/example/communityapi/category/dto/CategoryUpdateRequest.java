package org.example.communityapi.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank(message = "카테고리 이름을 입력해주세요.")
        @Size(min = 1, max = 10, message = "10글자 이하로 입력해주세요.")
        String name
) {}
