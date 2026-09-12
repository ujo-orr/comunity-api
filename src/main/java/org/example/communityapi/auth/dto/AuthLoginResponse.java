package org.example.communityapi.auth.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthLoginResponse {
    private String accessToken; // 로그인 성공 시 발급되는 JWT 토큰 (유예 상태일 때는 null)
    private String refreshToken;
    private String status;      // "SUCCESS" || "WITHDRAWAL_PENDING"

    // 정적 팩토리 메서드: 정상 로그인 성공 응답 생성용
    public static AuthLoginResponse success(String accessToken, String refreshToken) {
        return new AuthLoginResponse(accessToken, refreshToken,"SUCCESS");
    }

    // 정적 팩토리 메서드: 탈퇴 유예 상태 응답 생성용
    public static AuthLoginResponse withdrawalPending() {
        return new AuthLoginResponse(null, null, "WITHDRAWAL_PENDING");
    }
}
