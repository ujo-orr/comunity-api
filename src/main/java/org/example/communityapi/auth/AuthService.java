package org.example.communityapi.auth;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.auth.dto.MemberLoginRequest;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.auth.dto.AuthLoginResponse;
import org.example.communityapi.auth.entity.RefreshToken;
import org.example.communityapi.auth.repository.RefreshTokenRepository;
import org.example.communityapi.auth.dto.TokenResponse;
import org.example.communityapi.global.security.JwtTokenProvider;
import org.example.communityapi.member.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;
    private final MemberWithdrawalRepository memberWithdrawalRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    private TokenResponse createSaveTokens(Member member) {
        String email = member.getEmail();
        String accessToken = jwtTokenProvider.createAccessToken(email, member.getRole(), member.getId(), member.getTokenVersion());
        String refreshToken = jwtTokenProvider.createRefreshToken(email);

        refreshTokenRepository.findByEmail(email)
                .ifPresentOrElse(
                        token -> token.updateToken(refreshToken),
                        () -> refreshTokenRepository.save(new RefreshToken(email, refreshToken))
                );

        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponse reissue(String requestRefreshToken) {
        if (!jwtTokenProvider.validateToken(requestRefreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        String email = refreshToken.getEmail();

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole(), member.getId(), member.getTokenVersion());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member.getEmail());

        refreshToken.updateToken(newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public AuthLoginResponse login(MemberLoginRequest request) {
        Optional<Member> memberOpt = memberRepository.findByEmail(request.email());

        if (memberOpt.isPresent()) {
            Member member = memberOpt.get();

            if (!passwordEncoder.matches(request.password(), member.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }

            if (member.getStatus() == MemberStatus.BANNED) {
                throw new BusinessException(ErrorCode.BANNED_USER);
            }

            TokenResponse tokenResponse = createSaveTokens(member);

            return AuthLoginResponse.success(
                    tokenResponse.accessToken(),
                    tokenResponse.refreshToken()
            );
        }

        Optional<MemberWithdrawal> withdrawalOpt = memberWithdrawalRepository.findByEmail(request.email());

        if (withdrawalOpt.isPresent()) {
            MemberWithdrawal withdrawal = withdrawalOpt.get();

            if (!passwordEncoder.matches(request.password(), withdrawal.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }

            return AuthLoginResponse.withdrawalPending();
        }

        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    @Transactional
    public void logout(String accessToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String email = jwtTokenProvider.getEmailFromToken(accessToken);

        refreshTokenRepository.deleteByEmail(email);

        long expiration = jwtTokenProvider.getExpiration(accessToken);
        // 이미 만료된 토큰은 블랙리스트 저장 생략
        if (expiration > 0) {
            stringRedisTemplate.opsForValue().set(
                    accessToken,
                    "logout",
                    expiration,
                    TimeUnit.MILLISECONDS
            );
        }
    }
}
