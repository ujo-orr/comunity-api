package org.example.communityapi.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private JwtAuthenticationFilter filter;
    private String token;

    @BeforeEach
    void setUp() {
        var provider = new JwtTokenProvider("test-secret-key-must-be-at-least-32-bytes-long", 60000, 120000);
        filter = new JwtAuthenticationFilter(provider, redis, members, new ObjectMapper());
        token = provider.createAccessToken("filter@test.com", Role.USER, 1L, 0);
        request.addHeader("Authorization", "Bearer " + token);
        when(redis.opsForValue()).thenReturn(values);
        when(members.findByEmail("filter@test.com")).thenReturn(Optional.of(
                Member.builder().id(1L).email("filter@test.com").build()));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("old", ""));
    }

    @AfterEach
    void clean() { SecurityContextHolder.clearContext(); }

    @Test
    @DisplayName("Redis 장애가 발생하면 인증 정보를 제거하고 요청 처리를 중단하며 503을 반환한다")
    void redisOutageStopsChainAndClearsAuthentication() throws Exception {
        when(values.get(token)).thenThrow(new RedisConnectionFailureException("redis-secret"));
        filter.doFilter(request, response, chain);
        assertUnavailable();
        verifyNoInteractions(members);
    }

    @Test
    @DisplayName("인증 중 데이터베이스 장애가 발생하면 인증 정보를 제거하고 요청 처리를 중단하며 503을 반환한다")
    void databaseOutageStopsChainAndClearsAuthentication() throws Exception {
        when(members.findByEmail(anyString())).thenThrow(new DataAccessResourceFailureException("db-secret"));
        filter.doFilter(request, response, chain);
        assertUnavailable();
    }

    @Test
    @DisplayName("인증 중 내부 오류가 발생하면 인증 정보를 제거하고 내부 내용을 노출하지 않은 채 500을 반환한다")
    void programmingErrorIsNotDisguisedAsInvalidTokenOrOutage() throws Exception {
        when(members.findByEmail(anyString())).thenThrow(new IllegalArgumentException("internal-secret"));
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("S001").doesNotContain("internal-secret");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("후속 요청 처리 중 발생한 예외는 그대로 전달하고 요청을 재처리하지 않는다")
    void downstreamExceptionIsPropagatedAndChainIsNotRetried() throws Exception {
        when(values.get(token)).thenReturn("logout");
        var downstreamFailure = new JwtException("failure outside authentication");
        doThrow(downstreamFailure).when(chain).doFilter(request, response);
        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(downstreamFailure);
        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("유효한 토큰으로 회원을 인증하고 후속 요청을 한 번만 처리한다")
    void validTokenAuthenticatesAndCallsChainOnce() throws Exception {
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("filter@test.com");
        verify(chain, times(1)).doFilter(request, response);
    }

    private void assertUnavailable() throws Exception {
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("S003").doesNotContain("secret");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain);
    }
}
