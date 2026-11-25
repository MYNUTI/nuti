package com.example.nutriuniv.domain.category.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.category.dto.CategoryRequest;
import com.example.nutriuniv.domain.category.dto.CategoryTreeResponse;
import com.example.nutriuniv.domain.category.dto.CategoryUpdateRequest;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // ── GET /categories ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getCategoryTree() {
        // 숨긴 분류(「기타」)는 종류 목록에 보이지 않는다 (DATA_005)
        List<Category> all = categoryRepository.findAllByIsActiveTrue().stream().filter(c -> !c.isHidden()).toList();

        // 1패스: depth 역순으로 순회해서 leaf부터 노드 생성
        Map<Long, CategoryTreeResponse> nodeMap = new LinkedHashMap<>();
        all.stream()
                .sorted(Comparator.comparingInt(Category::getDepth).reversed()
                        .thenComparingInt(Category::getDisplayOrder))
                .forEach(c -> {
                    List<CategoryTreeResponse> children = all.stream()
                            .filter(child -> child.getParent() != null
                                    && child.getParent().getId().equals(c.getId()))
                            .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                            .map(child -> nodeMap.get(child.getId()))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    nodeMap.put(c.getId(), CategoryTreeResponse.builder()
                            .id(c.getId())
                            .name(c.getName())
                            .depth(c.getDepth())
                            .displayOrder(c.getDisplayOrder())
                            .children(children)
                            .build());
                });

        // 2패스: depth=1만 루트로 반환
        return all.stream()
                .filter(c -> c.getDepth() == 1)
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .map(c -> nodeMap.get(c.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── GET /categories/{categoryId}/brands ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBrandsByCategory(Long categoryId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 카테고리입니다."));

        return productRepository.findBrandCountByCategoryId(categoryId).stream()
                .map(row -> {
                    Brand brand = (Brand) row[0];
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", brand.getId());
                    item.put("name", brand.getName());
                    item.put("productCount", row[1]);
                    return item;
                })
                .collect(Collectors.toList());
    }

    // ── POST /admin/categories ────────────────────────────────────────────────────

    @Transactional
    public Long createCategory(CategoryRequest request) {

        if (request.getDepth() == null || request.getName() == null || request.getName().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "name과 depth는 필수입니다.");
        }
        if (request.getDepth() == 1 && request.getParentId() != null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "depth=1인 경우 parentId는 없어야 합니다.");
        }
        if (request.getDepth() > 1 && request.getParentId() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "depth가 2 이상인 경우 parentId는 필수입니다.");
        }

        Category parent = null;
        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 상위 카테고리입니다."));

            if (parent.getDepth() + 1 != request.getDepth()) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "depth와 parentId가 일치하지 않습니다.");
            }
        }

        // 같은 depth/parent 내 중복 카테고리명 확인
        if (categoryRepository.existsByNameAndDepthAndParentAndIsActiveTrue(
                request.getName(), request.getDepth(), parent)) {
            throw new CustomException(ErrorCode.DUPLICATE_RESOURCE, "이미 존재하는 카테고리명입니다.");
        }

        Category saved = categoryRepository.save(
                Category.create(request.getName(), request.getDepth(), parent, request.getDisplayOrder()));

        return saved.getId();
    }

    // ── PATCH /admin/categories/{categoryId} ──────────────────────────────────────

    @Transactional
    public void updateCategory(Long categoryId, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 카테고리입니다."));

        category.update(request.getName(), request.getDisplayOrder());
    }

    // ── DELETE /admin/categories/{categoryId} ─────────────────────────────────────

    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 카테고리입니다."));

        if (categoryRepository.existsByParentAndIsActiveTrue(category)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "하위 카테고리가 존재하여 삭제할 수 없습니다.");
        }
        if (productRepository.existsByCategoryIdAndIsActiveTrue(categoryId)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "해당 카테고리에 상품이 존재하여 삭제할 수 없습니다.");
        }

        category.deactivate();
    }
}