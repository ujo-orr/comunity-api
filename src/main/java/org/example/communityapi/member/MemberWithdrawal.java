package org.example.communityapi.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.communityapi.global.entity.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberWithdrawal extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long originalId;
    private String email;
    private String password;
    private String nickname;
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private Role role;

    private LocalDateTime originalCreatedAt;

    private LocalDateTime expireAt;

    public MemberWithdrawal(Member member) {
        this.originalId = member.getId();
        this.email = member.getEmail();
        this.password = member.getPassword();
        this.nickname = member.getNickname();
        this.phoneNumber = member.getPhoneNumber();
        this.role = member.getRole();
        this.originalCreatedAt = member.getCreatedAt();

        // 30일 동안은 탈퇴를 취소할 수 있다.
        this.expireAt = LocalDateTime.now().plusDays(30);
    }

    public Member toMember() {
        return Member.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .phoneNumber(phoneNumber)
                .role(role)
                .build();
    }
}
