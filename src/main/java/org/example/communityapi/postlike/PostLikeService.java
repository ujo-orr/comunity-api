package org.example.communityapi.postlike;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.member.Member;
import org.example.communityapi.member.MemberRepository;
import org.example.communityapi.post.Post;
import org.example.communityapi.post.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void likePost(Long postId, String email) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PostLikeId postLikeId = new PostLikeId(member.getId(), post.getId());
        if (postLikeRepository.existsById(postLikeId)) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED_POST);
        }

        postLikeRepository.save(new PostLike(member, post));
    }

    @Transactional
    public void unlikePost(Long postId, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PostLikeId postLikeId = new PostLikeId(member.getId(), postId);
        PostLike postLike = postLikeRepository.findById(postLikeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_LIKE_NOT_FOUND));

        postLikeRepository.delete(postLike);
    }

    public long getLikeCount(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return postLikeRepository.countByPostId(postId);
    }
}
