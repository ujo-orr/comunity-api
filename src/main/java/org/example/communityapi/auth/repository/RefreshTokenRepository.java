package org.example.communityapi.auth.repository;

import jakarta.persistence.LockModeType;
import org.example.communityapi.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByEmail(String email);

    // 같은 토큰으로 요청이 겹치면 하나씩 처리한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByToken(String token);
    void deleteByEmail(String email);
}
