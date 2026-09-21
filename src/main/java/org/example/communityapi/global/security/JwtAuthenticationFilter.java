package org.example.communityapi.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.global.error.StorageExceptionClassifier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.example.communityapi.global.error.ErrorResponse;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.MemberStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    private boolean isBlacklisted(String token) {
        return "logout".equals(stringRedisTemplate.opsForValue().get(token));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                authenticate(token);
            } catch (JwtException e) {
                log.debug("유효하지 않은 JWT");
                SecurityContextHolder.clearContext();
            } catch (DataAccessException | TransactionException e) {
                ErrorCode code = StorageExceptionClassifier.classify(e);
                if (code == ErrorCode.STORAGE_SERVICE_UNAVAILABLE) {
                    code = ErrorCode.AUTHENTICATION_SERVICE_UNAVAILABLE;
                }
                log.error("JWT 인증 저장소 처리 실패: {}", code.getCode(), e);
                SecurityContextHolder.clearContext();
                writeError(response, code);
                return;
            } catch (RuntimeException e) {
                log.error("JWT 인증 처리 실패", e);
                SecurityContextHolder.clearContext();
                writeError(response, ErrorCode.INTERNAL_SERVER_ERROR);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        Claims claims;
        try {
            claims = jwtTokenProvider.getClaimsFromToken(token);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Invalid token", e);
        }
        String email = claims.getSubject();
        String role = claims.get("role", String.class);
        Long memberId = claims.get("memberId", Long.class);
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);
        if (!StringUtils.hasText(email) || !StringUtils.hasText(role) || memberId == null
                || tokenVersion == null || isBlacklisted(token)) {
            SecurityContextHolder.clearContext();
            return;
        }

        // 동일 이메일 재가입 시 기존 토큰 사용 방지를 위한 회원 정보 검증
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null || member.getStatus() != MemberStatus.ACTIVE
                || !memberId.equals(member.getId())
                || tokenVersion != member.getTokenVersion()
                || !member.getRole().name().equals(role)) {
            SecurityContextHolder.clearContext();
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(getAuthentication(role, email));
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(errorCode));
    }

    private static UsernamePasswordAuthenticationToken getAuthentication(String role, String email) {
        String authorityRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(authorityRole)
        );

        User principal = new User(email, "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
            return null;
        }

        String token = bearerToken.substring(7).trim();

        if (!StringUtils.hasText(token)
                || "null".equalsIgnoreCase(token)
                || "undefined".equalsIgnoreCase(token)) {
            return null;
        }

        return token;
    }
}
