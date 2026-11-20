package com.familysupport.app.benefit.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 검색 결과.
 *
 * @param items               제외되지 않은 사업(정렬됨)
 * @param excluded            제외된 사업 중 내 시·도·전국 사업(제외 이유와 함께 보여주기용, 정렬됨)
 * @param today               판정 기준일(신청기한 표시용). null이면 기한을 보지 않았다
 * @param totalBenefits       저장된 전체 사업 수
 * @param lastCollectedAt     가장 최근 수집 시각(저장된 사업이 없으면 empty)
 * @param medianIncomePercent 입력한 소득의 중위소득 대비 %(입력이 없거나 환산 불가면 empty)
 */
public record BenefitSearchResult(
        List<EvaluatedBenefit> items,
        List<EvaluatedBenefit> excluded,
        LocalDate today,
        int totalBenefits,
        Optional<LocalDateTime> lastCollectedAt,
        Optional<Double> medianIncomePercent) {
}
