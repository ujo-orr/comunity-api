package org.example.communityapi.member;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberResponse {
    private final String nickname;

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getNickname());
    }
}
