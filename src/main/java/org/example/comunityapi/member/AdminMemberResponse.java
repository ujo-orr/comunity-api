package org.example.comunityapi.member;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class AdminMemberResponse {
    private final Long id;
    private final String email;
    private final String nickname;
    private final String phoneNumber;
    private final Role role;
    private final LocalDateTime createdAt;

    public AdminMemberResponse(Member member) {
        this.id = member.getId();
        this.email = member.getEmail();
        this.nickname = member.getNickname();
        this.phoneNumber = member.getPhoneNumber();
        this.role = member.getRole();
        this.createdAt = member.getCreatedAt();
    }
}