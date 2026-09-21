package org.example.communityapi.schema;

import jakarta.persistence.EntityManager;
import org.example.communityapi.auth.AuthService;
import org.example.communityapi.auth.dto.MemberLoginRequest;
import org.example.communityapi.auth.repository.RefreshTokenRepository;
import org.example.communityapi.category.Category;
import org.example.communityapi.category.CategoryRepository;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.member.*;
import org.example.communityapi.member.dto.*;
import org.example.communityapi.post.Post;
import org.example.communityapi.post.PostRepository;
import org.example.communityapi.post.PostService;
import org.example.communityapi.postlike.PostLikeRepository;
import org.example.communityapi.postlike.PostLikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@Transactional
abstract class MemberSchemaContract {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(70000000);
    private static final String PASSWORD = "Password1!";

    @Autowired MemberService memberService;
    @Autowired MemberRepository members;
    @Autowired MemberWithdrawalRepository withdrawals;
    @Autowired AuthService auth;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired PostService postService;
    @Autowired PostLikeService likes;
    @Autowired PostLikeRepository likeRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("회원가입 시 생성과 수정 시각을 기록하고 중복 전화번호 가입과 변경을 거부한다")
    void signupAndProfileChangesRejectDuplicatePhoneNumbers() {
        Member first = signup();
        Member second = signup();
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(first.getUpdatedAt()).isNotNull();
        var duplicate = new MemberSignUpRequest("duplicate@test.com", PASSWORD, first.getPhoneNumber(), "중복회원");
        assertThatThrownBy(() -> memberService.signUp(duplicate))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_PHONE_NUMBER);
        assertThatThrownBy(() -> memberService.updateMyProfile(second.getEmail(),
                new MemberUpdateRequest(null, first.getPhoneNumber(), null)))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_PHONE_NUMBER);
    }

    @Test
    @DisplayName("서비스 중복 확인을 우회해도 DB가 중복 전화번호 저장을 거부한다")
    void databaseRejectsDuplicatePhoneNumbers() {
        Member first = signup();
        assertThatThrownBy(() -> members.saveAndFlush(Member.builder()
                .email("duplicate@test.com").password("encoded").nickname("duplicate")
                .phoneNumber(first.getPhoneNumber()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("30자 이메일은 DB에 저장할 수 있다")
    void databaseAcceptsThirtyCharacterEmail() {
        members.saveAndFlush(Member.builder().email("a".repeat(21) + "@test.com")
                .password("encoded").nickname("boundary").phoneNumber("01099999998").build());
        assertThat(members.findByEmail("a".repeat(21) + "@test.com")).isPresent();
    }

    @Test
    @DisplayName("입력값 검증을 우회해도 DB가 30자를 초과한 이메일을 거부한다")
    void databaseRejectsEmailLongerThanThirtyCharacters() {
        assertThatThrownBy(() -> members.saveAndFlush(Member.builder()
                .email("a".repeat(22) + "@test.com").password("encoded")
                .nickname("longemail").phoneNumber("01099999999").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "admin"})
    @DisplayName("DB는 정의되지 않은 권한과 소문자로 작성한 권한을 거부한다")
    void databaseRejectsInvalidRoles(String role) {
        Member member = signup();
        assertCheckViolation(() -> jdbc.update("UPDATE members SET role = ? WHERE id = ?", role, member.getId()));
        MemberWithdrawal withdrawal = withdrawals.saveAndFlush(new MemberWithdrawal(member));
        assertCheckViolation(() -> jdbc.update("UPDATE member_withdrawal SET role = ? WHERE id = ?", role, withdrawal.getId()));
        assertThat(jdbc.queryForObject("SELECT role FROM members WHERE id = ?", String.class, member.getId()))
                .isEqualTo("USER");
        assertThat(jdbc.queryForObject("SELECT role FROM member_withdrawal WHERE id = ?", String.class, withdrawal.getId()))
                .isEqualTo("USER");
    }

    @Test
    @DisplayName("DB는 정의되지 않은 회원 상태를 거부한다")
    void databaseRejectsInvalidMemberStatus() {
        Member member = signup();
        assertCheckViolation(() -> jdbc.update("UPDATE members SET status = 'UNKNOWN' WHERE id = ?", member.getId()));
        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, member.getId()))
                .isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("일반 회원과 관리자 및 최고 관리자의 탈퇴 기록과 30일 유예 기간을 저장하고 원래 권한으로 복구한다")
    void withdrawalAndRestorationPreserveEveryRoleAndWithdrawalTime(Role role) {
        Member member = signup();
        member.changeRole(role);
        members.flush();
        Long originalId = member.getId();
        String email = member.getEmail();
        auth.login(new MemberLoginRequest(email, PASSWORD));
        assertThat(refreshTokens.findByEmail(email)).isPresent();

        memberService.withdrawMember(email, new MemberWithdrawalRequest(PASSWORD));
        entityManager.flush();
        entityManager.clear();

        assertThat(members.findByEmail(email)).isEmpty();
        assertThat(refreshTokens.findByEmail(email)).isEmpty();
        MemberWithdrawal withdrawal = withdrawals.findByEmail(email).orElseThrow();
        assertThat(withdrawal.getOriginalId()).isEqualTo(originalId);
        assertThat(withdrawal.getRole()).isEqualTo(role);
        assertThat(withdrawal.getOriginalCreatedAt()).isNotNull();
        assertThat(withdrawal.getDeletedAt()).isNotNull();
        assertThat(withdrawal.getExpireAt()).isEqualTo(withdrawal.getDeletedAt().plusDays(30));
        assertThat(withdrawal.getCreatedAt()).isNotNull();

        memberService.cancelWithdrawal(email, new MemberWithdrawalRequest(PASSWORD));
        entityManager.flush();
        entityManager.clear();
        assertThat(withdrawals.findByEmail(email)).isEmpty();
        Member restored = members.findByEmail(email).orElseThrow();
        assertThat(restored.getRole()).isEqualTo(role);
        assertThat(restored.getId()).isNotEqualTo(originalId);
    }

    @Test
    @DisplayName("탈퇴 후 다른 회원이 사용 중인 전화번호로 계정을 복구할 수 없다")
    void restorationRejectsPhoneNumberUsedByAnotherMember() {
        Member original = signup();
        String email = original.getEmail();
        String phone = original.getPhoneNumber();
        memberService.withdrawMember(email, new MemberWithdrawalRequest(PASSWORD));
        entityManager.flush();
        Member other = signup();
        memberService.updateMyProfile(other.getEmail(), new MemberUpdateRequest(null, phone, null));
        entityManager.flush();
        assertThatThrownBy(() -> memberService.cancelWithdrawal(email, new MemberWithdrawalRequest(PASSWORD)))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_PHONE_NUMBER);
    }

    @Test
    @DisplayName("프로필 수정 시 JPA가 수정 시각을 갱신하고 생성 시각을 유지한다")
    void auditingUpdatesModificationTimeAndPreservesCreationTime() {
        Member member = signup();
        Long id = member.getId();
        LocalDateTime old = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbc.update("UPDATE members SET created_at = ?, updated_at = ? WHERE id = ?", old, old, id);
        entityManager.clear();
        memberService.updateMyProfile(member.getEmail(), new MemberUpdateRequest("새닉네임", null, null));
        entityManager.flush();
        entityManager.clear();
        Member updated = members.findById(id).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(old);
        assertThat(updated.getUpdatedAt()).isAfter(old);
    }

    @Test
    @DisplayName("좋아요를 저장할 수 있고 게시글 조회수를 증가시켜도 수정 시각은 유지한다")
    void likesCanBeCreatedAndViewsDoNotChangeModificationTime() {
        createLikeAndVerifyViewTimestamp();
    }

    void createLikeAndVerifyViewTimestamp() {
        Member member = signup();
        Category category = categories.saveAndFlush(Category.builder().name(member.getNickname()).build());
        Post post = posts.saveAndFlush(Post.builder().member(member).category(category)
                .title("title").content("content").build());
        Long postId = post.getId();
        entityManager.clear();
        LocalDateTime updatedAt = posts.findById(postId).orElseThrow().getUpdatedAt();
        likes.likePost(postId, member.getEmail());
        entityManager.flush();
        assertThat(likeRepository.countByPostId(postId)).isEqualTo(1);
        postService.getPost(postId);
        entityManager.clear();
        Post read = posts.findById(postId).orElseThrow();
        assertThat(read.getViewCount()).isEqualTo(1);
        assertThat(read.getUpdatedAt()).isEqualTo(updatedAt);
    }

    private void assertCheckViolation(org.assertj.core.api.ThrowableAssert.ThrowingCallable statement) {
        String database = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        // MySQL CHECK 위반은 HY000/3819, H2는 23513으로 전달된다.
        boolean mysql = "MySQL".equals(database);
        assertThatThrownBy(statement).isInstanceOf(DataAccessException.class)
                .rootCause().isInstanceOfSatisfying(SQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo(mysql ? "HY000" : "23513");
                    assertThat(exception.getErrorCode()).isEqualTo(mysql ? 3819 : 23513);
                });
    }

    Member signup() {
        int suffix = SEQUENCE.incrementAndGet();
        Long id = memberService.signUp(new MemberSignUpRequest("m" + suffix + "@test.com", PASSWORD,
                "010" + suffix, "m" + suffix));
        members.flush();
        return members.findById(id).orElseThrow();
    }
}
