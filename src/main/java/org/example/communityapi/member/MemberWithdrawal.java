package org.example.communityapi.member;

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

    // Member 원복용 백업 데이터
    private String email;
    private String password;
    private String nickname;
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private Role role;

    private LocalDateTime originalCreatedAt; // 최초 가입일 복원용

    // 탈퇴 유예 관리 & 자동 파기용 데이터
    private LocalDateTime deletedAt; // 탈퇴 요청 일시
    private LocalDateTime expireAt;  // 데이터 영구 삭제(파기) 예정 일시

    public MemberWithdrawal(Member member) {
        this.email = member.getEmail();
        this.password = member.getPassword();
        this.nickname = member.getNickname();
        this.phoneNumber = member.getPhoneNumber();
        this.role = member.getRole();
        this.originalCreatedAt = member.getCreatedAt();

        // 시간 기록
        this.deletedAt = LocalDateTime.now();
        this.expireAt = this.deletedAt.plusDays(30); // 삭제 유예 만료일
    }
}