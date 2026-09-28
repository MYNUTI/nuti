package com.example.nutriuniv.domain.grade.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.grade.dto.GradeBatchResult;
import com.example.nutriuniv.domain.grade.dto.RecalibrationHistoryResponse;
import com.example.nutriuniv.domain.grade.dto.RecalibrationPreviewResponse;
import com.example.nutriuniv.domain.grade.dto.RecalibrationResultResponse;
import com.example.nutriuniv.domain.grade.service.GradeBatchService;
import com.example.nutriuniv.domain.grade.service.GradeRecalibrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Grade", description = "관리자 등급 엔진 — 전량 재계산, 기준 재산출(기능명세서 4.2·10.3)")
@RestController
@RequiredArgsConstructor
public class AdminGradeController {

    private final GradeBatchService gradeBatchService;
    private final GradeRecalibrationService recalibrationService;

    // POST /admin/pns/calculate — 경로는 기존 관리자 화면 호환을 위해 유지 (구 PNS 배치 자리)
    @Operation(summary = "등급 전량 재계산 (현행 기준)",
            description = """
                    분석 완료(판정 7종 보유) 활성 제품 전체를 목표 3종 × 열량구간(감량·근육 4구간, 일반 1) = 9슬롯으로 계산해
                    product_grades 를 한 트랜잭션으로 교체합니다. 기준(앵커·컷오프·기준값)은 바꾸지 않습니다 — 제품 적재·보강 뒤에 실행하세요.
                    재계산 중 조회는 직전 값을 봅니다. 다른 재계산·재산출이 진행 중이면 409.
                    """)
    @PostMapping("/admin/pns/calculate")
    public ResponseEntity<CommonResponse<GradeBatchResult>> recompute() {
        return ResponseEntity.ok(CommonResponse.success(gradeBatchService.recomputeAll()));
    }

    // POST /admin/grade-recalibrations/preview
    @Operation(summary = "기준 재산출 미리보기 (명세 10.3·4.2)",
            description = """
                    현행 기준값으로 전체를 채점한 뒤 목표별 raw 점수의 P1/P99 로 새 앵커를, 정규화 점수의 80/60/40/20% 지점으로
                    새 A~D 컷오프를 계산해 재산출 전후 등급 분포와 함께 보여줍니다. 저장하지 않습니다.
                    """)
    @PostMapping("/admin/grade-recalibrations/preview")
    public ResponseEntity<CommonResponse<RecalibrationPreviewResponse>> preview() {
        return ResponseEntity.ok(CommonResponse.success(recalibrationService.preview()));
    }

    // POST /admin/grade-recalibrations
    @Operation(summary = "기준 재산출 실행",
            description = """
                    미리보기와 같은 계산으로 새 버전(vN)을 만들고 전체 등급을 재계산합니다. 새 버전 저장과 등급 교체는 한 트랜잭션 —
                    실패하면 전부 되돌리고 ROLLED_BACK 기록만 남아 직전 버전이 유지됩니다. 기준일·모수를 버전과 함께 저장합니다.
                    마스터 적재가 끝난 뒤에 실행하세요(진행 중이면 409).
                    """)
    @PostMapping("/admin/grade-recalibrations")
    public ResponseEntity<CommonResponse<RecalibrationResultResponse>> execute(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(recalibrationService.execute(actor.userId())));
    }

    // GET /admin/grade-recalibrations
    @Operation(summary = "기준 재산출 이력",
            description = "버전·상태·기준일·모수·적용 시점. 재산출 전후로 같은 제품의 등급이 바뀌므로 적용 시점이 기준이 됩니다. 최신순.")
    @GetMapping("/admin/grade-recalibrations")
    public ResponseEntity<CommonResponse<RecalibrationHistoryResponse>> history() {
        return ResponseEntity.ok(CommonResponse.success(recalibrationService.history()));
    }
}
