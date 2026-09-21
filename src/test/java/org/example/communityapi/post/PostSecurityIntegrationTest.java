package org.example.communityapi.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.communityapi.category.Category;
import org.example.communityapi.category.CategoryRepository;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "security-writer@test.com")
class PostSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PostRepository posts;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;

    Post target;
    Category category;

    @BeforeEach
    void seed() {
        Member writer = members.save(Member.builder()
                .email("security-writer@test.com").password("test")
                .nickname("admin'--").phoneNumber("01077775555").build());
        category = categories.save(Category.builder().name("security").build());
        target = posts.save(Post.builder().member(writer).category(category)
                .title("admin'--").content("original").build());
        posts.save(Post.builder().member(writer).category(category)
                .title("ordinary post").content("body").build());
    }

    @Test
    @DisplayName("비회원도 게시글 목록과 상세를 조회하고 검색할 수 있으며 빈 검색어는 400을 반환한다")
    @WithAnonymousUser
    void anonymousVisitorsCanListSearchAndReadPosts() throws Exception {
        mvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2));
        for (String keyword : List.of("admin'--", "original", "ordinary")) {
            mvc.perform(get("/api/posts").param("keyword", keyword))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("totalElements").value(keyword.equals("admin'--") ? 2 : 1));
        }
        mvc.perform(get("/api/posts/{id}", target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(target.getId()))
                .andExpect(jsonPath("viewCount").value(1));
        mvc.perform(get("/api/posts").param("keyword", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("message").value("검색어를 입력해 주세요."));
    }

    @Test
    @DisplayName("비회원이 게시글 생성이나 수정 또는 삭제를 요청하면 401을 반환하고 게시글을 변경하지 않는다")
    @WithAnonymousUser
    void anonymousVisitorsCannotCreateUpdateOrDeletePosts() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "changed", "content", "changed", "categoryId", category.getId()));
        mvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/posts/{id}", target.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/posts/{id}", target.getId()))
                .andExpect(status().isUnauthorized());
        assertThat(posts.count()).isEqualTo(2);
        assertThat(posts.findById(target.getId()).orElseThrow().getContent()).isEqualTo("original");
    }

    @Test
    @DisplayName("SQL 구문이 포함된 검색어는 일반 문자열로 검색한다")
    void sqlSyntaxIsTreatedAsSearchText() throws Exception {
        mvc.perform(get("/api/posts").param("keyword", "admin'--"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2));
        mvc.perform(get("/api/posts").param("keyword", "' OR 1=1 --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(0));

    }

    @Test
    @DisplayName("게시글 생성이나 수정 시 글자 수 제한을 초과하거나 카테고리 ID가 유효하지 않으면 400을 반환한다")
    void createAndUpdateRejectOversizedTextAndInvalidCategory() throws Exception {
        long count = posts.count();
        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("title", "x".repeat(101), "content", "body", "categoryId", category.getId()),
                Map.of("title", "title", "content", "x".repeat(10001), "categoryId", category.getId()),
                Map.of("title", "title", "content", "body", "categoryId", 0))) {
            String json = objectMapper.writeValueAsString(body);
            mvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("C001"));
            mvc.perform(patch("/api/posts/{id}", target.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("C001"));
        }
        assertThat(posts.count()).isEqualTo(count);
        assertThat(target.getContent()).isEqualTo("original");
    }

    @Test
    @DisplayName("작성자가 아닌 회원이 게시글 수정이나 삭제를 요청하면 403을 반환한다")
    void otherMemberCannotChangeOrDeletePost() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "changed", "content", "changed", "categoryId", category.getId()));
        mvc.perform(patch("/api/posts/{id}", target.getId()).with(user("other@test.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/posts/{id}", target.getId()).with(user("other@test.com")))
                .andExpect(status().isForbidden());
        assertThat(posts.findById(target.getId())).isPresent();
        assertThat(target.getTitle()).isEqualTo("admin'--");
    }

    @Test
    @DisplayName("게시글 ID가 유효하지 않거나 검색어 길이가 제한을 초과하면 400을 반환한다")
    void invalidIdsAndOversizedSearchReturn400() throws Exception {
        mvc.perform(get("/api/posts/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts").param("keyword", "x".repeat(251)))
                .andExpect(status().isBadRequest());

    }
}
