package org.example.communityapi.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.communityapi.global.entity.BaseTimeEntity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "MEMBERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)

public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Member(Long id, String email, String password, String phoneNumber, String nickname, Role role, MemberStatus status) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.nickname = nickname;
        this.role = role != null ? role : Role.USER;
        this.status = status != null ? status : MemberStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 회원 정보 수정 비즈니스 메서드
    public void updateProfile(String nickname, String phoneNumber, String newPassword) {
        if (org.springframework.util.StringUtils.hasText(nickname)) {
            this.nickname = nickname;
        }
        if (org.springframework.util.StringUtils.hasText(phoneNumber)) {
            this.phoneNumber = phoneNumber;
        }
        if (org.springframework.util.StringUtils.hasText(newPassword)) {
            this.password = newPassword;
        }
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public void ban() {
        this.status = MemberStatus.BANNED;
    }

    public void unban() {
        this.status = MemberStatus.ACTIVE;
    }
}