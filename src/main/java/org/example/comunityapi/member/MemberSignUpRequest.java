package org.example.comunityapi.member;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor

public class MemberSignUpRequest {
    private  String email;
    private  String password;
    private  String phoneNumber;
    private  String nickname;

    public MemberSignUpRequest(String email, String password, String phoneNumber, String nickname) {
        this.email = email;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.nickname = nickname;
    }

    // DTO 기반 Member 엔터티 생성
    public Member toEntity(String encodedPassword) {
        return Member.builder()
                .email(this.email)
                .password(encodedPassword)
                .nickname(this.nickname)
                .phoneNumber(this.phoneNumber)
                .role(Role.USER)
                .build();
    }
}
