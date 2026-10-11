package com.example.nutriuniv.domain.ranking.service;

import com.example.nutriuniv.domain.grade.entity.GradeCopy;
import com.example.nutriuniv.domain.grade.repository.GradeCopyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 선정 기준 안내 문구 (GET /rankings/criteria) — grade_copies 에 RANKING_* 코드로 둔다. 산식·게이트가 바뀌면 DB 에서 고치고
 * 클라 배포 없이 갱신된다(API 명세). 비어 있을 때만 시드하고, 있으면 관리자가 고친 문구를 보존한다.
 */
@Service
@RequiredArgsConstructor
public class RankingCopySeeder {

    public static final String VERSION = "r1";
    public static final List<String> CODES = List.of("RANKING_BASIS", "RANKING_GATE", "RANKING_COVERAGE", "RANKING_NO_ADS", "RANKING_UPDATE");

    private final GradeCopyRepository copyRepository;

    @Transactional
    public void ensureSeeded() {
        if (!copyRepository.findByCopyCodeOrderByDisplayOrderAsc("RANKING_BASIS").isEmpty()) return;
        copyRepository.saveAll(List.of(
                GradeCopy.create("RANKING_BASIS", null, "어떻게 순위를 매기나요",
                        "같은 분류의 분석 완료 제품을 영양 점수(등급) 순으로 정렬합니다. 점수가 같으면 영양정보를 더 최근에 확인한 제품이 앞에 옵니다. "
                                + "목표(일반·체중 감량·근육 증가)를 바꾸면 그 목표 기준의 점수로 순위가 다시 매겨집니다. "
                                + "순위는 상대 순서일 뿐이라 1위여도 등급이 낮을 수 있어요 — 순위와 등급을 함께 보세요.", 1, VERSION),
                GradeCopy.create("RANKING_GATE", null, "어떤 분류가 열리나요",
                        "분석 완료 제품이 " + RankingGate.MIN_ANALYZED + "개 이상이고 A등급과 D등급이 각각 1개 이상 있는 분류만 순위를 보여줍니다. "
                                + "제품 수가 적거나 등급 차이가 없는 분류는 순위가 의미를 잃기 때문입니다.", 2, VERSION),
                GradeCopy.create("RANKING_COVERAGE", null, "분석 완료 비율은 무엇인가요",
                        "그 분류 전체 제품 중 영양성분 7종이 모두 확인돼 등급을 계산한 제품의 비율입니다. "
                                + "영양정보가 부족한 제품은 순위에는 들지 않지만 전체 개수에는 포함합니다.", 3, VERSION),
                GradeCopy.create("RANKING_NO_ADS", null, "광고나 제휴가 순위에 영향을 주나요",
                        "아니요. 순위에는 광고·제휴·노출 신호를 넣지 않습니다. 구매 링크는 별도로 표시되며 노출비를 받지 않습니다. "
                                + "등급은 식약처 공공 영양성분DB를 기준으로 계산합니다.", 4, VERSION),
                GradeCopy.create("RANKING_UPDATE", null, "언제 갱신되나요",
                        "매일 새벽 4시에 다시 계산합니다. 등급 기준이 재산출되면 다음 갱신부터 반영됩니다.", 5, VERSION)
        ));
    }
}
