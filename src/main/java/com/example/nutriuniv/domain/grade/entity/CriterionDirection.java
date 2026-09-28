package com.example.nutriuniv.domain.grade.entity;

/**
 * 기준값 한 줄이 점수에 기여하는 방식 (기능명세서 4.1 ②·③).
 * <ul>
 *   <li>BONUS   — 유익 성분(단백질·식이섬유). threshold 가 만점선: min(값/만점선, 1) × weight 가점</li>
 *   <li>PENALTY — 제한 성분(당류·포화지방·나트륨·콜레스테롤·트랜스지방). threshold 를 넘는 만큼 max_value 에서 1 이 되도록 감점</li>
 *   <li>CALORIE — 에너지밀도. weight 부호가 목표를 가른다: 감량 −1(감점) · 근육 증가 +1(가점) · 일반 0(미반영)</li>
 * </ul>
 */
public enum CriterionDirection {
    BONUS, PENALTY, CALORIE
}
