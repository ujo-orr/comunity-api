package org.example.communityapi.auth;

import org.example.communityapi.auth.repository.RefreshTokenRepository;
import org.example.communityapi.global.security.JwtTokenProvider;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.MemberWithdrawalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void logoutSkipsBlacklistWhenAccessTokenExpiresDuringLogout() {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AuthService authService = new AuthService(
                jwtTokenProvider,
                refreshTokenRepository,
                mock(MemberRepository.class),
                mock(MemberWithdrawalRepository.class),
                mock(PasswordEncoder.class),
                redisTemplate
        );
        String accessToken = "access-token";
        when(jwtTokenProvider.validateToken(accessToken)).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken(accessToken)).thenReturn("member@example.com");
        when(jwtTokenProvider.getExpiration(accessToken)).thenReturn(0L);

        authService.logout(accessToken);

        verify(refreshTokenRepository).deleteByEmail("member@example.com");
        verify(redisTemplate, never()).opsForValue();
    }
}
