package org.example.communityapi.attachment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
@ActiveProfiles("local")
class OrphanAttachmentCleanupLocalProfileTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("로컬 환경에서는 S3의 미사용 첨부파일 정리 기능을 활성화하지 않는다")
    void localProfileDoesNotCreateS3CleanupBeans() {
        assertThat(applicationContext.getBeansOfType(OrphanAttachmentCleanupService.class)).isEmpty();
        assertThat(applicationContext.containsBean("orphanAttachmentCleanupJob")).isFalse();
    }
}
