package org.example.communityapi.member.dto;

import org.example.communityapi.member.Member;

public record MemberSearchResponse (String nickname) {
    public static MemberSearchResponse from(Member member) {
        return new MemberSearchResponse(member.getNickname());
    }
}
