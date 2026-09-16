package org.example.communityapi.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    // 게시글 작성 API
    @PostMapping
    public ResponseEntity<Long> createPost(
            @Valid @RequestBody PostCreateRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        Long postId = postService.createPost(request, email);
        return ResponseEntity.ok(postId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPost(id));
    }

    @GetMapping("/by-nickname/{nickname}")
    public ResponseEntity<PageResponse<PostResponse>> getPostsByNickname(
            @NotBlank(message = "검색어를 입력해 주세요.") @PathVariable String nickname,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(
                postService.getPostsByNickname(nickname, PageRequestParams.of(page, size))));
    }

    @GetMapping({"", "/search"})
    public ResponseEntity<PageResponse<PostResponse>> getPosts(
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(
                postService.getPosts(title, PageRequestParams.of(page, size))));
    }

    // 4. 게시글 수정
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        postService.updatePost(id, request, email);
        return ResponseEntity.ok().build();
    }

    // 5. 게시글 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        postService.deletePost(id, email);
        return ResponseEntity.ok().build();
    }
}
