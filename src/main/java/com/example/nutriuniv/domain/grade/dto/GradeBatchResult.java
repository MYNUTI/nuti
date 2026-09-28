package com.example.nutriuniv.domain.grade.dto;

/** 등급 전량 재계산 결과 (구 PnsBatchService.BatchResult 와 같은 필드 + 적용 기준 버전). */
public record GradeBatchResult(int productCount, int savedRows, int combinations, long elapsedMs, String version) {}
