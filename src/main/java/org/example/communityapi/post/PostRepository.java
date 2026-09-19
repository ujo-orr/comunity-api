package org.example.communityapi.post;

import org.example.communityapi.post.dto.PostResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 여러 명이 동시에 조회해도 조회수가 빠지지 않게 DB에서 바로 올린다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Post p
        SET p.viewCount = p.viewCount + 1
        WHERE p.id = :id
        """)
    int incrementViewCount(@Param("id") Long id);

    @Query("""
        SELECT new org.example.communityapi.post.dto.PostResponse(
            p.id, p.title, p.content, c.name, m.nickname,
            p.viewCount, p.createdAt, p.updatedAt)
        FROM Post p JOIN p.member m JOIN p.category c
        WHERE p.id = :id
        """)
    Optional<PostResponse> findResponseById(@Param("id") Long id);

    boolean existsByCategoryId(Long categoryId);

    // 작성자를 확인할 때 쿼리가 한 번 더 나가지 않도록 함께 조회한다.
    @Query("SELECT p FROM Post p JOIN FETCH p.member WHERE p.id = :id")
    java.util.Optional<Post> findByIdWithMember(@Param("id") Long id);

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
    WHERE p.title LIKE CONCAT('%', :keyword, '%') ESCAPE '!'
       OR p.content LIKE CONCAT('%', :keyword, '%') ESCAPE '!'
       OR m.nickname LIKE CONCAT('%', :keyword, '%') ESCAPE '!'
    ORDER BY p.createdAt DESC, p.id DESC
    """)
    Page<PostResponse> searchPosts(
            @Param("keyword") String keyword, Pageable pageable
    );

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
    ORDER BY p.createdAt DESC, p.id DESC
    """)
    Page<PostResponse> findAllPosts(Pageable pageable);
}
