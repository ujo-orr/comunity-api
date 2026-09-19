package org.example.communityapi.integration;

import org.example.communityapi.auth.dto.AuthLoginResponse;
import org.example.communityapi.auth.dto.MemberLoginRequest;
import org.example.communityapi.auth.dto.RefreshTokenRequest;
import org.example.communityapi.auth.dto.TokenResponse;
import org.example.communityapi.attachment.dto.AttachmentResponse;
import org.example.communityapi.category.Category;
import org.example.communityapi.category.CategoryRepository;
import org.example.communityapi.member.dto.MemberSignUpRequest;
import org.example.communityapi.post.PostRepository;
import org.example.communityapi.post.dto.PostCreateRequest;
import org.example.communityapi.post.dto.PostResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.*;

@Tag("docker")
@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=true",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.job.enabled=false"
})
class CommunityFlowDockerTest {
    @TempDir
    static Path uploadDir;

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("community")
            .withUsername("community_app")
            .withPassword("test-only-password");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void databaseAndRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("file.upload-dir", () -> uploadDir.toString());
    }

    @Autowired TestRestTemplate http;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired JdbcTemplate jdbc;

    @Test
    void signupLoginReadAndLogoutWorkWithRealMySqlAndRedis() {
        // 카테고리는 관리자만 만들 수 있어서 테스트 전에 미리 저장
        Long categoryId = categories.save(Category.builder().name("docker-test").build()).getId();
        MemberSignUpRequest signup = new MemberSignUpRequest(
                "docker-flow@test.com", "Password1!", "01012345678", "테스터1");
        ResponseEntity<Long> signedUp = http.postForEntity("/api/members/signup", signup, Long.class);
        assertThat(signedUp.getStatusCode()).isEqualTo(CREATED);
        assertThat(signedUp.getBody()).isPositive();

        AuthLoginResponse login = http.postForObject("/api/auth/login",
                new MemberLoginRequest(signup.email(), signup.password()), AuthLoginResponse.class);
        assertThat(login).isNotNull();
        assertThat(login.status()).isEqualTo("SUCCESS");
        assertThat(login.accessToken()).isNotBlank();

        ResponseEntity<TokenResponse> reissued = http.postForEntity("/api/auth/reissue",
                new RefreshTokenRequest(login.refreshToken()), TokenResponse.class);
        assertThat(reissued.getStatusCode()).isEqualTo(OK);
        assertThat(reissued.getBody()).isNotNull();
        assertThat(reissued.getBody().refreshToken()).isNotEqualTo(login.refreshToken());
        ResponseEntity<String> replay = http.postForEntity("/api/auth/reissue",
                new RefreshTokenRequest(login.refreshToken()), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(UNAUTHORIZED);

        HttpHeaders authorization = new HttpHeaders();
        authorization.setBearerAuth(login.accessToken());
        ResponseEntity<Long> created = http.exchange("/api/posts", HttpMethod.POST,
                new HttpEntity<>(new PostCreateRequest("first post", "body", categoryId), authorization),
                Long.class);
        assertThat(created.getStatusCode()).isEqualTo(OK);
        Long postId = created.getBody();
        assertThat(postId).isNotNull();

        ResponseEntity<PostResponse> read = http.exchange("/api/posts/" + postId,
                HttpMethod.GET, new HttpEntity<>(authorization), PostResponse.class);
        assertThat(read.getStatusCode()).isEqualTo(OK);
        assertThat(read.getBody()).isNotNull();
        assertThat(read.getBody().viewCount()).isEqualTo(1);

        byte[] fileBytes = "<script>alert('test')</script>".getBytes(StandardCharsets.UTF_8);
        ByteArrayResource file = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return "sample.html";
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_HTML);
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("files", new HttpEntity<>(file, fileHeaders));
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setBearerAuth(login.accessToken());
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<AttachmentResponse[]> uploaded = http.exchange(
                "/api/posts/" + postId + "/attachments", HttpMethod.POST,
                new HttpEntity<>(parts, uploadHeaders), AttachmentResponse[].class);
        assertThat(uploaded.getStatusCode()).isEqualTo(OK);
        assertThat(uploaded.getBody()).hasSize(1);
        ResponseEntity<byte[]> downloaded = http.exchange(uploaded.getBody()[0].downloadUrl(),
                HttpMethod.GET, new HttpEntity<>(authorization), byte[].class);
        assertThat(downloaded.getStatusCode()).isEqualTo(OK);
        assertThat(downloaded.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(downloaded.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(downloaded.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(downloaded.getBody()).isEqualTo(fileBytes);

        ResponseEntity<Void> loggedOut = http.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(authorization), Void.class);
        assertThat(loggedOut.getStatusCode()).isEqualTo(OK);
        assertThat(redisTemplate.opsForValue().get(login.accessToken())).isEqualTo("logout");
        assertThat(redisTemplate.getExpire(login.accessToken(), TimeUnit.SECONDS)).isPositive();

        ResponseEntity<String> rejected = http.exchange("/api/posts/" + postId,
                HttpMethod.GET, new HttpEntity<>(authorization), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(posts.findById(postId).orElseThrow().getViewCount()).isEqualTo(1);

        Integer appliedV5 = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '5' AND success = 1
                """, Integer.class);
        assertThat(appliedV5).isEqualTo(1);
        Integer refreshTokenLength = jdbc.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'refresh_token'
                  AND COLUMN_NAME = 'token'
                """, Integer.class);
        assertThat(refreshTokenLength).isEqualTo(512);
        Integer attachmentUpdatedAt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'post_attachments'
                  AND COLUMN_NAME = 'updated_at'
                """, Integer.class);
        assertThat(attachmentUpdatedAt).isZero();
    }
}
