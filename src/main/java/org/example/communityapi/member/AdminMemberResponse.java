package org.example.communityapi.member;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AdminMemberResponse {
    private final Long id;
    private final String email;
    private final String nickname;
    private final String phoneNumber;
    private final Role role;
    private final MemberStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static AdminMemberResponse from(Member member) {
        return new AdminMemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getPhoneNumber(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}