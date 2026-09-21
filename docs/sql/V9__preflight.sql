-- 읽기 전용 사전 점검. 첫 네 조회의 결과가 0행이어야 V9를 적용할 수 있다.
-- 전화번호 비교는 UNIQUE 인덱스와 동일한 DB collation을 사용한다.
SELECT phone_number, COUNT(*) AS duplicate_count
FROM members
GROUP BY phone_number
HAVING COUNT(*) > 1;

SELECT id, email, CHAR_LENGTH(email) AS email_length
FROM members
WHERE CHAR_LENGTH(email) > 30;

SELECT id, role, status
FROM members
WHERE CAST(role AS BINARY) NOT IN ('USER', 'ADMIN', 'SUPERADMIN')
   OR CAST(status AS BINARY) NOT IN ('ACTIVE', 'BANNED');

SELECT id, role
FROM member_withdrawal
WHERE CAST(role AS BINARY) NOT IN ('USER', 'ADMIN', 'SUPERADMIN');

-- 아래 결과는 V9 적용을 막지는 않지만 과거 탈퇴 계정 복구 시 확인이 필요하다.
SELECT id, email, phone_number
FROM member_withdrawal
WHERE CHAR_LENGTH(email) > 30 OR phone_number IS NULL;
