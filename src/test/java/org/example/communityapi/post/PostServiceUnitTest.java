package org.example.communityapi.post;

import org.example.communityapi.attachment.FileStorageService;
import org.example.communityapi.attachment.PostAttachmentRepository;
import org.example.communityapi.category.Category;
import org.example.communityapi.category.CategoryRepository;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.post.dto.PostUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceUnitTest {
    @Mock PostRepository posts;
    @Mock MemberRepository members;
    @Mock CategoryRepository categories;
    @Mock PostAttachmentRepository attachments;
    @Mock FileStorageService files;
    @InjectMocks PostService service;

    @Test
    void writerCanUpdatePostAndCategory() {
        Member writer =
                Member.builder()
                        .email("writer@test.com")
                        .build();
        Category oldCategory =
                Category
                        .builder()
                        .name("old").build();
        Category newCategory =
                Category
                        .builder()
                        .name("new")
                        .build();
        Post post =
                Post.builder()
                        .member(writer)
                        .category(oldCategory)
                        .title("before")
                        .content("before body")
                        .build();

        when(posts.findByIdWithMember(1L))
                .thenReturn(Optional.of(post));
        when(categories.findById(2L))
                .thenReturn(Optional.of(newCategory));

        service.updatePost(
                1L,
                new PostUpdateRequest("after",  "after body",  2L),
                "writer@test.com");

        assertThat(post.getTitle()).isEqualTo("after");
        assertThat(post.getContent()).isEqualTo("after body");
        assertThat(post.getCategory()).isSameAs(newCategory);
    }

    @Test
    void otherMemberCannotUpdatePostOrLoadNewCategory() {
        Member writer =
                Member
                        .builder()
                        .email("writer@test.com")
                        .build();
        Post post =
                Post
                        .builder()
                        .member(writer)
                        .title("original")
                        .content("original body")
                        .build();

        when(posts.findByIdWithMember(1L))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.updatePost(
                1L,
                new PostUpdateRequest("changed", "changed body", 2L), "other@test.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(post.getTitle()).isEqualTo("original");
        verifyNoInteractions(categories);
    }

    @Test
    void missingPostDoesNotIncrementViewCount() {
        when(posts.incrementViewCount(99L)).thenReturn(0);

        assertThatThrownBy(() -> service.getPost(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
        verify(posts, never()).findResponseById(anyLong());
    }
}
