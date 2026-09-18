package org.example.communityapi.attachment;

import org.example.communityapi.category.Category;
import org.example.communityapi.category.CategoryRepository;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.post.Post;
import org.example.communityapi.post.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
@Transactional
class PostAttachmentTimestampIntegrationTest {
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired PostAttachmentRepository attachments;
    @Autowired JdbcTemplate jdbc;

    @Test
    void attachmentStoresUploadTimeWithoutModificationColumn() {
        Member member = members.save(Member.builder()
                .email("attachment-time@test.com")
                .password("test")
                .nickname("attachment-time")
                .phoneNumber("01098765432")
                .build());
        Category category = categories.save(Category.builder().name("attachment-time").build());
        Post post = posts.save(Post.builder().member(member).category(category)
                .title("title").content("content").build());

        PostAttachment attachment = attachments.saveAndFlush(PostAttachment.builder()
                .post(post)
                .originalFileName("sample.txt")
                .storageKey("attachments/posts/" + post.getId() + "/sample")
                .contentType("text/plain")
                .fileSize(4)
                .build());

        assertThat(attachment.getCreatedAt()).isNotNull();
        Integer updatedAtColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'POST_ATTACHMENTS' AND COLUMN_NAME = 'UPDATED_AT'
                """, Integer.class);
        assertThat(updatedAtColumns).isZero();
    }
}
