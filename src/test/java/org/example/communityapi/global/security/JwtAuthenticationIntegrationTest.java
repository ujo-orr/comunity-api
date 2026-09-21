package org.example.communityapi.global.security;

import jakarta.servlet.ServletContext;
import java.util.UUID;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.Role;
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
    void validTokenCanAccessProtectedEndpoint() throws Exception {
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("email").value(member.getEmail()));
    }

    @Test
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
    void tokenVersionMismatchIsRejected() throws Exception {
        String stale = jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole(), member.getId(),
                member.getTokenVersion() + 1);
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + stale))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }

    @Test
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
    void doesNotCreateAdministratorAccountsByDefault() {
        assertThat(memberRepository.findByEmail("admin@system.com")).isEmpty();
        assertThat(memberRepository.findByEmail("superAdmin@system.com")).isEmpty();
    }

    @Test
    void bannedMemberCannotUseExistingToken() throws Exception {
        member.ban();
        memberRepository.save(member);
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }

    @Test
    void changedRoleInvalidatesExistingToken() throws Exception {
        member.changeRole(Role.ADMIN);
        memberRepository.save(member);
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }

    @Test
    void missingMemberCannotUseExistingToken() throws Exception {
        memberRepository.deleteById(member.getId());
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }

    @Test
    void redisFailureReturnsServiceUnavailable() throws Exception {
        when(valueOperations.get(anyString())).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("redis unavailable"));
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andDo(print()).andExpect(jsonPath("code").value("S003"));
    }

    @Test
    void loggedOutTokenIsRejected() throws Exception {
        when(valueOperations.get(token)).thenReturn("logout");
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("A002"));
    }
}
