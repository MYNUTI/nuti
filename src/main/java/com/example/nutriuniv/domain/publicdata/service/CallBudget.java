package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.domain.publicdata.client.PublicApi;

import java.time.LocalDateTime;

/**
 * 지금 몇 번 더 불러도 되는가 (sync.md 「호출 가능량」). 순수 함수 — CallBudgetTest 로 고정.
 * <ul>
 *   <li>하루 한도의 90% 까지만 — 재시도 · 손으로 확인할 몫을 남긴다</li>
 *   <li>식품안전나라 낮 시간(06~19시)은 서비스별 300회 → 270회</li>
 *   <li>식품안전나라를 CHNG_DT 없이 부르면(전량 · I0490 · EVL_YR) 1시간 100회 → 90회</li>
 * </ul>
 * 한도는 서비스(API)별로 따로 센다 — C005 가 막혀도 C002 는 된다.
 */
public final class CallBudget {

    static final double SAFETY = 0.9;
    static final int FOOD_SAFETY_DAYTIME_LIMIT = 300;
    static final int FOOD_SAFETY_HOURLY_LIMIT_WITHOUT_CHNG_DT = 100;
    static final int DAYTIME_FROM = 6, DAYTIME_TO = 19;

    private CallBudget() {}

    /** 지난 호출 수. today = 오늘 0시부터, lastHour = 최근 1시간, daytimeToday = 오늘 06~19시. */
    public record Used(int today, int lastHour, int daytimeToday) {}

    /**
     * @param dailyLimit  그 API 의 하루 한도(PublicApi.dailyLimit, 공공데이터포털은 설정값)
     * @param usingChngDt CHNG_DT 조건으로 부르는가(증분 회차)
     * @param wanted      이번에 부르고 싶은 수
     * @return 0 이상
     */
    public static int allowed(PublicApi api, int dailyLimit, boolean usingChngDt, LocalDateTime now, Used used, int wanted) {
        int left = (int) Math.floor(dailyLimit * SAFETY) - used.today();
        if (api.provider() == PublicApi.Provider.FOOD_SAFETY) {
            int hour = now.getHour();
            if (hour >= DAYTIME_FROM && hour < DAYTIME_TO) {
                left = Math.min(left, (int) Math.floor(FOOD_SAFETY_DAYTIME_LIMIT * SAFETY) - used.daytimeToday());
            }
            if (!usingChngDt) {
                left = Math.min(left, (int) Math.floor(FOOD_SAFETY_HOURLY_LIMIT_WITHOUT_CHNG_DT * SAFETY) - used.lastHour());
            }
        }
        return Math.max(0, Math.min(left, wanted));
    }
}