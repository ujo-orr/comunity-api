package org.example.communityapi.comment;

import org.example.communityapi.comment.dto.CommentResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("""
    SELECT new org.example.communityapi.comment.dto.CommentResponse(
    c.id, c.content, m.nickname, c.createdAt, c.updatedAt)
    FROM Comment c
    JOIN c.member m
    WHERE c.post.id = :postId
    ORDER BY c.createdAt ASC, c.id ASC
    """)
    Page<CommentResponse> findResponsesByPostId(@Param("postId") Long postId, Pageable pageable);

    @Query("""
SELECT c
FROM Comment c
JOIN FETCH c.member
WHERE c.id = :id
""")
    Optional<Comment> findByIdWithMember(@Param("id") Long id);
}
