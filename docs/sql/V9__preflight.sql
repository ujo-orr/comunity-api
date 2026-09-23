-- V9 적용 전 데이터 확인용 조회. 첫 4개 조회 결과가 모두 0행이면 적용 가능
-- 전화번호 중복 확인 시 DB의 UNIQUE 제약조건과 같은 비교 기준 사용
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

-- 아래 결과는 V9 적용 가능 여부와 무관. 탈퇴 계정 복구 시 별도 확인 필요
SELECT id, email, phone_number
FROM member_withdrawal
WHERE CHAR_LENGTH(email) > 30 OR phone_number IS NULL;
