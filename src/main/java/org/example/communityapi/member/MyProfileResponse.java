package org.example.communityapi.member;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MyProfileResponse {

    private final String email;
    private final String nickname;
    private final String phoneNumber;
    private final Role role;

    public static MyProfileResponse from(Member member) {
        return new MyProfileResponse(
                member.getEmail(),
                member.getNickname(),
                member.getPhoneNumber(),
                member.getRole()
        );
    }
}