package org.example.communityapi.post;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.attachment.FileStorageService;
import org.example.communityapi.attachment.PostAttachmentRepository;
import org.example.communityapi.category.Category;
import org.example.communityapi.category.CategoryRepository;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.post.dto.PostCreateRequest;
import org.example.communityapi.post.dto.PostResponse;
import org.example.communityapi.post.dto.PostUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final PostAttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;

    // 게시글 작성
    @Transactional
    public Long createPost(PostCreateRequest request, String userEmail) {

        Member member = memberRepository.findByEmail(userEmail)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Post post = Post.builder()
                .title(request.title())
                .content(request.content())
                .member(member)
                .category(category)
                .build();

        return postRepository.save(post).getId();
    }

    // 특정 회원의 게시글 조회
    public List<PostResponse> getPostsByNickname(String nickname) {

        if (!memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return postRepository.findByMemberNickname(nickname);
    }

    // 게시글 검색
    public List<PostResponse> getPosts(String title) {

        if (StringUtils.hasText(title)) {
            return postRepository.findByTitle(title);
        }

        return postRepository.findAllPosts();
    }

    // 게시글 수정
    @Transactional
    public void updatePost(
            Long id,
            PostUpdateRequest request,
            String email
    ) {
        Post post = postRepository.findByIdWithMember(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        post.validateWriter(email);

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        post.updatePost(
                request.title(),
                request.content(),
                category
        );
    }

    // 게시글 삭제
    @Transactional
    public void deletePost(Long id, String email) {

        Post post = postRepository.findByIdWithMember(id)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.POST_NOT_FOUND));

        post.validateWriter(email);

        // FK cascade는 첨부파일 메타데이터를 지우고, 실제 파일은 커밋 뒤에 제거한다.
        // 롤백된 게시글 삭제 때문에 파일만 먼저 사라지는 일을 막는다.
        var storageKeys = attachmentRepository.findByPostId(id).stream()
                .map(attachment -> attachment.getStorageKey())
                .toList();
        postRepository.delete(post);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageKeys.forEach(fileStorageService::deleteQuietly);
            }
        });
    }
}
