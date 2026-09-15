package org.example.communityapi.member.dto;

import org.example.communityapi.member.Member;
import org.example.communityapi.member.Role;

public record MemberMyProfileResponse (
        String email,
        String nickname,
        String phoneNumber,
        Role role
) {
    public static MemberMyProfileResponse from(Member member) {
        return new MemberMyProfileResponse(
                member.getEmail(),
                member.getNickname(),
                member.getPhoneNumber(),
                member.getRole()
        );
    }
}