package org.example.communityapi.member.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.Role;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberMyProfileResponse {

    private final String email;
    private final String nickname;
    private final String phoneNumber;
    private final Role role;

    public static MemberMyProfileResponse from(Member member) {
        return new MemberMyProfileResponse(
                member.getEmail(),
                member.getNickname(),
                member.getPhoneNumber(),
                member.getRole()
        );
    }
}