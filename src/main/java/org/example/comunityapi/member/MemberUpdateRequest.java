package org.example.comunityapi.member;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberUpdateRequest {
    private String nickname;
    private String phoneNumber;
    private String password;

    public MemberUpdateRequest(String nickname, String phoneNumber, String password) {
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.password = password;
    }
}
