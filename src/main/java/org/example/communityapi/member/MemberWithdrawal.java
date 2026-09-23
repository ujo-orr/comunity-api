package org.example.communityapi.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.communityapi.global.entity.BaseTimeEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_withdrawal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberWithdrawal extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long originalId;

    // 기존 이메일 보관을 위해 탈퇴 이력에는 회원가입 길이 제한(30자) 미적용
    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    private LocalDateTime originalCreatedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime expireAt;

    public MemberWithdrawal(Member member) {
        this.originalId = member.getId();
        this.email = member.getEmail();
        this.password = member.getPassword();
        this.nickname = member.getNickname();
        this.phoneNumber = member.getPhoneNumber();
        this.role = member.getRole();
        this.originalCreatedAt = member.getCreatedAt();

        // 30일간의 탈퇴 취소 유예 기간 설정
        this.deletedAt = LocalDateTime.now();
        this.expireAt = deletedAt.plusDays(30);
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
