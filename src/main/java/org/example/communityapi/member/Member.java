package org.example.communityapi.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)

public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Role role;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Member(String email, String password, String phoneNumber, String nickname, Role role) {
        this.email = email;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.nickname = nickname;
        this.role = role != null ? role : Role.USER;
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
}