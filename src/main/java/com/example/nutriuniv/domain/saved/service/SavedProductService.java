package com.example.nutriuniv.domain.saved.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.pns.service.GradeLabel;
import com.example.nutriuniv.domain.pns.service.PnsLookupService;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.saved.dto.SaveResultResponse;
import com.example.nutriuniv.domain.saved.dto.SavedProductPageResponse;
import com.example.nutriuniv.domain.saved.entity.SavedProduct;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 제품 저장 (기능명세서 7.1) — 구 LikeService 개조.
 * 바뀐 규칙: 익명 소유 지원(user XOR anonymous), 재저장·미존재 해제 모두 200 멱등, 상한 200(409), 동의 전 403 CONSENT_REQUIRED.
 */
@Service
@RequiredArgsConstructor
public class SavedProductService {

    public static final int MAX_SAVED = 200;

    private final SavedProductRepository savedProductRepository;
    private final ProductRepository productRepository;
    private final OwnerResolver ownerResolver;
    private final PnsLookupService pnsLookupService;

    // ── GET /me/saved-products ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SavedProductPageResponse getSaved(Actor actor, int page, int size) {
        if (page < 1 || size < 1) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page, size는 1 이상이어야 합니다.");
        }
        Owner owner = ownerResolver.resolve(actor);

        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SavedProduct> saved = owner.isUser()
                ? savedProductRepository.findByUserIdAndProductIsActiveTrue(owner.userId(), pageable)
                : savedProductRepository.findByAnonymousIdAndProductIsActiveTrue(owner.anonymousId(), pageable);

        // 목표에 맞는 등급 일괄 조회 (N+1 방지)
        GoalType goal = pnsLookupService.resolveGoalType(owner);
        List<Long> productIds = saved.getContent().stream().map(s -> s.getProduct().getId()).toList();
        Map<Long, String> gradeMap = pnsLookupService.lookupGrades(productIds, PnsLookupService.DEFAULT_EER_BAND, goal.pnsGoal());

        List<SavedProductPageResponse.Item> items = saved.getContent().stream()
                .map(s -> {
                    Product p = s.getProduct();
                    String grade = gradeMap.get(p.getId());
                    return SavedProductPageResponse.Item.builder()
                            .productId(p.getId())
                            .name(p.getName())
                            .imageUrl(p.getImageUrl())
                            // 제품 상태 컬럼(ANALYZED/INSUFFICIENT)은 핵심 루프 브랜치에서 도입 — 그때까지 등급 유무로 대신한다
                            .status(grade != null ? "ANALYZED" : "INSUFFICIENT")
                            .grade(grade)
                            .label(GradeLabel.of(grade))
                            .savedAt(s.getCreatedAt())
                            .build();
                })
                .toList();

        return SavedProductPageResponse.builder()
                .items(items)
                .totalCount(saved.getTotalElements())
                .hasNext(saved.hasNext())
                .build();
    }

    // ── POST /me/saved-products/{productId} ──────────────────────────────────────────

    @Transactional
    public SaveResultResponse save(Actor actor, Long productId) {
        Owner owner = ownerResolver.resolve(actor);                        // 동의 전 → 403, 클라가 동의 후 이어감
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (find(owner, productId).isPresent()) {
            return SaveResultResponse.saved(count(owner));                 // 재저장 200 멱등
        }
        long current = count(owner);
        if (current >= MAX_SAVED) {
            throw new CustomException(ErrorCode.SAVE_LIMIT_EXCEEDED);
        }
        savedProductRepository.save(SavedProduct.create(owner, product));
        return SaveResultResponse.saved(current + 1);
    }

    // ── DELETE /me/saved-products/{productId} ────────────────────────────────────────

    @Transactional
    public SaveResultResponse remove(Actor actor, Long productId) {
        Owner owner = ownerResolver.resolve(actor);
        find(owner, productId).ifPresent(savedProductRepository::delete);   // 없는 것 지워도 200 멱등
        return SaveResultResponse.removed();
    }

    // ── 다른 도메인용 (상품 상세 saved 표시 등) ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isSaved(Owner owner, Long productId) {
        if (owner == null) {
            return false;
        }
        return owner.isUser()
                ? savedProductRepository.existsByUserIdAndProductIdAndProductIsActiveTrue(owner.userId(), productId)
                : savedProductRepository.existsByAnonymousIdAndProductIdAndProductIsActiveTrue(owner.anonymousId(), productId);
    }

    @Transactional(readOnly = true)
    public long count(Owner owner) {
        return owner.isUser()
                ? savedProductRepository.countByUserId(owner.userId())
                : savedProductRepository.countByAnonymousId(owner.anonymousId());
    }

    private Optional<SavedProduct> find(Owner owner, Long productId) {
        return owner.isUser()
                ? savedProductRepository.findByUserIdAndProductId(owner.userId(), productId)
                : savedProductRepository.findByAnonymousIdAndProductId(owner.anonymousId(), productId);
    }
}
