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
                // 먼저 서명과 만료 시간을 검증한다.
                Claims claims = jwtTokenProvider.getClaimsFromToken(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);

                // Refresh Token이 Access Token 자리에 들어오는 것을 막는다.
                if (!StringUtils.hasText(email) || !StringUtils.hasText(role)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                // Redis 장애 시 로그아웃된 토큰을 판별할 수 없으므로 인증 요청을 거절한다.
                boolean blacklisted;
                try {
                    blacklisted = isBlacklisted(token);
                } catch (RuntimeException e) {
                    log.error("JWT 블랙리스트 조회 실패", e);
                    SecurityContextHolder.clearContext();
                    writeAuthenticationUnavailable(response);
                    return;
                }
                if (blacklisted) {
                    log.debug("로그아웃된 JWT 사용 시도");
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                // 차단, 탈퇴 또는 권한 변경 후에 발급 전 토큰이 계속 쓰이지 않도록 한다.
                Member member = memberRepository.findByEmail(email).orElse(null);
                if (member == null || member.getStatus() != MemberStatus.ACTIVE
                        || !member.getRole().name().equals(role)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                UsernamePasswordAuthenticationToken authentication = getAuthentication(role, email);
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException | IllegalArgumentException e) {
                log.debug("유효하지 않은 JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeAuthenticationUnavailable(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_SERVICE_UNAVAILABLE;
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
