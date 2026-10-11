-- ============================================================================
-- feat/grade-engine — 수동 실행 SQL (전부 선택 사항)
--
-- 새 테이블 6개(product_grades · grade_recalibrations · grade_anchors · grade_criteria · grade_cutoffs · grade_copies)는
-- Hibernate(ddl-auto=update)가 부팅 시 만들고, 초기 기준 v1·설명 문구 시드와 첫 전량 등급 계산은 앱이 부팅 시 스스로 한다
-- (GradeEngineInitializer). 따라서 이 브랜치는 배포만으로 동작하고, 아래는 정리·확인용이다.
-- ============================================================================

-- [확인] 부팅 후 초기 적재가 끝났는지 — 분석 완료 제품 수 × 9 와 같아야 한다
-- SELECT goal, eer_band, COUNT(*) FROM product_grades GROUP BY goal, eer_band ORDER BY goal, eer_band;

-- [확인] 구 pns 값과 등급이 같은지 (v1 = 구 상수 그대로. 일반=health, 감량=diet, 근육 증가=bulk, 구간 2000)
-- SELECT COUNT(*) AS mismatched
-- FROM   product_grades g
-- JOIN   product_pns_by_eer p
--   ON   p.product_id = g.product_id
--  AND   p.eer_band   = 2000
--  AND   p.goal       = CASE g.goal WHEN 'GENERAL' THEN 'health' WHEN 'WEIGHT_LOSS' THEN 'diet' ELSE 'bulk' END
-- WHERE  g.eer_band IN (0, 2000)
--   AND  g.grade <> p.grade;
-- (0 이 아닐 수 있는 경우: 구 배치는 판정 7종 중 일부가 비어도 0 으로 채점했고 parent 없는 분류는 제외했다 — 새 배치는 명세 4.1 대로
--  7종 결측은 등급 없음, parent 없는 분류도 포함. 그 차이만큼은 정상이다.)

-- [정리] 구 pns 테이블은 코드에서 더 참조하지 않는다. 위 확인이 끝난 뒤 지운다.
-- DROP TABLE IF EXISTS product_pns_by_eer;
