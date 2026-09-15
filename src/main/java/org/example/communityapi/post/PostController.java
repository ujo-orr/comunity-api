package org.example.communityapi.post;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.example.communityapi.post.dto.PostCreateRequest;
import org.example.communityapi.post.dto.PostResponse;
import org.example.communityapi.post.dto.PostUpdateRequest;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@Validated
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 게시글 작성 API
    public ResponseEntity<Long> createPost(
            @RequestBody PostCreateRequest request,
            Authentication authentication) {
                String email = authentication.getName();
        Long postId = postService.createPost(request, email);
        return ResponseEntity.ok(postId);
    }

    // 게시글 닉네임으로 조회 API
    @GetMapping("/{nickname}")
    public ResponseEntity<List<PostResponse>> getPostsByNickname(
            @NotBlank(message = "검색어를 입력해 주세요.")
            @PathVariable String nickname) {
        List<PostResponse> response = postService.getPostsByNickname(nickname);
        return ResponseEntity.ok(response);
    }

    // 3. 게시글 다건 조회 (제목 검색어 파라미터 옵션 지원)
    @GetMapping("/search")
    public ResponseEntity<List<PostResponse>> getPosts(
            @RequestParam(required = false) String title
    ) {
        List<PostResponse> response = postService.getPosts(title);
        return ResponseEntity.ok(response);
    }

    // 4. 게시글 수정
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updatePost(
            @PathVariable Long id,
            @RequestBody PostUpdateRequest request,
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

