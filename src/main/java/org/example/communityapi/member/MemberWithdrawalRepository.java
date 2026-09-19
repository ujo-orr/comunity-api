package org.example.communityapi.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberWithdrawalRepository extends JpaRepository<MemberWithdrawal, Long> {

    Optional<MemberWithdrawal> findByEmail(String email);
    boolean existsByEmail(String email);

    void deleteByExpireAtBefore(LocalDateTime now);
}
