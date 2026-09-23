-- 적용 전 docs/sql/V9__preflight.sql로 중복값과 잘못된 데이터 확인
-- 문제 발견 시 기존 데이터 수정 없이 테이블 변경 중단
CREATE TEMPORARY TABLE v9_member_schema_preflight (
    valid TINYINT NOT NULL,
    CONSTRAINT v9_requires_valid_member_data CHECK (valid = 1)
);

INSERT INTO v9_member_schema_preflight (valid)
SELECT CASE WHEN
    EXISTS (SELECT 1 FROM members WHERE CHAR_LENGTH(email) > 30)
    OR EXISTS (SELECT phone_number FROM members GROUP BY phone_number HAVING COUNT(*) > 1)
    OR EXISTS (SELECT 1 FROM members
               WHERE CAST(role AS BINARY) NOT IN ('USER', 'ADMIN', 'SUPERADMIN')
                  OR CAST(status AS BINARY) NOT IN ('ACTIVE', 'BANNED'))
    OR EXISTS (SELECT 1 FROM member_withdrawal
               WHERE CAST(role AS BINARY) NOT IN ('USER', 'ADMIN', 'SUPERADMIN'))
    THEN 0 ELSE 1 END;

DROP TEMPORARY TABLE v9_member_schema_preflight;

-- 이메일 길이 변경 시 데이터 잘림 방지. 변경 성공 후 기존 설정 복원
SET @v9_previous_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'STRICT_ALL_TABLES');

ALTER TABLE members
    MODIFY COLUMN email VARCHAR(30) NOT NULL,
    ADD CONSTRAINT uk_members_phone_number UNIQUE (phone_number),
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT chk_members_role CHECK (CAST(role AS BINARY) IN ('USER', 'ADMIN', 'SUPERADMIN')),
    ADD CONSTRAINT chk_members_status CHECK (CAST(status AS BINARY) IN ('ACTIVE', 'BANNED'));

ALTER TABLE member_withdrawal
    ADD CONSTRAINT chk_member_withdrawal_role CHECK (CAST(role AS BINARY) IN ('USER', 'ADMIN', 'SUPERADMIN'));

SET SESSION sql_mode = @v9_previous_sql_mode;
