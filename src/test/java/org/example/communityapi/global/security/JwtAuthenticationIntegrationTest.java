package org.example.communityapi.global.security;

import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
@AutoConfigureMockMvc
class JwtAuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired MemberRepository memberRepository;
    @MockBean StringRedisTemplate redisTemplate;
    @MockBean ValueOperations<String, String> valueOperations;

    Member member;
    String token;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        member = memberRepository.save(Member.builder()
                .email("jwt-" + suffix + "@test.com")
                .nickname("jwt-" + suffix)
                .phoneNumber("010" + suffix)
                .password("encoded")
                .build());
        token = jwtTokenProvider.createAccessToken(member.getEmail(), member.getRole());
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
    void doesNotCreateAdministratorAccountsByDefault() {
        assertThat(memberRepository.findByEmail("admin@system.com")).isEmpty();
        assertThat(memberRepository.findByEmail("superAdmin@system.com")).isEmpty();
    }

    @Test
    void bannedMemberCannotUseExistingToken() throws Exception {
        member.ban();
        memberRepository.save(member);
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changedRoleInvalidatesExistingToken() throws Exception {
        member.changeRole(Role.ADMIN);
        memberRepository.save(member);
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingMemberCannotUseExistingToken() throws Exception {
        memberRepository.deleteById(member.getId());
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
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
                .andExpect(status().isUnauthorized());
    }
}
