package org.example.communityapi.category;

import lombok.RequiredArgsConstructor;
import org.example.communityapi.category.dto.CategoryCreateRequest;
import org.example.communityapi.category.dto.CategoryResponse;
import org.example.communityapi.category.dto.CategoryUpdateRequest;
import org.example.communityapi.global.error.BusinessException;
import org.example.communityapi.global.error.ErrorCode;
import org.example.communityapi.post.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;

    @Transactional
    public Long createCategory(CategoryCreateRequest request){
        if (categoryRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }
        Category category = Category.builder()
                .name(request.name())
                .build();

        return categoryRepository.save(category).getId();
    }

    @Transactional
    public void updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!category.getName().equals(request.name()) && categoryRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }

        category.updateCategory(request.name());
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id).orElseThrow(
                () -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (postRepository.existsByCategoryId(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
    categoryRepository.delete(category);
    }
}
