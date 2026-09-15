package org.example.communityapi.post;

import org.example.communityapi.post.dto.PostResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    boolean existsByCategoryId(Long categoryId);

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
    """)
    List<PostResponse> findAllPosts();
}
