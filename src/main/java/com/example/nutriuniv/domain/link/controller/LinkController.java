package com.example.nutriuniv.domain.link.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.link.dto.LinkFeedbackRequest;
import com.example.nutriuniv.domain.link.dto.LinkResolveRequest;
import com.example.nutriuniv.domain.link.dto.LinkResolveResponse;
import com.example.nutriuniv.domain.link.dto.PurchaseLinkResponse;
import com.example.nutriuniv.domain.link.service.LinkResolveService;
import com.example.nutriuniv.domain.link.service.PurchaseLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Link", description = "구매 링크 (기능명세서 8.1) · 붙여넣은 링크 제품 인식 (2차)")
@RestController
@RequiredArgsConstructor
public class LinkController {

    private final PurchaseLinkService purchaseLinkService;
    private final LinkResolveService linkResolveService;

    // GET /products/{productId}/purchase-link
    @Operation(summary = "구매 링크 (명세 8.1)",
            description = "저장된 쿠팡 링크를 꺼내 줍니다. matchType EXACT(구매하기) | NAME_SEARCH(이 제품을 검색해 보기 — 구매하기 금지) | NONE. " +
                    "가격은 EXACT 이고 24시간 이내 조회분일 때만(priceCheckedAt 함께). 광고 표시(isAd·adNotice)는 버튼 바로 옆에. " +
                    "외부 호출이 없어 제품 화면은 항상 정상. 없는·비활성 제품 404. 클릭 로그는 기존 POST /logging/cta.")
    @GetMapping("/products/{productId}/purchase-link")
    public ResponseEntity<CommonResponse<PurchaseLinkResponse>> purchaseLink(
            @Parameter(description = "제품 ID") @PathVariable Long productId) {
        return ResponseEntity.ok(CommonResponse.success(purchaseLinkService.get(productId)));
    }

    // POST /links/resolve
    @Operation(summary = "붙여넣은 링크 제품 인식",
            description = "sourceType COUPANG | UNKNOWN. 쿠팡 링크만 외부 조회(3초 예산, 쿠팡 호스트 밖으로 안 나감). " +
                    "매칭 순서: 사용자 교정 → 쿠팡 상품 ID(이미 연결된 제품) → 페이지 제목을 검색 색인으로. " +
                    "미지원·매칭 실패·시간 초과 전부 200 + matched=null (parsedTitle 로 검색 폴백). url 형식 오류 400. 같은 링크 24시간 캐시(cached=true). " +
                    "저장 전 utm_·제휴코드를 뗍니다. 클립보드 감지 OFF 면 클라이언트가 호출하지 않습니다.")
    @PostMapping("/links/resolve")
    public ResponseEntity<CommonResponse<LinkResolveResponse>> resolve(Actor actor, @RequestBody LinkResolveRequest request) {
        return ResponseEntity.ok(CommonResponse.success(linkResolveService.resolve(request, actor)));
    }

    // POST /links/resolve/{resolveId}/feedback
    @Operation(summary = "링크 매칭 피드백",
            description = "「맞아요」 confirmed=true / 「아니에요」 confirmed=false (+ correctedProductId 선택). 없는 resolveId 404, 없는 제품 404. " +
                    "바로잡은 제품은 같은 링크의 다음 인식에 먼저 쓰입니다.")
    @PostMapping("/links/resolve/{resolveId}/feedback")
    public ResponseEntity<CommonResponse<Void>> feedback(Actor actor,
                                                         @Parameter(description = "resolve 응답의 resolveId") @PathVariable String resolveId,
                                                         @RequestBody LinkFeedbackRequest request) {
        linkResolveService.feedback(resolveId, request, actor);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
