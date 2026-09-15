package org.example.communityapi.comment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.communityapi.comment.dto.CommentCreateRequest;
import org.example.communityapi.comment.dto.CommentResponse;
import org.example.communityapi.comment.dto.CommentUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<Long> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(commentService.createComment(postId, request, authentication.getName()));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getComments(postId));
    }

    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<Void> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            Authentication authentication) {
        commentService.updateComment(commentId, request, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long commentId,
            Authentication authentication) {
        commentService.deleteComment(commentId, authentication.getName());
        return ResponseEntity.ok().build();
    }
}
