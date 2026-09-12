package org.example.communityapi.global.security;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    private boolean isBlacklisted(String token) {
        try {
            String isLogout = stringRedisTemplate.opsForValue().get(token);
            return "logout".equals(isLogout);
        } catch (Exception e) {
            log.error("Redis 조회 중 에러 발생: {}", e.getMessage());
            return false; // Redis 장애 시 서비스 전체 중단을 막기 위해 통과 처리
        }
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
                // 1. Redis 블랙리스트(로그아웃 여부) 확인
                if (isBlacklisted(token)) {
                    log.debug("로그아웃된 JWT 사용 시도");
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                // 2. Claims 파싱 (만료/위조 체크)
                Claims claims = jwtTokenProvider.getClaimsFromToken(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);

                // Refresh Token이 Access Token 자리에 들어오는 것 방지 (role 존재 여부 확인)
                if (!StringUtils.hasText(email) || !StringUtils.hasText(role)) {
                    log.debug("JWT에 필수 Claims(email/role)가 누락되었습니다.");
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                // 3. Authentication 객체 생성 및 Context 저장
                UsernamePasswordAuthenticationToken authentication = getAuthentication(role, email);
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException | IllegalArgumentException e) {
                log.debug("유효하지 않은 JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private static UsernamePasswordAuthenticationToken getAuthentication(String role, String email) {
        // "ROLE_" 중복 접두사 방지
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