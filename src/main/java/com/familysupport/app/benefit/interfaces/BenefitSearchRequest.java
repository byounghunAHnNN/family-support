package com.familysupport.app.benefit.interfaces;

import java.util.List;

/**
 * 검색 요청(POST 본문). 소득·가구 상황이 들어 있어 URL에 넣지 않는다.
 *
 * @param stages             LifeStage 이름 목록(PREGNANT 등)
 * @param annualIncomeManwon 가구 합산 연 소득(만원)
 */
public record BenefitSearchRequest(
        String sido,
        String sigungu,
        List<String> stages,
        Integer parentAge,
        Integer childAge,
        Integer householdSize,
        Long annualIncomeManwon,
        Boolean dualIncome,
        Boolean noHome,
        Boolean multiChild,
        Boolean singleParent,
        Boolean disabled,
        Boolean lowIncome,
        Boolean multicultural,
        String keyword) {
}
