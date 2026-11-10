package com.example.nutriuniv.domain.admin.dto;

import java.util.Map;

/** DELETE /admin/devices/{anonymousId}/records 출력 — 표마다 지운 행 · 연결을 끊은 행 수. */
public record DeviceDeletionResponse(Map<String, Integer> deleted, Map<String, Integer> unlinked, int deletedTotal, int unlinkedTotal) {}
