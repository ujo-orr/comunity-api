package org.example.communityapi.post;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.attachment.AttachmentStorage;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final PostAttachmentRepository attachmentRepository;
    private final AttachmentStorage fileStorageService;

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

    @Transactional
    public PostResponse getPost(Long id) {
        if (postRepository.incrementViewCount(id) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return postRepository.findResponseById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    public Page<PostResponse> getPosts(String keyword, Pageable pageable) {
        if (keyword == null) {
            return postRepository.findAllPosts(pageable);
        }
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(ErrorCode.EMPTY_SEARCH_KEYWORD);
        }

        // LIKE의 특수문자도 사용자가 입력한 문자 그대로 검색한다.
        String escapedKeyword = keyword.strip()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return postRepository.searchPosts(escapedKeyword, pageable);
    }

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

    @Transactional
    public void deletePost(Long id, String email) {

        Post post = postRepository.findByIdWithMember(id)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.POST_NOT_FOUND));

        post.validateWriter(email);

        // DB 삭제가 끝난 뒤 파일을 지워야 롤백돼도 첨부파일이 남는다.
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
