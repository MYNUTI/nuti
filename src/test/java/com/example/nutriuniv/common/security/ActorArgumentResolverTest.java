package com.example.nutriuniv.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 기기 익명 ID 헤더 — UUID 모양만 받는다 (v3 ACCT_001: 기기가 만든 무작위 값). */
class ActorArgumentResolverTest {

    @Test
    void UUID는_소문자로_맞춰_받는다() {
        assertEquals("3f2b8c1e-9d4a-4b6f-8e2a-1c5d7f9a0b3e",
                ActorArgumentResolver.anonymousIdOrNull(" 3F2B8C1E-9D4A-4B6F-8E2A-1C5D7F9A0B3E "));
    }

    @Test
    void UUID가_아니면_없는_것으로_본다() {
        assertNull(ActorArgumentResolver.anonymousIdOrNull(null));
        assertNull(ActorArgumentResolver.anonymousIdOrNull(""));
        assertNull(ActorArgumentResolver.anonymousIdOrNull("abc"));
        assertNull(ActorArgumentResolver.anonymousIdOrNull("3f2b8c1e9d4a4b6f8e2a1c5d7f9a0b3e"));          // 하이픈 없음
        assertNull(ActorArgumentResolver.anonymousIdOrNull("3f2b8c1e-9d4a-4b6f-8e2a-1c5d7f9a0b3e-extra"));
        assertNull(ActorArgumentResolver.anonymousIdOrNull("zzzzzzzz-9d4a-4b6f-8e2a-1c5d7f9a0b3e"));
    }
}
