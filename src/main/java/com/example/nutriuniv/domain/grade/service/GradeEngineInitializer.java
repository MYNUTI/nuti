package com.example.nutriuniv.domain.grade.service;

import com.example.nutriuniv.domain.grade.repository.GradeInputRepository;
import com.example.nutriuniv.domain.grade.repository.ProductGradeRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.nutriuniv.domain.grade.calc.DefaultCalibration;
import org.springframework.boot.ApplicationArguments;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 등급 엔진 준비 — 수동 SQL 없이 배포만으로 동작하게 한다.
 * <ol>
 *   <li>v3 정리 — 없앤 GENERAL 목표의 행을 지우고, v2 등급 라벨·설명 문구를 v3 문구로 바꾼다(재실행해도 같다)</li>
 *   <li>APPLIED 기준 버전이 없으면 v1(구 PnsCalculator 상수) 시드, 설명 문구 시드</li>
 *   <li>products.status 가 비어 있는 제품이 있으면 분석 완료 판정(4.1)으로 한 번에 채운다 (컬럼 신설 직후 백필)</li>
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
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    /** GENERAL 목표 행이 남을 수 있는 표 — 엔티티가 GoalType 을 읽다가 실패하지 않게 먼저 지운다. */
    static final List<String> GOAL_TABLES = List.of(
            "product_grades", "grade_anchors", "grade_criteria", "grade_cutoffs", "grade_copies", "category_rankings", "user_goals");

    @Override
    public void run(ApplicationArguments args) {
        try {
            migrateToV3();
        } catch (Exception e) {
            log.error("[GRADE] v3 정리 실패 — GENERAL 목표 행이 남아 있으면 등급 조회가 실패할 수 있습니다", e);
        }
        try {
            calibrationService.ensureSeeded();
        } catch (Exception e) {
            log.error("[GRADE] 기준 시드 실패 — 코드 기본값(v1)으로 계산합니다", e);
            return;
        }
        try {
            long missing = productRepository.countByStatusIsNull();
            if (missing > 0) {
                int updated = batchService.refreshProductStatusInTx();
                log.info("[GRADE] products.status 백필 — 비어 있던 {}건 중 {}건 갱신", missing, updated);
            }
        } catch (Exception e) {
            log.error("[GRADE] products.status 백필 실패 — 다음 등급 재계산 때 다시 채워집니다", e);
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

    /** GENERAL 행 삭제 · 라벨 · 문구. 표가 아직 없으면(새 DB) 그 표만 건너뛴다. */
    void migrateToV3() {
        int removed = 0;
        for (String table : GOAL_TABLES) {
            try {
                removed += jdbcTemplate.update("DELETE FROM " + table + " WHERE goal = 'GENERAL'");
            } catch (DataAccessException e) {
                log.debug("[GRADE] {} 정리 건너뜀: {}", table, e.getMessage());
            }
        }
        int labels = 0;
        for (Map.Entry<String, String> e : DefaultCalibration.LEGACY_LABELS.entrySet()) {
            labels += jdbcTemplate.update("UPDATE grade_cutoffs SET label = ? WHERE label = ?", e.getValue(), e.getKey());
        }
        int copies = 0;
        for (DefaultCalibration.LegacyCopy c : DefaultCalibration.LEGACY_COPY_BODIES) {
            copies += c.goal() == null
                    ? jdbcTemplate.update("UPDATE grade_copies SET body = ? WHERE copy_code = ? AND goal IS NULL AND body = ?", c.newBody(), c.code(), c.oldBody())
                    : jdbcTemplate.update("UPDATE grade_copies SET body = ? WHERE copy_code = ? AND goal = ? AND body = ?", c.newBody(), c.code(), c.goal(), c.oldBody());
        }
        if (removed + labels + copies > 0) {
            log.info("[GRADE] v3 정리 — GENERAL 행 {}건 삭제, 라벨 {}건 · 문구 {}건 갱신", removed, labels, copies);
        }
    }
}
