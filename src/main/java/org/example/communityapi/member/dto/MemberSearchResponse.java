package org.example.communityapi.member.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.communityapi.member.Member;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberSearchResponse {
    private final String nickname;

    public static MemberSearchResponse from(Member member) {
        return new MemberSearchResponse(member.getNickname());
    }
}
