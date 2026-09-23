package org.example.communityapi.integration;

import org.example.communityapi.attachment.AttachmentStorage;
import org.example.communityapi.attachment.S3AttachmentStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("docker")
@Testcontainers
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.config.location=file:src/main/resources/application.yml",
        "JWT_SECRET=prod-profile-test-only-secret-at-least-32-bytes",
        "REDIS_HOST=localhost", "S3_BUCKET=test-bucket", "AWS_REGION=ap-northeast-2"
})
class ProdDeploymentDockerTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", mysql::getJdbcUrl);
        registry.add("DB_USERNAME", mysql::getUsername);
        registry.add("DB_PASSWORD", mysql::getPassword);
    }

    // 실제 AWS 호출 없이 운영 설정과 MySQL 마이그레이션 확인
    @MockitoBean S3Client s3;
    @Autowired AttachmentStorage storage;
    @Autowired Environment environment;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobLauncher launcher;
    @Autowired Job cleanupJob;

    @Test
    @DisplayName("운영 환경은 S3와 마이그레이션된 스키마를 사용하고 첨부파일 정리 배치를 정상 완료한다")
    void prodUsesS3AndFlywayAndCompletesCleanupBatch() throws Exception {
        assertThat(storage).isInstanceOf(S3AttachmentStorage.class);
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class)).isEqualTo(9);
        var execution = launcher.run(cleanupJob,
                new JobParametersBuilder().addLong("time", System.nanoTime()).toJobParameters());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("운영 환경에서는 최고 관리자도 API 문서와 데이터베이스 콘솔에 접근하면 403을 반환한다")
    @WithMockUser(roles = "SUPERADMIN")
    void prodDeniesDocsAndConsoleEvenForAdministrators() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui/index.html", "/h2-console/"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }
}
