package org.example.comunityapi.member;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class MemberResponse {
    private final Long id;
    private final String email;
    private final String nickname;
    private final String phoneNumber;
    private final LocalDateTime createdAt;

    // Member Entity를 받아 DTO로 변환하는 생성자
    public MemberResponse(Member member) {
        this.id = member.getId();
        this.email = member.getEmail();
        this.nickname = member.getNickname();
        this.phoneNumber = member.getPhoneNumber();
        this.createdAt = member.getCreatedAt();
    }
}
