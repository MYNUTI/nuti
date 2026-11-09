package com.example.nutriuniv.domain.link.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.link.dto.PurchaseLinkResponse;
import com.example.nutriuniv.domain.link.service.PurchaseLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 붙여넣은 쿠팡 링크로 제품 찾기(/links/resolve)는 v3 명세 「만들지 않는 것」이라 뺐다
@Tag(name = "Link", description = "구매 링크 (기능명세서 8.1)")
@RestController
@RequiredArgsConstructor
public class LinkController {

    private final PurchaseLinkService purchaseLinkService;

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
}
