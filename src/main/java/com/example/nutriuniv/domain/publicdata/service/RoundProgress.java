package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.domain.publicdata.client.PublicApi;

/**
 * 한 페이지를 받은 뒤 회차가 끝났는가 (sync.md 「끝까지 받음」). 순수 함수 — RoundProgressTest 로 고정.
 * <ul>
 *   <li>받은 행 수가 totalCount 와 같아지면 끝. 넘거나 모자란 채로 빈 페이지가 오면 실패(일부만 받은 채 계산하면 칸이 대량으로 비거나 틀어진다)</li>
 *   <li>C005 는 totalCount 를 믿을 수 없어 빈 페이지 · 덜 찬 페이지가 나오면 끝</li>
 *   <li>totalCount 가 없으면 빈 페이지 · 덜 찬 페이지에서 끝</li>
 * </ul>
 */
public final class RoundProgress {

    public enum Next { CONTINUE, DONE, COUNT_MISMATCH }

    private RoundProgress() {}

    /**
     * @param total         응답의 전체 행 수(없으면 null)
     * @param receivedAfter 이번 페이지까지 받은 행 수
     * @param rowsThisPage  이번 페이지 행 수
     */
    public static Next after(PublicApi api, Long total, long receivedAfter, int rowsThisPage) {
        boolean lastPage = rowsThisPage < api.pageSize();   // 빈 페이지 포함
        if (api.totalUntrusted() || total == null) {
            return lastPage ? Next.DONE : Next.CONTINUE;
        }
        if (receivedAfter == total) return Next.DONE;
        if (receivedAfter > total) return Next.COUNT_MISMATCH;
        return lastPage ? Next.COUNT_MISMATCH : Next.CONTINUE;
    }
}