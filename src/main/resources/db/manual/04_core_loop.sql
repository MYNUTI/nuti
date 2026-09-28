-- ============================================================================
-- feat/core-loop — 수동 실행 SQL (전부 선택 사항)
--
-- Hibernate(ddl-auto=update)가 부팅 시 만드는 것: products.barcode(varchar 13, UNIQUE)·products.status,
-- analysis_requests(barcode·keyword·product_id 각각 UNIQUE — PostgreSQL 은 NULL 을 서로 다른 값으로 보므로 그대로 부분 유니크),
-- analysis_request_events, scan_event_logs.
-- products.status 는 부팅 시 GradeEngineInitializer 가 NULL 인 행을 한 번에 채우고, 이후 영양정보 저장·등급 배치가 갱신한다.
-- ============================================================================

-- [확인] 상태 분포 — 분석 완료 비율(데이터 현황 대시보드 10.5 의 analyzedRatio)
-- SELECT status, COUNT(*) FROM products WHERE is_active = TRUE GROUP BY status;

-- [데이터] 바코드 적재는 코드가 아니라 데이터 작업이다 — 식약처 원천(품목제조보고번호 기준)에서 바코드를 가져와 넣을 때
--         반드시 13자리로 정규화해서 넣는다(14자리는 앞 0 제거, 12·8자리는 왼콽 0 채움, 체크섬 검증). 규칙은 BarcodeNormalizer 와 같아야 한다.
--         엑셀 업로드에 「바코드」 열을 넣으면 서버가 같은 규칙으로 정규화해 저장한다(형식·체크섬 오류·중복은 그 셀만 건너뜀).
-- 예) 이미 14자리로 들어온 값을 고치는 경우:
-- UPDATE products SET barcode = substring(barcode from 2) WHERE length(barcode) = 14 AND barcode LIKE '0%';

-- [확인] 대기 목록 수요 상위
-- SELECT type, COALESCE(barcode, keyword, product_id::text) AS identifier, request_count, status, last_requested_at
-- FROM analysis_requests ORDER BY request_count DESC, last_requested_at DESC LIMIT 50;
