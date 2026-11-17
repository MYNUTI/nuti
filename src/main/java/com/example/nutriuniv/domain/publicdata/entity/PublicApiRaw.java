package com.example.nutriuniv.domain.publicdata.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공공 API 원본 — 응답 한 행을 가공하지 않고 그대로(jsonb). API 13개를 api 칸으로 나눠 한 표에 둔다.
 * <ul>
 *   <li>34컬럼 재계산은 이 표만 읽는다. 재계산 규칙이 바뀌어도 API 를 다시 부르지 않는다</li>
 *   <li>row_hash = payload 문자열의 sha256. 같은 회차에서 같은 행을 두 번 넣지 않는다(재시도 · 페이지 겹침)</li>
 *   <li>증분 회차가 쌓이면 같은 행이 여러 회차에 있을 수 있다 — 재계산이 CHNG_DT 최신 · 중복 제거로 정리한다</li>
 *   <li>전량 회차가 끝나면 그 회차가 시작되기 전의 행은 지운다(삭제된 줄을 걸러내는 유일한 방법)</li>
 * </ul>
 * 읽기 · 쓰기는 PublicApiRawRepository(JDBC). 이 엔티티는 표를 만들기(ddl-auto) 위한 정의다.
 */
@Entity
@Table(name = "public_api_raw",
        uniqueConstraints = @UniqueConstraint(name = "uk_public_api_raw_round_row", columnNames = {"api", "round_id", "row_hash"}),
        indexes = @Index(name = "idx_public_api_raw_api", columnList = "api, round_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicApiRaw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String api;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "row_hash", nullable = false, length = 64)
    private String rowHash;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}