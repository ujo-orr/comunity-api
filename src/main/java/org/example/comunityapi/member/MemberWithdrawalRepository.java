package org.example.comunityapi.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface MemberWithdrawalRepository extends JpaRepository<MemberWithdrawal, Long> {
    // 만료 일시(expireAt)가 현재 시간 이전인 데이터 일괄 삭제 (스케줄러용)
    void deleteByExpireAtBefore(LocalDateTime now);
}
