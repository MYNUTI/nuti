package com.example.nutriuniv.domain.scan.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductBarcode;
import com.example.nutriuniv.domain.product.repository.ProductBarcodeRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.product.util.BarcodeNormalizer;
import com.example.nutriuniv.domain.scan.dto.BarcodePickResponse;
import com.example.nutriuniv.domain.scan.entity.BarcodeLinkReview;
import com.example.nutriuniv.domain.scan.entity.BarcodeName;
import com.example.nutriuniv.domain.scan.entity.BarcodePick;
import com.example.nutriuniv.domain.scan.repository.BarcodeLinkReviewRepository;
import com.example.nutriuniv.domain.scan.repository.BarcodeNameRepository;
import com.example.nutriuniv.domain.scan.repository.BarcodePickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 바코드 확인 · 선택 (v3 SCAN_008 · SCAN_009 · SCAN_010).
 * <ul>
 *   <li>비슷한 후보: 바코드에 등록된 이름(barcode_names)과 같은 회사 제품 중 용량 표기를 뺀 이름이 가장 비슷한 1개, 0.9 미만이면 없음</li>
 *   <li>선택은 기기(계정)마다 저장 — 다음 스캔부터 내 선택으로 바로 결과</li>
 *   <li>서로 다른 2명 이상이 같은 제품을 고르면 확인 대기에 올린다. 「이 제품인가요？」 선택이면 그때 바코드를 잇는다(모두에게 결과)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodePickService {

    /** 확인 대기에 올리는 서로 다른 사람 수 (11-17 · 판단값). */
    public static final int LINK_PICKERS = 2;

    public static final String THANKS = "알려 줘서 고마워요. 확인되면 다음부터 바로 나와요.";

    /** 같은 회사 제품을 이만큼까지만 비교한다. */
    private static final int MAX_COMPANY_PRODUCTS = 5_000;

    private final BarcodePickRepository pickRepository;
    private final BarcodeLinkReviewRepository reviewRepository;
    private final BarcodeNameRepository nameRepository;
    private final ProductRepository productRepository;
    private final ProductBarcodeRepository productBarcodeRepository;
    private final OwnerResolver ownerResolver;
    private final JdbcTemplate jdbcTemplate;

    public record Similar(long productId, double similarity) {}

    // ── 스캔에서 ──────────────────────────────────────────────────────────────────

    public Optional<BarcodeName> knownName(String barcode) {
        return nameRepository.findByBarcode(barcode);
    }

    /** 이 기기(계정)가 이 바코드에 고른 제품. 소유자가 없으면 없음. */
    public Optional<Long> ownPick(String barcode, Owner owner) {
        String key = ownerKey(owner);
        if (key == null) return Optional.empty();
        return pickRepository.findByBarcodeAndOwnerKey(barcode, key).map(BarcodePick::getProductId);
    }

    /** 제품별 고른 사람 수 — 여러 개 고르기 목록에서 많이 고른 제품을 위로. */
    public Map<Long, Long> pickCounts(String barcode) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] row : pickRepository.countByProduct(barcode)) {
            m.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return m;
    }

    /** 같은 회사 활성 제품 가운데 이름이 가장 비슷한 1개 (0.9 이상). 회사를 모르면 없음. */
    public Optional<Similar> similar(BarcodeName known) {
        String company = NameSimilarity.normalizeCompany(known.getCompany());
        if (company.isEmpty()) return Optional.empty();
        String target = NameSimilarity.normalizeName(known.getName());
        if (target.isEmpty()) return Optional.empty();

        String like = "%" + company + "%";
        List<Object[]> rows = jdbcTemplate.query("""
                SELECT p.id, p.name, p.maker, b.name
                  FROM products p LEFT JOIN brands b ON b.id = p.brand_id
                 WHERE p.is_active = TRUE
                   AND (LOWER(REPLACE(COALESCE(p.maker, ''), ' ', '')) LIKE ? OR LOWER(REPLACE(COALESCE(b.name, ''), ' ', '')) LIKE ?)
                 LIMIT ?
                """, (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)},
                like, like, MAX_COMPANY_PRODUCTS);

        Similar best = null;
        for (Object[] r : rows) {
            boolean sameCompany = company.equals(NameSimilarity.normalizeCompany((String) r[2]))
                    || company.equals(NameSimilarity.normalizeCompany((String) r[3]));
            if (!sameCompany) continue;
            double s = NameSimilarity.dice(target, NameSimilarity.normalizeName((String) r[1]));
            if (s >= NameSimilarity.THRESHOLD && (best == null || s > best.similarity())) {
                best = new Similar((Long) r[0], s);
            }
        }
        return Optional.ofNullable(best);
    }

    // ── POST /products/barcode/{barcode}/picks ────────────────────────────────────

    @Transactional
    public BarcodePickResponse pick(String rawBarcode, Long productId, String kindParam, Actor actor) {
        String barcode = BarcodeNormalizer.normalize(rawBarcode);
        BarcodePick.Kind kind = kind(kindParam);
        Product product = productRepository.findById(productId).filter(Product::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "없는 제품입니다."));
        if (kind == BarcodePick.Kind.MULTIPLE && !productBarcodeRepository.existsByProductIdAndBarcode(productId, barcode)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "이 바코드에 붙은 제품이 아닙니다.");
        }

        String key = ownerKey(ownerResolver.resolveOrNull(actor));
        if (key == null) {
            return new BarcodePickResponse(barcode, productId, false, THANKS);   // 기기 ID 가 없으면 기억하지 못한다
        }
        pickRepository.findByBarcodeAndOwnerKey(barcode, key)
                .ifPresentOrElse(p -> p.change(productId, kind),
                        () -> pickRepository.save(BarcodePick.of(barcode, productId, key, kind)));
        pickRepository.flush();

        long pickers = pickRepository.countByBarcodeAndProductId(barcode, productId);
        boolean linked = pickers >= LINK_PICKERS && queue(barcode, product, kind, pickers);
        return new BarcodePickResponse(barcode, productId, linked, THANKS);
    }

    /** 확인 대기에 올리고, 「이 제품인가요？」 선택이면 바코드를 잇는다. 이번에 이었으면 true. */
    private boolean queue(String barcode, Product product, BarcodePick.Kind kind, long pickers) {
        boolean link = kind == BarcodePick.Kind.CONFIRM && !productBarcodeRepository.existsByProductIdAndBarcode(product.getId(), barcode);
        if (link) productBarcodeRepository.save(ProductBarcode.of(product, barcode));

        Optional<BarcodeLinkReview> found = reviewRepository.findByBarcodeAndProductId(barcode, product.getId());
        if (found.isEmpty()) {
            reviewRepository.save(BarcodeLinkReview.pending(barcode, product.getId(), kind, link, pickers));
            log.info("[SCAN] 확인 대기 barcode={} product={} kind={} pickers={}", barcode, product.getId(), kind, pickers);
        } else if (found.get().getStatus() == BarcodeLinkReview.Status.REJECTED) {
            found.get().reopen(kind, link, pickers);
        } else {
            found.get().updatePickers(pickers);
        }
        return link;
    }

    // ── 운영 「확인 대기」 ─────────────────────────────────────────────────────────

    /**
     * 운영자 판단. 맞다 — 「이 제품인가요？」는 확정, 여러 개 고르기는 그 바코드를 고른 제품에만 남긴다.
     * 아니다 — 선택으로 이었던 연결을 끊고 그 선택을 지운다(다음 스캔에서 다시 묻는다).
     */
    @Transactional
    public BarcodeLinkReview decide(long reviewId, boolean confirmed, Long adminUserId) {
        BarcodeLinkReview r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "없는 확인 대기입니다."));
        if (r.getStatus() != BarcodeLinkReview.Status.PENDING) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "이미 처리한 확인 대기입니다.");
        }
        if (confirmed && r.getKind() == BarcodePick.Kind.MULTIPLE) {
            for (ProductBarcode b : productBarcodeRepository.findByBarcode(r.getBarcode())) {
                if (!b.getProduct().getId().equals(r.getProductId())) productBarcodeRepository.delete(b);
            }
        }
        if (!confirmed) {
            if (r.isLinkedByPicks()) {
                productBarcodeRepository.findByBarcode(r.getBarcode()).stream()
                        .filter(b -> b.getProduct().getId().equals(r.getProductId()))
                        .forEach(productBarcodeRepository::delete);
            }
            pickRepository.deleteByBarcodeAndProductId(r.getBarcode(), r.getProductId());
        }
        r.decide(confirmed, adminUserId);
        return r;
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────────

    static String ownerKey(Owner owner) {
        if (owner == null) return null;
        if (owner.isUser()) return "u:" + owner.userId();
        return owner.anonymousId() == null || owner.anonymousId().isBlank() ? null : "a:" + owner.anonymousId();
    }

    private static BarcodePick.Kind kind(String raw) {
        if (raw == null || raw.isBlank()) return BarcodePick.Kind.CONFIRM;
        try {
            return BarcodePick.Kind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "kind 는 CONFIRM 또는 MULTIPLE 이어야 합니다.");
        }
    }
}
