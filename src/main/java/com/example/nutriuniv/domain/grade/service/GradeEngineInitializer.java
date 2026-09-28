package com.example.nutriuniv.domain.grade.service;

import com.example.nutriuniv.domain.grade.repository.GradeInputRepository;
import com.example.nutriuniv.domain.grade.repository.ProductGradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 등급 엔진 준비 — 수동 SQL 없이 배포만으로 동작하게 한다.
 * <ol>
 *   <li>APPLIED 기준 버전이 없으면 v1(구 PnsCalculator 상수) 시드, 설명 문구 시드</li>
 *   <li>product_grades 가 비어 있고 분석 완료 제품이 있으면 백그라운드로 첫 전량 계산 (구 pns 테이블과 같은 결과)</li>
 * </ol>
 * 어느 단계가 실패해도 서버 기동은 막지 않는다 — 조회는 등급 없음(INSUFFICIENT)으로 내려가고, 로그와 수동 실행으로 복구한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeEngineInitializer implements ApplicationRunner {

    private final CalibrationService calibrationService;
    private final GradeBatchService batchService;
    private final ProductGradeRepository productGradeRepository;
    private final GradeInputRepository inputRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            calibrationService.ensureSeeded();
        } catch (Exception e) {
            log.error("[GRADE] 기준 시드 실패 — 코드 기본값(v1)으로 계산합니다", e);
            return;
        }
        try {
            if (productGradeRepository.count() == 0 && inputRepository.countAnalyzed() > 0) {
                log.info("[GRADE] product_grades 가 비어 있어 초기 적재를 시작합니다");
                batchService.recomputeAsync("startup");
            }
        } catch (Exception e) {
            log.error("[GRADE] 초기 적재 판단 실패 — POST /admin/pns/calculate 로 수동 실행하세요", e);
        }
    }
}
