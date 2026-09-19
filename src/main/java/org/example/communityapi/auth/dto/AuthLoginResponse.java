package org.example.communityapi.auth.dto;

public record AuthLoginResponse(
        String accessToken,
        String refreshToken,
        String status
) {

    public static AuthLoginResponse success(String accessToken, String refreshToken) {
        return new AuthLoginResponse(accessToken, refreshToken, "SUCCESS");
    }

    public static AuthLoginResponse withdrawalPending() {
        return new AuthLoginResponse(null, null, "WITHDRAWAL_PENDING");
    }
}
