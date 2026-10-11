package com.example.nutriuniv.domain.search.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동의어 사전 (기능명세서 6.2 「사전 400~600항목을 먼저 만들어야 한다」). term → canonical 로 정규화된 형태(SearchNormalizer.normalize)만 저장한다.
 * 같은 canonical 을 가리키는 term 들이 한 묶음. 검색은 질의를 묶음 전체로 확장(비슷한 제품 절)하고, 자동완성·didYouMean 은 편집거리로 교정한다.
 * 부팅 시 비어 있으면 예시 몇 줄만 시드된다 — 본 사전은 팀이 채운다(db/manual/06_search.sql 의 적재 템플릿).
 */
@Entity
@Table(name = "search_synonyms",
        uniqueConstraints = @UniqueConstraint(name = "uk_search_synonyms_term_canonical", columnNames = {"term", "canonical"}),
        indexes = @Index(name = "idx_search_synonyms_canonical", columnList = "canonical"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchSynonym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String term;            // 정규화된 검색어 (소문자·공백/특수문자 제거)

    @Column(nullable = false, length = 50)
    private String canonical;       // 정규화된 대표어

    @Enumerated(EnumType.STRING)
    @Column(name = "synonym_type", nullable = false, length = 15)
    private SynonymType synonymType;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public static SearchSynonym create(String term, String canonical, SynonymType type) {
        SearchSynonym s = new SearchSynonym();
        s.term = term;
        s.canonical = canonical;
        s.synonymType = type;
        s.isActive = true;
        return s;
    }
}
