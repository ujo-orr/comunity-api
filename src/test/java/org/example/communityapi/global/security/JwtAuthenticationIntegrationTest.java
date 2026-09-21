package org.example.communityapi.global.security;

import jakarta.servlet.ServletContext;
import java.util.UUID;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
@AutoConfigureMockMvc
class JwtAuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired MemberRepository memberRepository;
    @MockitoBean StringRedisTemplate redisTemplate;
    @MockitoBean ValueOperations<String, String> valueOperations;

    @Autowired ApplicationContext context;
    @Autowired ServletContext servletContext;
    @Autowired SecurityFilterChain securityFilterChain;
    @Autowired FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration;
    @Autowired TestRestTemplate client;
    @MockitoSpyBean JwtAuthenticationFilter filter;

    Member member;
    String token;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        member = memberRepository.save(Member.builder()
                .email("jwt-" + suffix + "@test.com")
                .nickname("jwt" + suffix.substring(0, 6))
                .phoneNumber("010" + suffix)
                .password("encoded")
                .build());
        token = jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole(), member.getId(), member.getTokenVersion());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        memberRepository.deleteById(member.getId());
    }

    @Test
    @DisplayName("유효한 Access Token으로 내 정보를 조회하면 200과 회원 정보를 반환한다")
    void validTokenCanAccessProtectedEndpoint() throws Exception {
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("email").value(member.getEmail()));
    }

    @Test
    @DisplayName("기본 사용자 인증을 비활성화하고 JWT 인증을 인가 전에 한 번만 적용하도록 구성한다")
    void defaultUserAndServletFilterRegistrationAreAbsent() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(jwtFilterRegistration.isEnabled()).isFalse();
        assertThat(jwtFilterRegistration.getFilter()).isSameAs(filter);
        assertThat(servletContext.getFilterRegistrations().values())
                .noneMatch(registration -> registration.getClassName().contains("JwtAuthenticationFilter"));
        var filters = securityFilterChain.getFilters();
        assertThat(filters.stream().filter(candidate -> candidate == filter).count()).isEqualTo(1);
        assertThat(filters.indexOf(filter)).isLessThan(filters.indexOf(filters.stream()
                .filter(AuthorizationFilter.class::isInstance).findFirst().orElseThrow()));
        assertThat(filters).noneMatch(candidate -> candidate instanceof UsernamePasswordAuthenticationFilter
                || candidate instanceof BasicAuthenticationFilter || candidate instanceof LogoutFilter);
    }

    @Test
    @DisplayName("실제 HTTP 요청은 JWT 인증을 한 번만 수행하고 세션 쿠키 없이 200을 반환한다")
    void realServletRequestExecutesJwtFilterOnce() throws Exception {
        clearInvocations(filter, valueOperations);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var response = client.exchange("/api/members/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        verify(filter, times(1)).doFilter(any(), any(), any());
        verify(valueOperations, times(1)).get(token);
    }

    @Test
    @DisplayName("인증 정보를 세션에 유지하지 않아 후속 요청에 토큰이 없으면 401을 반환한다")
    void authenticationIsNotRetainedInSession() throws Exception {
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
        mvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    @DisplayName("잘못된 토큰이나 Refresh Token으로 보호 API에 접근하면 401을 반환한다")
    void malformedAndRefreshTokensCannotAuthenticate() throws Exception {
        for (String invalid : new String[]{"invalid.jwt", jwtTokenProvider.createRefreshToken(member.getEmail())}) {
            mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("code").value("A002"))
                    .andExpect(jsonPath("message").value("인증이 필요하거나 유효하지 않은 토큰입니다."));
        }
    }

    @Test
    @DisplayName("회원과 토큰의 버전이 일치하지 않으면 401을 반환한다")
    void tokenVersionMismatchIsRejected() throws Exception {
        String stale = jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole(), member.getId(),
                member.getTokenVersion() + 1);
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + stale))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }

    @Test
    @DisplayName("권한이 없는 회원이 관리자 API나 카테고리 생성 API에 접근하면 403을 반환한다")
    void insufficientRoleReturnsExistingForbiddenResponse() throws Exception {
        mvc.perform(get("/api/admin/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("code").value("A003"))
                .andExpect(jsonPath("message").value("해당 리소스에 접근할 권한이 없습니다."));
        mvc.perform(post("/api/categories").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"name\":\"카테고리\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("A003"));
    }

    @Test
    @DisplayName("토큰이 없거나 유효하지 않아도 공개 게시글 목록과 회원 검색은 200을 반환한다")
    void publicReadsAllowAnonymousRequestsEvenWithInvalidToken() throws Exception {
        for (String authorization : new String[]{"", "Bearer invalid.jwt"}) {
            mvc.perform(get("/api/posts").header("Authorization", authorization))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/members/search").param("nickname", member.getNickname())
                            .header("Authorization", authorization))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("기본 설정에서는 관리자와 최고 관리자 계정을 자동으로 생성하지 않는다")
    void doesNotCreateAdministratorAccountsByDefault() {
        assertThat(memberRepository.findByEmail("admin@system.com")).isEmpty();
        assertThat(memberRepository.findByEmail("superAdmin@system.com")).isEmpty();
    }

    @Test
    @DisplayName("정지된 회원이 기존 Access Token으로 보호 API에 접근하면 401을 반환한다")
    void bannedMemberCannotUseExistingToken() throws Exception {
        member.ban();
        memberRepository.save(member);
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }

    @Test
    @DisplayName("회원 권한이 변경되면 기존 Access Token으로 보호 API에 접근할 때 401을 반환한다")
    void changedRoleInvalidatesExistingToken() throws Exception {
        member.changeRole(Role.ADMIN);
        memberRepository.save(member);
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }

    @Test
    @DisplayName("삭제된 회원의 기존 Access Token으로 보호 API에 접근하면 401을 반환한다")
    void missingMemberCannotUseExistingToken() throws Exception {
        memberRepository.deleteById(member.getId());
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }

    @Test
    @DisplayName("인증 중 Redis 연결에 실패하면 503을 반환한다")
    void redisFailureReturnsServiceUnavailable() throws Exception {
        when(valueOperations.get(anyString())).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("redis unavailable"));
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andDo(print()).andExpect(jsonPath("code").value("S003"));
    }

    @Test
    @DisplayName("로그아웃한 Access Token으로 보호 API에 접근하면 401을 반환한다")
    void loggedOutTokenIsRejected() throws Exception {
        when(valueOperations.get(token)).thenReturn("logout");
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }
}
