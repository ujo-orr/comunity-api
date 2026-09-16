package org.example.communityapi.post;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.communityapi.category.Category;
import org.example.communityapi.global.entity.BaseTimeEntity;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.member.Member;
import org.hibernate.annotations.DynamicUpdate;

@DynamicUpdate
@Entity
@Table(name = "POSTS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int viewCount = 0;

    @Builder
    public Post(Member member, Category category, String title, String content) {
        this.member = member;
        this.category = category;
        this.title = title;
        this.content = content;
        this.viewCount = 0;
    }

    // 게시글 수정 비즈니스 메서드
    public void updatePost(
            String title,
            String content,
            Category category
    ) {
        this.title = title;
        this.content = content;
        this.category = category;
    }

    // 조회수 증가 비즈니스 메서드
    public void increaseViewCount() {
        this.viewCount++;
    }


    public void validateWriter(String email) {
        if (!member.getEmail().equals(email)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}