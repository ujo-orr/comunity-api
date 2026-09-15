package org.example.communityapi.auth.dto;

public record AuthLoginResponse(
        String accessToken,
        String refreshToken,
        String status
) {

    // 정적 팩토리 메서드: 정상 로그인 성공 응답 생성용
    public static AuthLoginResponse success(String accessToken, String refreshToken) {
        return new AuthLoginResponse(accessToken, refreshToken, "SUCCESS");
    }

    // 정적 팩토리 메서드: 탈퇴 유예 상태 응답 생성용
    public static AuthLoginResponse withdrawalPending() {
        return new AuthLoginResponse(null, null, "WITHDRAWAL_PENDING");
    }
}
