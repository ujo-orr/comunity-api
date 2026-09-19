package org.example.communityapi.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.communityapi.post.dto.PostCreateRequest;
import org.example.communityapi.post.dto.PostResponse;
import org.example.communityapi.post.dto.PostUpdateRequest;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.example.communityapi.global.dto.PageResponse;
import org.example.communityapi.global.dto.PageRequestParams;

@RestController
@RequestMapping("/api/posts")
@Validated
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<Long> createPost(
            @Valid @RequestBody PostCreateRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        Long postId = postService.createPost(request, email);
        return ResponseEntity.ok(postId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@Positive @PathVariable Long id) {
        return ResponseEntity.ok(postService.getPost(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PostResponse>> getPosts(
            @Size(max = 250, message = "검색어는 250자 이하로 입력해주세요.")
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(
                postService.getPosts(keyword, PageRequestParams.of(page, size))));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updatePost(
            @Positive @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        postService.updatePost(id, request, email);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @Positive @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        postService.deletePost(id, email);
        return ResponseEntity.ok().build();
    }
}
