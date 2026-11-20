package com.familysupport.app.benefit.domain;

import java.util.List;

/**
 * 검색 결과 1건(카드 1장) — 대표 사업 + 내 상황에 대한 판정.
 *
 * @param others 같은 사업으로 묶인 다른 출처의 사업(상세에서 출처별 원문을 보여준다). 없으면 빈 목록
 */
public record EvaluatedBenefit(Benefit benefit, Eligibility eligibility, List<Benefit> others) {

    public EvaluatedBenefit {
        others = List.copyOf(others);
    }
}
