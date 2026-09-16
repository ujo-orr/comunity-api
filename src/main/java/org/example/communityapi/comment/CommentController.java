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

import org.example.communityapi.global.dto.PageResponse;
import org.example.communityapi.global.dto.PageRequestParams;
import org.springframework.web.bind.annotation.RequestParam;

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
    public ResponseEntity<PageResponse<CommentResponse>> getComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(
                commentService.getComments(postId, PageRequestParams.of(page, size))));
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
