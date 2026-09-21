package org.example.communityapi.global.security;

import org.example.communityapi.auth.AuthService;
import org.example.communityapi.auth.dto.MemberLoginRequest;
import org.example.communityapi.auth.entity.RefreshToken;
import org.example.communityapi.auth.repository.RefreshTokenRepository;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.member.MemberService;
import org.example.communityapi.member.Role;
import org.example.communityapi.member.dto.MemberSignUpRequest;
import org.example.communityapi.member.dto.MemberUpdateRequest;
import org.example.communityapi.member.dto.MemberWithdrawalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class SecurityRegressionIntegrationTest {

    private static final String PASSWORD = "Password1!";

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired MemberService memberService;
    @Autowired MemberRepository members;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired JwtTokenProvider tokens;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean StringRedisTemplate redisTemplate;
    @MockitoBean ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void signupLoginReissueAndLogoutUseExistingControllerFlow() throws Exception {
        mvc.perform(post("/api/members/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"publicflow@test.com","password":"Password1!",
                                 "phoneNumber":"01012345678","nickname":"공개가입"}
                                """))
                .andExpect(status().isCreated());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"publicflow@test.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isOk()).andReturn();
        String refresh = mapper.readTree(login.getResponse().getContentAsString()).get("refreshToken").asText();
        var reissue = mvc.perform(post("/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("refreshToken", refresh))))
                .andExpect(status().isOk()).andReturn();
        String access = mapper.readTree(reissue.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(post("/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("A002"));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        assertThat(refreshTokens.findByEmail("publicflow@test.com")).isEmpty();
        org.mockito.Mockito.verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(access),
                org.mockito.ArgumentMatchers.eq("logout"), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void refreshTokenChangesEveryTimeAndCannotBeReused() {
        Member member = saveMember("rotate", Role.USER);
        String firstToken = authService.login(new MemberLoginRequest(member.getEmail(), PASSWORD)).refreshToken();

        var response = authService.reissue(firstToken);

        assertThat(response.refreshToken()).isNotEqualTo(firstToken);
        assertThat(tokens.getClaimsFromToken(response.refreshToken()).getId())
                .isNotEqualTo(tokens.getClaimsFromToken(firstToken).getId());
        assertThatThrownBy(() -> authService.reissue(firstToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void bannedMemberCannotRefreshEvenIfTokenRemainsInDatabase() {
        Member member = saveMember("banned", Role.USER);
        String refreshToken = tokens.createRefreshToken(member.getEmail());
        refreshTokens.save(new RefreshToken(member.getEmail(), refreshToken));
        member.ban();

        assertThatThrownBy(() -> authService.reissue(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void withdrawalRemovesRefreshTokenAndReservesEmailDuringGracePeriod() {
        Member member = saveMember("withdraw", Role.USER);
        authService.login(new MemberLoginRequest(member.getEmail(), PASSWORD));

        memberService.withdrawMember(member.getEmail(), new MemberWithdrawalRequest(PASSWORD));

        assertThat(refreshTokens.findByEmail(member.getEmail())).isEmpty();
        assertThatThrownBy(() -> memberService.signUp(new MemberSignUpRequest(
                member.getEmail(), PASSWORD, "01012345678", "새회원")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void oldAccessTokenCannotAuthenticateANewAccountUsingTheSameEmail() throws Exception {
        Member original = saveMember("reused", Role.USER);
        String accessToken = accessToken(original);
        members.delete(original);
        members.flush();
        Member replacement = saveMember("reused", Role.USER);
        assertThat(replacement.getId()).isNotEqualTo(original.getId());

        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRevokesExistingAccessAndRefreshTokens() throws Exception {
        Member member = saveMember("pwdchange", Role.USER);
        var originalTokens = authService.login(new MemberLoginRequest(member.getEmail(), PASSWORD));

        memberService.updateMyProfile(member.getEmail(), new MemberUpdateRequest(null, null, "Changed1!Password"));

        assertThat(refreshTokens.findByEmail(member.getEmail())).isEmpty();
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + originalTokens.accessToken()))
                .andExpect(status().isUnauthorized());
        var newTokens = authService.login(new MemberLoginRequest(member.getEmail(), "Changed1!Password"));
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + newTokens.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void withdrawnMemberCanRestoreWithPasswordWithoutAnAccessToken() throws Exception {
        Member member = saveMember("restore", Role.USER);
        memberService.withdrawMember(member.getEmail(), new MemberWithdrawalRequest(PASSWORD));
        members.flush();

        mvc.perform(post("/api/members/restore")
                        .param("email", member.getEmail())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        assertThat(members.findByEmail(member.getEmail())).isPresent();
    }

    @Test
    void adminCannotBanSuperadmin() throws Exception {
        Member admin = saveMember("admincheck", Role.ADMIN);
        Member superadmin = saveMember("supercheck", Role.SUPERADMIN);

        mvc.perform(patch("/api/admin/ban/{id}", superadmin.getId())
                        .header("Authorization", "Bearer " + accessToken(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("A004"));
    }

    @Test
    void roleChangeRejectsMissingRole() throws Exception {
        Member superadmin = saveMember("rolecheck", Role.SUPERADMIN);
        Member target = saveMember("roletarget", Role.USER);

        mvc.perform(patch("/api/v1/superadmin/members/{id}/role", target.getId())
                        .header("Authorization", "Bearer " + accessToken(superadmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(target.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void unknownMemberAndWrongPasswordHaveTheSameLoginResponse() throws Exception {
        saveMember("logincheck", Role.USER);

        for (String email : new String[]{"logincheck@test.com", "missing@test.com"}) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"Wrong1!Password\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("code").value("A001"));
        }
    }

    @Test
    void unbanningDoesNotMakeOldAccessTokenValidAgain() throws Exception {
        Member member = saveMember("unbancheck", Role.USER);
        String oldToken = accessToken(member);

        member.ban();
        member.unban();

        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + accessToken(member)))
                .andExpect(status().isOk());
    }

    @Test
    void restoringRoleDoesNotMakeOldAdminTokenValidAgain() throws Exception {
        Member member = saveMember("oldadmin", Role.ADMIN);
        String oldToken = accessToken(member);

        member.changeRole(Role.USER);
        member.changeRole(Role.ADMIN);

        mvc.perform(get("/api/admin/members").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/members").header("Authorization", "Bearer " + accessToken(member)))
                .andExpect(status().isOk());
    }

    @Test
    void signupRejectsEmailLongerThanDatabaseColumn() throws Exception {
        mvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"email-longer-than-thirty-characters@test.com",
                                 "password":"Password1!","phoneNumber":"01012345678","nickname":"회원가입"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sqlCommentInSearchKeywordIsTreatedAsText() {
        saveMember("adminsearch", Role.USER);

        assertThat(members.searchAdminMemberResponsesByKeyword("adminsearch")).hasSize(1);
        assertThat(members.searchAdminMemberResponsesByKeyword("admin'--")).isEmpty();
    }

    private Member saveMember(String name, Role role) {
        return members.saveAndFlush(Member.builder()
                .email(name + "@test.com")
                .nickname(name)
                .phoneNumber(name)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .build());
    }

    private String accessToken(Member member) {
        return tokens.createAccessToken(member.getEmail(), member.getRole(), member.getId(), member.getTokenVersion());
    }
}
