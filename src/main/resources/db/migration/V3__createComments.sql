CREATE TABLE comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),

    CONSTRAINT fk_comments_post
        FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_member
        FOREIGN KEY (member_id) REFERENCES members(id)
);
