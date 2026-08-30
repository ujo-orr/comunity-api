package org.example.comunityapi.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberWithdrawal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id;
    private String email;          // 탈퇴한 유저 이메일
    private LocalDateTime deletedAt; // 탈퇴 일시
    private LocalDateTime expireAt;  // 법적 파기 예정 일시 (예: 탈퇴일 + 3년 혹은 30일)

    public MemberWithdrawal(String email, int retentionDays) {
        this.email = email;
        this.deletedAt = LocalDateTime.now();
        this.expireAt = this.deletedAt.plusDays(retentionDays); // 유예/보관 기간 설정
    }
}