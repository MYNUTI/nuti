package com.example.nutriuniv.domain.grade.entity;

/**
 * 기준 재산출 버전의 상태 (기능명세서 4.2·10.3).
 * <ul>
 *   <li>APPLIED     — 적용됨. 이 중 applied_at 이 가장 최근인 행이 현행 기준</li>
 *   <li>ROLLED_BACK — 실행 중 실패해 트랜잭션이 되돌려짐. 등급·기준은 직전 버전 그대로이고 이 행은 실패 기록만 남긴다</li>
 * </ul>
 * 미리보기(preview)는 저장하지 않는다 — 같은 데이터로 실행하면 같은 값이 나오는 순수 계산이다.
 */
public enum RecalibrationStatus {
    APPLIED, ROLLED_BACK
}
