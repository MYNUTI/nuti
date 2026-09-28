package com.example.nutriuniv.domain.saved.repository;

import com.example.nutriuniv.domain.saved.entity.SavedProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SavedProductRepository extends JpaRepository<SavedProduct, Long> {

    // 저장 목록 페이징 (활성 상품만) — 로그인 / 익명
    Page<SavedProduct> findByUserIdAndProductIsActiveTrue(Long userId, Pageable pageable);

    Page<SavedProduct> findByAnonymousIdAndProductIsActiveTrue(String anonymousId, Pageable pageable);

    // 멱등 저장·해제용 단건 조회
    Optional<SavedProduct> findByUserIdAndProductId(Long userId, Long productId);

    Optional<SavedProduct> findByAnonymousIdAndProductId(String anonymousId, Long productId);

    // 상한(200) 체크
    long countByUserId(Long userId);

    long countByAnonymousId(String anonymousId);

    // 상품 목록/상세의 isSaved 체크용
    boolean existsByUserIdAndProductIdAndProductIsActiveTrue(Long userId, Long productId);

    boolean existsByAnonymousIdAndProductIdAndProductIsActiveTrue(String anonymousId, Long productId);

    // 추천 API에서 isSaved 일괄 체크용
    @Query("SELECT s.product.id FROM SavedProduct s WHERE s.userId = :userId")
    Set<Long> findSavedProductIdsByUserId(@Param("userId") Long userId);

    // 검색 결과 isSaved 일괄 체크용 — 익명 소유
    @Query("SELECT s.product.id FROM SavedProduct s WHERE s.anonymousId = :anonymousId")
    Set<Long> findSavedProductIdsByAnonymousId(@Param("anonymousId") String anonymousId);

    // 로그인 병합(7.2)용 — 익명 소유 전체
    List<SavedProduct> findByAnonymousId(String anonymousId);
}
