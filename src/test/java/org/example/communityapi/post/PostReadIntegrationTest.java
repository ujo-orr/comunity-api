package org.example.communityapi.post;

import org.example.communityapi.category.Category;
import org.example.communityapi.category.CategoryRepository;
import org.example.communityapi.comment.Comment;
import org.example.communityapi.comment.CommentRepository;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
@AutoConfigureMockMvc
@WithMockUser
class PostReadIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    @Autowired PostService service;
    @Autowired CommentRepository comments;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    Member writer;
    Category category;
    List<Long> postIds;
    List<Long> commentIds;

    @BeforeEach
    void seed() {
        writer = members.save(Member.builder().email("reader@test.com").password("test")
                .nickname("12345").phoneNumber("01012345678").build());
        category = categories.save(Category.builder().name("read-test").build());
        postIds = new ArrayList<>();
        commentIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var post = posts.save(Post.builder().member(writer).category(category)
                    .title("paging " + i).content("body").build());
            postIds.add(post.getId());
            jdbc.update("update posts set created_at = '2026-01-01 00:00:00' where id = ?", post.getId());
        }
        for (int i = 0; i < 3; i++) {
            var comment = comments.save(Comment.builder().post(posts.findById(postIds.getFirst()).orElseThrow())
                    .member(writer).content("comment " + i).build());
            commentIds.add(comment.getId());
            jdbc.update("update comments set created_at = '2026-01-01 00:00:00' where id = ?", comment.getId());
        }
    }

    @AfterEach
    void clean() {
        comments.deleteAllById(commentIds);
        posts.deleteAllById(postIds);
        members.deleteById(writer.getId());
        categories.deleteById(category.getId());
    }

    // 게시글 단건 조회 증가
    @Test
    void detailIncrementsAndReturnsCurrentCount() throws Exception {
        Long id = postIds.getFirst();
        var updatedAt = posts.findById(id).orElseThrow().getUpdatedAt();
        for (int count = 1; count <= 2; count++) {
            mvc.perform(get("/api/posts/{id}", id)).andExpect(status().isOk())
                    .andExpect(jsonPath("id").value(id))
                    .andExpect(jsonPath("writerName").value("12345"))
                    .andExpect(jsonPath("viewCount").value(count));
        }
        assertThat(posts.findById(id).orElseThrow().getViewCount()).isEqualTo(2);
        assertThat(posts.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
    }

    // 목록 조회 API들의 페이징 동작, 미회수 증가 규칙
    @Test
    void listsArePagedAndDoNotIncrementViews() throws Exception {
        for (String path : List.of("/api/posts", "/api/posts/search", "/api/posts/by-nickname/12345")) {
            mvc.perform(get(path).param("title", "paging").param("size", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(3))
                    .andExpect(jsonPath("content[0].id").value(postIds.getLast()))
                    .andExpect(jsonPath("last").value(false));
            mvc.perform(get(path).param("page", "1").param("size", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(1))
                    .andExpect(jsonPath("content[0].id").value(postIds.getFirst()))
                    .andExpect(jsonPath("page").value(1)).andExpect(jsonPath("size").value(2))
                    .andExpect(jsonPath("totalElements").value(3)).andExpect(jsonPath("totalPages").value(2))
                    .andExpect(jsonPath("first").value(false)).andExpect(jsonPath("last").value(true));
        }
        assertThat(posts.findAllById(postIds)).allMatch(p -> p.getViewCount() == 0);
    }

    // 검색 필터 동장, 빈 페이지, 잘못된 검색어
    @Test
    void filtersAndEmptyPages() throws Exception {
        mvc.perform(get("/api/posts/search").param("title", "paging 1"))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(postIds.get(1)))
                .andExpect(jsonPath("page").value(0)).andExpect(jsonPath("size").value(20));
        mvc.perform(get("/api/posts").param("title", "missing"))
                .andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty())
                .andExpect(jsonPath("totalElements").value(0));
        mvc.perform(get("/api/posts").param("page", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty())
                .andExpect(jsonPath("totalElements").value(3));
        mvc.perform(get("/api/posts").param("title", " "))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(3));
    }

    // 댓글 페이징, 오름차순
    @Test
    void commentsArePagedOldestFirstAndScopedToPost() throws Exception {
        mvc.perform(get("/api/posts/{id}/comments", postIds.getFirst()).param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(2))
                .andExpect(jsonPath("content[0].id").value(commentIds.getFirst()))
                .andExpect(jsonPath("content[1].id").value(commentIds.get(1)))
                .andExpect(jsonPath("totalElements").value(3)).andExpect(jsonPath("last").value(false));
        mvc.perform(get("/api/posts/{id}/comments", postIds.getFirst()).param("size", "2").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(commentIds.getLast()))
                .andExpect(jsonPath("last").value(true));
        mvc.perform(get("/api/posts/{id}/comments", postIds.getLast()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
    }

    // 지원하지 않는 자원
    @Test
    void missingResourcesReturn404() throws Exception {
        for (String path : List.of("/api/posts/9223372036854775807",
                "/api/posts/9223372036854775807/comments", "/api/posts/by-nickname/missing")) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    // 유효하지 않은 페이징 파라미터
    @Test
    void invalidPageParametersReturn400() throws Exception {
        for (String path : List.of("/api/posts", "/api/posts/search", "/api/posts/by-nickname/12345",
                "/api/posts/" + postIds.getFirst() + "/comments")) {
            for (String[] param : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"},
                    new String[]{"size", "101"}, new String[]{"page", "abc"})) {
                mvc.perform(get(path).param(param[0], param[1])).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("code").value("C001"));
            }
        }
    }

    // 동시성 조회수 누락
    @Test
    void concurrentReadsDoNotLoseIncrements() throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                futures.add(executor.submit(() -> service.getPost(postIds.getFirst())));
            }
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        }
        assertThat(posts.findById(postIds.getFirst()).orElseThrow().getViewCount()).isEqualTo(30);
    }

    // 게시글 수정 시 이전 조회수 덮어쓰기 검증
    @Test
    void editingPreviouslyLoadedPostDoesNotOverwriteViewCount() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var loaded = posts.findById(postIds.getFirst()).orElseThrow();
            // 별도 DB 갱신 이후 영속성 컨텍스트의 조회 수는 이전 값으로 남아 있다.
            jdbc.update("update posts set view_count = view_count + 1 where id = ?", loaded.getId());
            loaded.updatePost("edited", "edited body", category);
        });
        var saved = posts.findById(postIds.getFirst()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("edited");
        assertThat(saved.getViewCount()).isEqualTo(1);
    }
}
