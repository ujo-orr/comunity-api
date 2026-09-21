package org.example.communityapi.attachment;

import org.example.communityapi.category.Category;
import org.example.communityapi.category.CategoryRepository;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.post.Post;
import org.example.communityapi.post.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
@Transactional
class PostAttachmentTimestampIntegrationTest {
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired PostAttachmentRepository attachments;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path uploadDirectory;

    @Test
    @DisplayName("첨부파일 업로드가 롤백되면 디스크에 저장된 파일도 삭제된다")
    void rolledBackUploadDoesNotLeaveFileOnDisk() {
        Member member = members.save(Member.builder()
                .email("rollback@test.com")
                .password("test")
                .nickname("rollback")
                .phoneNumber("01012345678")
                .build());

        Category category =
                categories.save(Category.builder()
                        .name("rollback").build());

        Post post =
                posts.save(Post.builder()
                        .member(member)
                        .category(category)
                        .title("title")
                        .content("content")
                        .build());

        FileStorageService storage = new FileStorageService(uploadDirectory.toString());
        PostAttachmentService service = new PostAttachmentService(attachments, posts, storage);

        var file = new MockMultipartFile(
                "files",
                "sample.txt",
                "text/plain",
                new byte[]{1});

        service.upload(post.getId(), List.of(file), member.getEmail());
        String key = attachments.findByPostId(post.getId()).getFirst().getStorageKey();
        Path storedFile = uploadDirectory.resolve(key);
        assertThat(storedFile).exists();

        TestTransaction.flagForRollback();
        TestTransaction.end();

        assertThat(storedFile).doesNotExist();
    }

    @Test
    @DisplayName("첨부파일은 업로드 시각을 기록하고 수정 시각은 별도 저장하지 않는다")
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
