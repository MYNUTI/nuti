package com.example.nutriuniv.domain.saved.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.example.nutriuniv.domain.product.service.ProductCardService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 제품 저장 (기능명세서 7.1) — 구 LikeService 개조.
 * 바뀐 규칙: 익명 소유 지원(user XOR anonymous), 재저장·미존재 해제 모두 200 멱등, 상한 200(409), 기기 ID 없음 400 ANONYMOUS_ID_REQUIRED(동의는 필요 없음).
 */
@Service
@RequiredArgsConstructor
public class SavedProductService {

    public static final int MAX_SAVED = 200;

    private final SavedProductRepository savedProductRepository;
    private final ProductRepository productRepository;
    private final OwnerResolver ownerResolver;
    private final ProductCardService cardService;

    // ── GET /me/saved-products ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SavedProductPageResponse getSaved(Actor actor, String goalParam, Integer kcalBand, int page, int size) {
        if (page < 1 || size < 1) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page, size는 1 이상이어야 합니다.");
        }
        Owner owner = ownerResolver.resolve(actor);
        GoalType goal = goalParam == null || goalParam.isBlank() ? GoalType.DEFAULT : GoalType.from(goalParam);
        EerBand band = EerBand.ofOrDefault(kcalBand);

        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SavedProduct> saved = owner.isUser()
                ? savedProductRepository.findByUserIdAndProductIsActiveTrue(owner.userId(), pageable)
                : savedProductRepository.findByAnonymousIdAndProductIsActiveTrue(owner.anonymousId(), pageable);

        List<Long> ids = saved.getContent().stream().map(s -> s.getProduct().getId()).toList();
        Map<Long, ProductCard> cards = new HashMap<>();
        cardService.withBetter(cardService.cards(ids, goal, band, owner, null), goal, band)
                .forEach(c -> cards.put(c.productId(), c));

        List<SavedProductPageResponse.Item> items = saved.getContent().stream()
                .filter(s -> cards.containsKey(s.getProduct().getId()))
                .map(s -> new SavedProductPageResponse.Item(cards.get(s.getProduct().getId()), s.getCreatedAt()))
                .toList();
        return new SavedProductPageResponse(goal.name(), band.kcal(), saved.getTotalElements(), saved.hasNext(), items);
    }

    // ── POST /me/saved-products/{productId} ──────────────────────────────────────────

    @Transactional
    public SaveResultResponse save(Actor actor, Long productId) {
        Owner owner = ownerResolver.resolve(actor);                        // 기기 ID 없음 → 400
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
