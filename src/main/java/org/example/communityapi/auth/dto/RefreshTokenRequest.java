package org.example.communityapi.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh Token은 필수 입력 항목입니다.")
        String refreshToken
) {}
