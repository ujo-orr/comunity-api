package org.example.communityapi.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.communityapi.global.entity.BaseTimeEntity;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private int tokenVersion;

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

    @Builder
    public Member(Long id, String email, String password, String phoneNumber, String nickname, Role role, MemberStatus status) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.nickname = nickname;
        this.role = role != null ? role : Role.USER;
        this.status = status != null ? status : MemberStatus.ACTIVE;
    }

    public void updateProfile(String nickname, String phoneNumber, String newPassword) {
        if (org.springframework.util.StringUtils.hasText(nickname)) {
            this.nickname = nickname;
        }
        if (org.springframework.util.StringUtils.hasText(phoneNumber)) {
            this.phoneNumber = phoneNumber;
        }
        if (org.springframework.util.StringUtils.hasText(newPassword)) {
            if (!newPassword.equals(this.password)) {
                this.tokenVersion++;
            }
            this.password = newPassword;
        }
    }

    public void changeRole(Role newRole) {
        if (this.role != newRole) {
            this.tokenVersion++;
        }
        this.role = newRole;
    }

    public void ban() {
        // 정지 해제 후 기존 토큰 재사용 방지를 위한 토큰 버전 증가
        if (this.status != MemberStatus.BANNED) {
            this.tokenVersion++;
        }
        this.status = MemberStatus.BANNED;
    }

    public void unban() {
        this.status = MemberStatus.ACTIVE;
    }
}
