package org.example.communityapi.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@Tag("docker")
@Testcontainers
@SpringBootTest(properties = {
        "spring.sql.init.mode=never", "spring.batch.job.enabled=false",
        "spring.flyway.enabled=true", "spring.jpa.defer-datasource-initialization=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class MemberSchemaDockerTest extends MemberSchemaContract {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Test
    @DisplayName("빈 MySQL에 V1부터 V9까지 적용하면 회원과 탈퇴 기록의 컬럼 정의가 최종 설계와 일치한다")
    void migratedColumnsMatchFinalSchema() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
        assertColumn("members", "email", "varchar(30)", "NO");
        assertColumn("members", "phone_number", "varchar(20)", "NO");
        assertColumn("members", "role", "varchar(20)", "NO");
        assertColumn("members", "status", "varchar(20)", "NO");
        for (String column : List.of("created_at", "updated_at")) {
            Map<String, Object> metadata = assertColumn("members", column, "datetime(6)", "NO");
            assertThat(metadata.get("COLUMN_DEFAULT")).isNull();
            assertThat(metadata.get("EXTRA").toString()).doesNotContain("on update");
        }
        for (String column : List.of("email", "password")) {
            assertColumn("member_withdrawal", column, "varchar(255)", "NO");
        }
        for (String column : List.of("nickname", "role")) {
            assertColumn("member_withdrawal", column, "varchar(20)", "NO");
        }
        assertColumn("member_withdrawal", "phone_number", "varchar(20)", "YES");
        assertColumn("member_withdrawal", "deleted_at", "datetime(6)", "NO");
        assertColumn("member_withdrawal", "expire_at", "datetime(6)", "NO");
        assertColumn("member_withdrawal", "original_id", "bigint", "YES");
        for (String column : List.of("original_created_at", "created_at", "updated_at")) {
            assertColumn("member_withdrawal", column, "datetime(6)", "YES");
        }
        Map<String, Object> likeTime = assertColumn("post_likes", "created_at", "datetime(6)", "NO");
        assertThat(likeTime.get("COLUMN_DEFAULT").toString()).isEqualToIgnoringCase("CURRENT_TIMESTAMP(6)");
        assertColumn("refresh_token", "token", "varchar(512)", "NO");
    }

    @Test
    @DisplayName("MySQL은 전화번호 유일 제약과 좋아요 복합키 및 기존 외래키 삭제 정책을 유지한다")
    void keysAndDeleteRulesMatchEntityRelationships() {
        assertThat(jdbc.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'members'
                  AND INDEX_NAME = 'uk_members_phone_number' AND NON_UNIQUE = 0
                """, String.class)).containsExactly("phone_number");
        assertThat(jdbc.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_likes' AND CONSTRAINT_NAME = 'PRIMARY'
                ORDER BY ORDINAL_POSITION
                """, String.class)).containsExactly("member_id", "post_id");
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(k.TABLE_NAME, '.', k.COLUMN_NAME, '->', k.REFERENCED_TABLE_NAME,
                              '.', k.REFERENCED_COLUMN_NAME, ':', r.DELETE_RULE)
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS r
                  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                  AND r.TABLE_NAME = k.TABLE_NAME
                WHERE k.TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME IN ('members', 'posts', 'categories')
                """, String.class)).containsExactlyInAnyOrder(
                "posts.member_id->members.id:NO ACTION", "posts.category_id->categories.id:NO ACTION",
                "comments.member_id->members.id:NO ACTION", "comments.post_id->posts.id:CASCADE",
                "post_likes.member_id->members.id:CASCADE", "post_likes.post_id->posts.id:CASCADE",
                "post_attachments.post_id->posts.id:CASCADE");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('member_withdrawal', 'refresh_token')
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForList("""
                SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK'
                  AND TABLE_NAME IN ('members', 'member_withdrawal') AND ENFORCED = 'YES'
                """, String.class)).containsExactlyInAnyOrder(
                "chk_members_role", "chk_members_status", "chk_member_withdrawal_role");
    }

    @Test
    @DisplayName("좋아요 생성 시 MySQL이 생성 시각을 기록하고 회원 수정 시각은 DB가 자동 변경하지 않는다")
    void databaseOwnsLikeTimeButDoesNotOverwriteMemberAuditTime() {
        createLikeAndVerifyViewTimestamp();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_likes WHERE created_at IS NULL", Integer.class)).isZero();
        var member = signup();
        var before = jdbc.queryForObject("SELECT updated_at FROM members WHERE id = ?", java.sql.Timestamp.class, member.getId());
        jdbc.update("UPDATE members SET nickname = ? WHERE id = ?", "dbchange", member.getId());
        assertThat(jdbc.queryForObject("SELECT updated_at FROM members WHERE id = ?", java.sql.Timestamp.class, member.getId()))
                .isEqualTo(before);
    }

    private Map<String, Object> assertColumn(String table, String column, String type, String nullable) {
        var metadata = jdbc.queryForMap("""
                SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, table, column);
        assertThat(metadata.get("COLUMN_TYPE")).as(table + "." + column).isEqualTo(type);
        assertThat(metadata.get("IS_NULLABLE")).as(table + "." + column).isEqualTo(nullable);
        return metadata;
    }
}
