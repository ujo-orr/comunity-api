package org.example.communityapi.schema;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@Tag("docker")
@Testcontainers
class MemberSchemaMigrationDockerTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withEnv("MYSQL_ROOT_HOST", "%");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    @DisplayName("V8의 기존 회원과 탈퇴 기록을 보존하면서 V9로 업그레이드한다")
    void upgradePreservesExistingDataAndAuditTimestamps() {
        var dataSource = versionEightDatabase();
        var jdbc = new JdbcTemplate(dataSource);
        insertMember(jdbc, "existing@test.com", "01012345678", "SUPERADMIN");
        jdbc.update("""
                INSERT INTO member_withdrawal
                    (email, password, nickname, phone_number, role, deleted_at, expire_at)
                VALUES ('archived-email-longer-than-thirty-characters@test.com', 'encoded', 'archived',
                        NULL, 'SUPERADMIN', '2026-01-01 00:00:00', '2026-01-31 00:00:00')
                """);
        var memberBefore = jdbc.queryForMap("SELECT * FROM members");
        var withdrawalBefore = jdbc.queryForMap("SELECT * FROM member_withdrawal");

        assertThat(Flyway.configure().dataSource(dataSource).load().migrate().migrationsExecuted).isEqualTo(1);

        assertThat(jdbc.queryForMap("SELECT * FROM members")).isEqualTo(memberBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM member_withdrawal")).isEqualTo(withdrawalBefore);
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicatePhone", "longEmail", "invalidRole", "invalidStatus", "invalidWithdrawalRole"})
    @DisplayName("기존 데이터가 새 제약조건과 충돌하면 V9는 데이터를 변경하거나 잘라내지 않고 중단한다")
    void conflictingDataStopsMigrationWithoutChangingBusinessTables(String conflict) {
        var dataSource = versionEightDatabase();
        var jdbc = new JdbcTemplate(dataSource);
        insertMember(jdbc, "existing@test.com", "01012345678", "USER");
        switch (conflict) {
            case "duplicatePhone" -> insertMember(jdbc, "second@test.com", "01012345678", "USER");
            case "longEmail" -> jdbc.update("UPDATE members SET email = ?", "a".repeat(22) + "@test.com");
            case "invalidRole" -> jdbc.update("UPDATE members SET role = 'admin'");
            case "invalidStatus" -> jdbc.update("UPDATE members SET status = 'UNKNOWN'");
            case "invalidWithdrawalRole" -> jdbc.update("""
                    INSERT INTO member_withdrawal (email, password, nickname, role, deleted_at, expire_at)
                    VALUES ('archived@test.com', 'encoded', 'archived', 'OWNER', NOW(6), NOW(6))
                    """);
            default -> throw new IllegalArgumentException(conflict);
        }
        var membersBefore = jdbc.queryForList("SELECT * FROM members ORDER BY id");
        var withdrawalsBefore = jdbc.queryForList("SELECT * FROM member_withdrawal ORDER BY id");
        var memberDdlBefore = jdbc.queryForMap("SHOW CREATE TABLE members");
        var withdrawalDdlBefore = jdbc.queryForMap("SHOW CREATE TABLE member_withdrawal");

        // 비엄격 MySQL 설정에서도 사전 검사에 의해 긴 이메일을 잘라내지 않고 거부해야 한다.
        var flyway = Flyway.configure().dataSource(dataSource).initSql("SET SESSION sql_mode = ''").load();
        assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("v9_requires_valid_member_data");

        assertThat(jdbc.queryForList("SELECT * FROM members ORDER BY id")).isEqualTo(membersBefore);
        assertThat(jdbc.queryForList("SELECT * FROM member_withdrawal ORDER BY id")).isEqualTo(withdrawalsBefore);
        assertThat(jdbc.queryForMap("SHOW CREATE TABLE members")).isEqualTo(memberDdlBefore);
        assertThat(jdbc.queryForMap("SHOW CREATE TABLE member_withdrawal")).isEqualTo(withdrawalDdlBefore);
    }

    private DriverManagerDataSource versionEightDatabase() {
        var root = new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword());
        String database = "migration_case_" + SEQUENCE.incrementAndGet();
        new JdbcTemplate(root).execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        var dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + database), "root", mysql.getPassword());
        assertThat(Flyway.configure().dataSource(dataSource).target("8").load().migrate().migrationsExecuted).isEqualTo(8);
        return dataSource;
    }

    private void insertMember(JdbcTemplate jdbc, String email, String phone, String role) {
        jdbc.update("""
                INSERT INTO members (email, password, nickname, phone_number, role, created_at, updated_at)
                VALUES (?, 'encoded', ?, ?, ?, '2020-01-01 00:00:00', '2020-01-02 00:00:00')
                """, email, email.substring(0, email.indexOf('@')), phone, role);
    }
}
