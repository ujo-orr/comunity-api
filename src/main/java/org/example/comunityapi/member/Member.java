package org.example.comunityapi.member;

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

    @Column(nullable = false, unique = true, length = 20)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 11)
    private String phoneNumber;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
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
    public void updateProfile(String nickname, String password, String phoneNumber) {
        this.nickname = nickname;
        this.password = password;
        this.phoneNumber = phoneNumber;
    }
}