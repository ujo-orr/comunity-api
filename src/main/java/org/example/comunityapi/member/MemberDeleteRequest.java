package org.example.comunityapi.member;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberDeleteRequest {
    private String password; // 탈퇴 확인용 비밀번호

    public MemberDeleteRequest(String password) {
        this.password = password;
    }
}
