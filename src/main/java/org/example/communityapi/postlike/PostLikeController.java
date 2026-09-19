package org.example.communityapi.postlike;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostLikeController {

    private final PostLikeService postLikeService;

    @PostMapping("/{postId}/likes")
    public ResponseEntity<Void> likePost(
            @Positive @PathVariable Long postId,
            Authentication authentication) {
        postLikeService.likePost(postId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<Void> unlikePost(
            @Positive @PathVariable Long postId,
            Authentication authentication) {
        postLikeService.unlikePost(postId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{postId}/likes/count")
    public ResponseEntity<Long> getLikeCount(@Positive @PathVariable Long postId) {
        return ResponseEntity.ok(postLikeService.getLikeCount(postId));
    }
}
