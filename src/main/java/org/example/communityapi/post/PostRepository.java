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

    // SQL의 원자적 증가 연산으로 동시 조회 시 증가분 유실을 방지한다.
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

    // 게시글 작성자 검증이 필요한 수정/삭제 흐름에서 사용
    // LAZY 연관관계를 유지하면서도 작성자 조회로 인한 추가 쿼리를 방지
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
        ORDER BY p.createdAt DESC, p.id DESC
        """)
    Page<PostResponse> findByMemberNickname(
            @Param("nickname") String nickname, Pageable pageable
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
    ORDER BY p.createdAt DESC, p.id DESC
    """)
    Page<PostResponse> findByTitle(
            @Param("title") String title, Pageable pageable
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
    ORDER BY p.createdAt DESC, p.id DESC
    """)
    Page<PostResponse> findAllPosts(Pageable pageable);
}
