-- ============================================================================
-- feat/search-v2 — 수동 실행 SQL (권장 1건 + 사전 적재 템플릿)
--
-- Hibernate(ddl-auto=update)가 부팅 시 만드는 것: products.name_normalized·name_chosung, search_synonyms, search_settings.
-- 부팅 시 앱이 스스로: search_settings 기본값(유사도 0.30·편집거리 2)·동의어 예시 시드(비어 있을 때만), 색인 컬럼 백필(백그라운드).
-- 따라서 검색은 배포만으로 동작한다. 아래는 속도와 사전을 위한 것이다.
-- ============================================================================

-- [권장 · 배포 후] pg_trgm GIN 인덱스 — 없어도 동작하지만 27만 건에서 similarity()·LIKE '%x%' 가 순차 스캔이 된다. Hibernate 는 GIN 을 못 만든다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_products_name_normalized_trgm ON products USING gin (name_normalized gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_name_trgm            ON products USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_name_chosung_trgm    ON products USING gin (name_chosung gin_trgm_ops);

-- [확인] 색인 백필이 끝났는지 (0 이어야 한다)
-- SELECT COUNT(*) FROM products WHERE name_normalized IS NULL;

-- [설정] 유사도 기준값·편집거리 상한 — 실측으로 조정 (재배포 없음)
-- UPDATE search_settings SET setting_value = '0.35', updated_at = now() WHERE setting_key = 'SIMILARITY_THRESHOLD';

-- [사전] 동의어 400~600항목 적재 템플릿 — term·canonical 은 반드시 정규화 형태(소문자, 공백·특수문자 제거)로 넣는다.
--        SearchNormalizer.normalize 와 같은 규칙: '프로틴 바' → '프로틴바', 'Protein Bar' → 'proteinbar'
-- INSERT INTO search_synonyms (term, canonical, synonym_type, is_active) VALUES
--     ('프로틴', '단백질', 'SYNONYM', TRUE),
--     ('proteinbar', '프로틴바', 'SYNONYM', TRUE),
--     ('maeil', '매일', 'BRAND_ALIAS', TRUE)
-- ON CONFLICT (term, canonical) DO NOTHING;

-- [확인] 사전 규모
-- SELECT synonym_type, COUNT(*) FROM search_synonyms WHERE is_active GROUP BY synonym_type;
