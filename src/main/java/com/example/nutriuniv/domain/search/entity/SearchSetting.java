package com.example.nutriuniv.domain.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검색 기준값 (기능명세서 6.1 「유사도 기준값은 DB에서 읽는다. 초기값은 추정이고 실측으로 조정한다」).
 * 키: SIMILARITY_THRESHOLD(pg_trgm 유사도 하한, 기본 0.30) · DID_YOU_MEAN_MAX_DISTANCE(오타 교정 편집거리 상한, 기본 2).
 * 관리자가 DB 에서 바로 바꾼다(재배포 없음).
 */
@Entity
@Table(name = "search_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchSetting {

    public static final String SIMILARITY_THRESHOLD = "SIMILARITY_THRESHOLD";
    public static final String DID_YOU_MEAN_MAX_DISTANCE = "DID_YOU_MEAN_MAX_DISTANCE";

    @Id
    @Column(name = "setting_key", length = 40)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 100)
    private String value;

    @Column(length = 255)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static SearchSetting create(String key, String value, String description) {
        SearchSetting s = new SearchSetting();
        s.key = key;
        s.value = value;
        s.description = description;
        s.updatedAt = LocalDateTime.now();
        return s;
    }
}
