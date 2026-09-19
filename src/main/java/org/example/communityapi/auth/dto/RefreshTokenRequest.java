package org.example.communityapi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh Token은 필수 입력 항목입니다.")
        @Size(max = 512, message = "올바른 토큰을 입력해주세요.")
        String refreshToken
) {}
