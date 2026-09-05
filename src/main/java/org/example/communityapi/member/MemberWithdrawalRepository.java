package org.example.communityapi.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberWithdrawalRepository extends JpaRepository<MemberWithdrawal, Long> {

    // 이메일로 탈퇴 유예 기록 조회 (로그인 시 유예 상태 체크 및 복구 처리용)
    Optional<MemberWithdrawal> findByEmail(String email);

    // 만료 일시(expireAt)가 현재 시간 이전인 데이터 일괄 삭제 (스케줄러/배치 파기용)
    void deleteByExpireAtBefore(LocalDateTime now);
}
