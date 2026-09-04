package org.example.comunityapi.member;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberLoginResponse {
    private String accessToken; // 로그인 성공 시 발급되는 JWT 토큰 (유예 상태일 때는 null)
    private String status;      // "SUCCESS" or "WITHDRAWAL_PENDING"

    // 정적 팩토리 메서드: 정상 로그인 성공 응답 생성용
    public static MemberLoginResponse success(String accessToken) {
        return new MemberLoginResponse(accessToken, "SUCCESS");
    }

    // 정적 팩토리 메서드: 탈퇴 유예 상태 응답 생성용
    public static MemberLoginResponse withdrawalPending() {
        return new MemberLoginResponse(null, "WITHDRAWAL_PENDING");
    }
}
