package org.example.communityapi.comment;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.comment.dto.CommentCreateRequest;
import org.example.communityapi.comment.dto.CommentResponse;
import org.example.communityapi.comment.dto.CommentUpdateRequest;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.post.Post;
import org.example.communityapi.post.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Long createComment(Long postId, CommentCreateRequest request, String email) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Comment comment = Comment.builder()
                .post(post)
                .member(member)
                .content(request.content())
                .build();

        return commentRepository.save(comment).getId();
    }

    public Page<CommentResponse> getComments(Long postId, Pageable pageable) {
        if (!postRepository.existsById(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return commentRepository.findResponsesByPostId(postId, pageable);
    }

    @Transactional
    public void updateComment(Long commentId, CommentUpdateRequest request, String email) {
        Comment comment = commentRepository.findByIdWithMember(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        comment.validateWriter(email);
        comment.updateContent(request.content());
    }

    @Transactional
    public void deleteComment(Long commentId, String email) {
        Comment comment = commentRepository.findByIdWithMember(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        comment.validateWriter(email);
        commentRepository.delete(comment);
    }
}
