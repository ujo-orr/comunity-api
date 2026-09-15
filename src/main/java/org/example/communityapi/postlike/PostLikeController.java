package org.example.communityapi.postlike;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PostLikeController {

    private final PostLikeService postLikeService;

    @PostMapping("/api/posts/{postId}/likes")
    public ResponseEntity<Void> likePost(
            @PathVariable Long postId,
            Authentication authentication) {
        postLikeService.likePost(postId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/posts/{postId}/likes")
    public ResponseEntity<Void> unlikePost(
            @PathVariable Long postId,
            Authentication authentication) {
        postLikeService.unlikePost(postId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/posts/{postId}/likes/count")
    public ResponseEntity<Long> getLikeCount(@PathVariable Long postId) {
        return ResponseEntity.ok(postLikeService.getLikeCount(postId));
    }
}
