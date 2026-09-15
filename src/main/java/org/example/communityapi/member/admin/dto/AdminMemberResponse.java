package org.example.communityapi.member.admin.dto;

import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberStatus;
import org.example.communityapi.member.Role;

import java.time.LocalDateTime;

public record AdminMemberResponse(
        Long id,
        String email,
        String nickname,
        String phoneNumber,
        Role role,
        MemberStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
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