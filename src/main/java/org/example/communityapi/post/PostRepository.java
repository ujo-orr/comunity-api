package org.example.communityapi.post;

import org.example.communityapi.post.dto.PostResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    boolean existsByCategoryId(Long categoryId);

    /**
     * 게시글 작성자 검증이 필요한 수정/삭제 흐름에서 사용한다.
     * LAZY 연관관계를 유지하면서도 작성자 조회로 인한 추가 쿼리를 방지한다.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.member WHERE p.id = :id")
    java.util.Optional<Post> findByIdWithMember(@Param("id") Long id);

    // 닉네임으로 게시글 다건 조회
    @Query("""
        SELECT new org.example.communityapi.post.dto.PostResponse(
            p.id,
            p.title,
            p.content,
            c.name,
            m.nickname,
            p.viewCount,
            p.createdAt,
            p.updatedAt
        )
        FROM Post p
        JOIN p.member m
        JOIN p.category c
        WHERE m.nickname = :nickname
        ORDER BY p.createdAt DESC
        """)
    List<PostResponse> findByMemberNickname(
            @Param("nickname") String nickname
    );

    // 제목 키워드로 게시글 다건 조회
    @Query("""
    SELECT new org.example.communityapi.post.dto.PostResponse(
        p.id,
        p.title,
        p.content,
        c.name,
        m.nickname,
        p.viewCount,
        p.createdAt,
        p.updatedAt
    )
    FROM Post p
    JOIN p.member m
    JOIN p.category c
    WHERE p.title LIKE CONCAT('%', :title, '%')
    ORDER BY p.createdAt DESC
    """)
    List<PostResponse> findByTitle(
            @Param("title") String title
    );

    // 전체 게시글 조회
    @Query("""
    SELECT new org.example.communityapi.post.dto.PostResponse(
        p.id,
        p.title,
        p.content,
        c.name,
        m.nickname,
        p.viewCount,
        p.createdAt,
        p.updatedAt
    )
    FROM Post p
    JOIN p.member m
    JOIN p.category c
    ORDER BY p.createdAt DESC
    """)
    List<PostResponse> findAllPosts();
}
