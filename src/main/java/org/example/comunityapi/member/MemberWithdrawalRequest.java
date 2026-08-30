package org.example.comunityapi.member;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberWithdrawalRequest {
    private String password; // 탈퇴 확인용 비밀번호

    public MemberWithdrawalRequest(String password) {this.password = password;}
}
