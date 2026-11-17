package com.example.nutriuniv.domain.publicdata.compute;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 34컬럼 계산의 입력 — 원본 표에서 꺼낸 행(필드명 → 값)과 DB 에 저장된 값.
 * 원본 행은 받은 순서(먼저 받은 것 → 나중 받은 것)로 넣는다. 같은 키가 여러 번 나오면 나중 것을 쓴다.
 *
 * @param discontinued I2852 의 품목보고번호(ComputeRules.reportNo 로 맞춘 값)
 * @param categories   지금 product 의 master_id → 분류 코드. 분류는 LLM 이 의미를 읽고 채운 값이라 규칙으로 다시 만들 수 없어 그대로 이어 쓴다
 * @param storedGroups 저장된 묶음 구성 food_cd → 묶음
 */
public record MasterInput(
        List<Map<String, String>> nutriDb,
        List<Map<String, String>> foodQrList,
        List<Map<String, String>> foodQrNutrition,
        List<Map<String, String>> foodQrMaker,
        List<Map<String, String>> haccp,
        List<Map<String, String>> c005,
        List<Map<String, String>> i2570,
        List<Map<String, String>> c002,
        List<Map<String, String>> c006,
        List<Map<String, String>> i0490,
        Set<String> discontinued,
        Map<String, String> categories,
        Map<String, BrandGrouping.Stored> storedGroups
) {}