-- ============================================================================
-- chore/db-schema-v2 — 2차 서버는 v2 스키마에 새로 쌓는다(기존 public 테이블은 손대지 않고 보존)
--
-- [배포 전] GitHub 시크릿 SPRING_DATASOURCE_URL 끝에 ?currentSchema=v2,public 를 붙인다
--   (URL 에 이미 ? 가 있으면 &currentSchema=v2,public).
--   · v2     = 2차 테이블이 생기고 읽히는 곳. 네이티브 SQL 도 v2 를 먼저 찾는다
--   · public = pg_trgm 함수(similarity·gin_trgm_ops)가 설치된 곳. 빼면 검색이 'function does not exist' 로 실패
--   deploy.yml 의 hibernate.default_schema=v2 · hbm2ddl.create_namespaces=true 로 첫 부팅 때 v2 스키마와 테이블이 자동 생성된다.
--
-- [배포 후] 앱이 한 번 부팅해 v2 에 테이블이 생긴 뒤 이 파일을 실행한다.
--   02~08 수동 SQL 은 기존 public 기준이라 그대로 돌리지 말 것 — search_path 없이 돌리면 보존 중인 public 테이블이 바뀐다.
--   v2 에 필요한 것만 아래에 모았다. 새 스키마는 엔티티대로 만들어져 NOT NULL 해제(02·05)는 필요 없다.
-- 모든 문장은 재실행해도 안전(IF NOT EXISTS / ON CONFLICT).
-- ============================================================================

SET search_path TO v2, public;

-- ─── 02: 소유자 XOR(CHECK) · 익명 저장 중복 방지(부분 유니크) · 정책 시드 ──────────────────

ALTER TABLE consents
    DROP CONSTRAINT IF EXISTS ck_consents_owner_xor,
    ADD CONSTRAINT ck_consents_owner_xor CHECK ((user_id IS NULL) <> (anonymous_id IS NULL));

ALTER TABLE user_goals
    DROP CONSTRAINT IF EXISTS ck_user_goals_owner_xor,
    ADD CONSTRAINT ck_user_goals_owner_xor CHECK ((user_id IS NULL) <> (anonymous_id IS NULL));

ALTER TABLE user_favorites
    DROP CONSTRAINT IF EXISTS ck_user_favorites_owner_xor,
    ADD CONSTRAINT ck_user_favorites_owner_xor CHECK ((user_id IS NULL) <> (anonymous_id IS NULL));

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_favorites_anon_product
    ON user_favorites (anonymous_id, product_id) WHERE anonymous_id IS NOT NULL;

-- 정책 버전 시드 — 버전·URL 은 팀이 확정한 값으로 바꿀 것. is_current 행이 없으면 서버는 요청 버전을 그대로 기록한다(경고 로그).
INSERT INTO policies (policy_type, version, url, effective_from, is_current)
VALUES ('PRIVACY', 'v1', 'https://example.com/policies/privacy/v1', CURRENT_DATE, TRUE),
       ('TERMS',   'v1', 'https://example.com/policies/terms/v1',   CURRENT_DATE, TRUE),
       ('HEALTH',  'v1', 'https://example.com/policies/health/v1',  CURRENT_DATE, TRUE)
ON CONFLICT (policy_type, version) DO NOTHING;

-- ─── 06: 검색 GIN 인덱스 (Hibernate 는 GIN 을 못 만든다) ─────────────────────────────────

CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public;
CREATE INDEX IF NOT EXISTS idx_products_name_normalized_trgm ON products USING gin (name_normalized gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_name_trgm            ON products USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_name_chosung_trgm    ON products USING gin (name_chosung gin_trgm_ops);

-- ─── 08: 분류별 집계·랭킹 배치용 ────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_products_category_id ON products (category_id);

-- [확인] 2차 테이블이 v2 에만 생겼는지 — public 의 테이블 수는 배포 전과 같아야 한다
-- SELECT table_schema, COUNT(*) FROM information_schema.tables WHERE table_schema IN ('public', 'v2') GROUP BY 1;
