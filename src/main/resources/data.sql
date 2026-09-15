-- 1. 기본 카테고리 데이터 (id: 1, 2, 3 자동 생성)
INSERT INTO categories (name, created_at, updated_at)
VALUES ('일상생활', NOW(), NOW());

INSERT INTO categories (name, created_at, updated_at)
VALUES ('요리', NOW(), NOW());

INSERT INTO categories (name, created_at, updated_at)
VALUES ('경제', NOW(), NOW());


-- 기본 회원 데이터 Password123!
INSERT INTO members (email, password, nickname, phone_number, role, status, created_at, updated_at)
VALUES ('test@example.com', '$2a$12$lPuUjdhho6r/XaOI7F/ozOV4RPV9YU2ptrxnB3dm2BaouiWLp1N/u', 'abc', '01000000002', 'USER', 'ACTIVE', NOW(), NOW());

-- 기본 게시글 데이터
INSERT INTO posts (id, member_id, category_id, title, content, view_count, created_at, updated_at)
VALUES (1, 1, 1, '첫 게시글', 'abcdefgㄱㄴㄷㄹㅁㅂㅅ', 0, NOW(), NOW());
