package org.example.communityapi.member;

import lombok.Getter;

@Getter
public class MemberResponse {
    private final Long id;
    private final String nickname;

    public MemberResponse(Member member) {
        this.id = member.getId();
        this.nickname = member.getNickname();
    }
}
