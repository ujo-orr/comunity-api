package org.example.communityapi.category;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.communityapi.category.dto.CategoryCreateRequest;
import org.example.communityapi.category.dto.CategoryResponse;
import org.example.communityapi.category.dto.CategoryUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController{

    private final CategoryService categoryService;
    // 카테고리 추가
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> createCategory(
            @Valid
            @RequestBody CategoryCreateRequest request) {
        Long categoryId = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryId);
    }

    // 카테고리 삭제
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // 카테고리 수정
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateCategory(
            @PathVariable Long id,
            @Valid
            @RequestBody CategoryUpdateRequest request) {
        categoryService.updateCategory(id, request);
        return ResponseEntity.ok().build();
    }

    // 카테고리 조회
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        List<CategoryResponse> responses = categoryService.getAllCategories();
        return ResponseEntity.ok(responses);
    }
}