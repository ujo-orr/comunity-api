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
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("회원가입과 로그인 후 토큰을 재발급할 수 있고 사용한 Refresh Token과 로그아웃한 토큰은 재사용하지 못하도록 처리한다")
    void signupLoginReissueAndLogoutManageTokenLifecycle() throws Exception {
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
    @DisplayName("토큰을 재발급하면 Refresh Token이 변경되고 이전 Refresh Token은 재사용할 수 없다")
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
    @DisplayName("정지된 회원은 Refresh Token이 저장되어 있어도 토큰을 재발급할 수 없다")
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
    @DisplayName("회원이 탈퇴하면 Refresh Token을 삭제하고 유예 기간에는 같은 이메일로 가입할 수 없다")
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
    @DisplayName("같은 이메일로 새 계정을 생성해도 이전 계정의 Access Token으로 접근하면 401을 반환한다")
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
    @DisplayName("비밀번호를 변경하면 기존 Access Token과 Refresh Token은 폐기되고 새 비밀번호로 로그인할 수 있다")
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
    @DisplayName("탈퇴한 회원은 Access Token 없이 비밀번호로 계정을 복구하면 200을 반환한다")
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
    @DisplayName("관리자가 최고 관리자를 정지시키려고 하면 403을 반환한다")
    void adminCannotBanSuperadmin() throws Exception {
        Member admin = saveMember("admincheck", Role.ADMIN);
        Member superadmin = saveMember("supercheck", Role.SUPERADMIN);

        mvc.perform(patch("/api/admin/ban/{id}", superadmin.getId())
                        .header("Authorization", "Bearer " + accessToken(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("A004"));
    }

    @Test
    @DisplayName("변경할 권한을 누락하면 400을 반환하고 기존 권한을 유지한다")
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
    @DisplayName("존재하지 않는 회원과 잘못된 비밀번호의 로그인은 같은 에러 코드와 401을 반환한다")
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
    @DisplayName("정지를 해제해도 기존 Access Token은 401을 반환하고 새 토큰으로만 접근할 수 있다")
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
    @DisplayName("관리자 권한을 복원해도 기존 관리자 토큰은 401을 반환하고 새 토큰으로만 접근할 수 있다")
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
    @DisplayName("회원가입 시 이메일이 저장 가능한 길이를 초과하면 400을 반환한다")
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
    @DisplayName("회원 검색어에 포함된 SQL 주석 구문은 일반 문자열로 검색한다")
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
