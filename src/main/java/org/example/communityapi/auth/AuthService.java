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

    // 로그인 시 AccessToken + RefreshToken 발급 후 DB 저장/갱신
    private TokenResponse createSaveTokens(String email, Role role) {
        String accessToken = jwtTokenProvider.createAccessToken(email, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(email);

        // 기존 RefreshToken이 존재하면 덮어쓰고, 없으면 새로 저장
        refreshTokenRepository.findByEmail(email)
                .ifPresentOrElse(
                        token -> token.updateToken(refreshToken),
                        () -> refreshTokenRepository.save(new RefreshToken(email, refreshToken))
                );

        return new TokenResponse(accessToken, refreshToken);
    }

    // 토큰 재발급 (Reissue)
    @Transactional
    public TokenResponse reissue(String requestRefreshToken) {
        // Refresh Token 검증
        if (!jwtTokenProvider.validateToken(requestRefreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // DB에 저장된 토큰인지 확인
        RefreshToken refreshToken = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        String email = refreshToken.getEmail();

        // 💡 토큰 생성을 위한 회원 정보 DB 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Access Token & Refresh Token 모두 재발급 (RTR 방식: Refresh Token Rotate 적용)
        String newAccessToken = jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member.getEmail());

        refreshToken.updateToken(newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    // 로그인
    @Transactional
    public AuthLoginResponse login(MemberLoginRequest request) {
        Optional<Member> memberOpt = memberRepository.findByEmail(request.getEmail());

        if (memberOpt.isPresent()) {
            Member member = memberOpt.get();

            if (member.getStatus() == MemberStatus.BANNED) {
                throw new BusinessException(ErrorCode.BANNED_USER);
            }

            if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }

            TokenResponse tokenResponse = createSaveTokens(member.getEmail(), member.getRole());

            return AuthLoginResponse.success(
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken()
            );
        }

        // 탈퇴 유예 계정 확인
        Optional<MemberWithdrawal> withdrawalOpt = memberWithdrawalRepository.findByEmail(request.getEmail());

        if (withdrawalOpt.isPresent()) {
            MemberWithdrawal withdrawal = withdrawalOpt.get();

            if (!passwordEncoder.matches(request.getPassword(), withdrawal.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }

            return AuthLoginResponse.withdrawalPending();
        }

        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }

    // 로그아웃
    @Transactional
    public void logout(String accessToken) {
        // Access Token 검증
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Access Token에서 사용자 이메일 추출
        String email = jwtTokenProvider.getEmailFromToken(accessToken);

        // DB/Redis에서 해당 회원의 Refresh Token 삭제
        refreshTokenRepository.deleteByEmail(email);

        // Access Token 남은 유효시간(ms) 계산
        long expiration = jwtTokenProvider.getExpiration(accessToken);

        // Redis에 Access Token을 블랙리스트로 등록
        stringRedisTemplate.opsForValue().set(
                accessToken,
                "logout",
                expiration,
                TimeUnit.MILLISECONDS
        );
    }
}
