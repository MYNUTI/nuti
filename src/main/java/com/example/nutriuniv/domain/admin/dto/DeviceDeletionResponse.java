package com.example.nutriuniv.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;

/** DELETE /admin/devices/{anonymousId}/records 출력 — 표마다 지운 행 · 연결을 끊은 행 수, 요청 · 처리 시각 (ADM_006). */
public record DeviceDeletionResponse(Map<String, Integer> deleted, Map<String, Integer> unlinked, int deletedTotal, int unlinkedTotal,
                                     @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime requestedAt,
                                     @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime processedAt) {}
