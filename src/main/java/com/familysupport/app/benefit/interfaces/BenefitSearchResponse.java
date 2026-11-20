package com.familysupport.app.benefit.interfaces;

import java.util.List;

/**
 * 검색 응답.
 *
 * @param items               조건 일치·확인 필요 사업
 * @param excluded            제외된 사업(내 시·도·전국만) — 제외 이유는 reasons
 * @param medianIncomePercent 입력한 소득의 중위소득 대비 %(입력이 없거나 환산 불가면 null)
 * @param lastCollectedAt     마지막 수집 시각(yyyy-MM-dd HH:mm, 없으면 null)
 */
public record BenefitSearchResponse(
        List<Item> items,
        List<Item> excluded,
        int matchedCount,
        int checkCount,
        int totalBenefits,
        Long medianIncomePercent,
        String lastCollectedAt) {

    /**
     * 카드 1장(같은 사업 묶음의 대표).
     *
     * @param supportTypes 지원 방식 코드(SupportType 이름) — 첫 번째가 카드 배지
     * @param fields       분야 코드(BenefitField 이름)
     * @param targetGroups 특정 대상 이름(표시용)
     * @param periodLabel  신청기한 짧은 문구(상시 신청, ~ 2026.12.31 (D-41), 마감됨 …)
     * @param periodStatus OPEN / UPCOMING / CLOSED / UNKNOWN
     * @param freshness    NEW(원문 등록 30일 이내) / UPDATED(원문 수정 30일 이내) / NONE
     * @param sources      출처별 원문(대표 먼저). 같은 사업이 다른 출처에도 있으면 2개 이상
     */
    public record Item(
            String id,
            String name,
            String organization,
            String regionLabel,
            String scope,
            String sourceLabel,
            List<String> stages,
            String status,
            String statusLabel,
            List<String> reasons,
            List<String> supportTypes,
            List<String> fields,
            List<String> targetGroups,
            String periodLabel,
            String periodStatus,
            String freshness,
            List<Source> sources) {
    }

    /** 출처 1곳의 원문(상세 팝업 탭 1개). */
    public record Source(
            String sourceLabel,
            String organization,
            String summary,
            String targetText,
            String criteriaText,
            String supportText,
            String applyMethod,
            String applyPeriod,
            String contact,
            String detailUrl,
            String sourceModified) {
    }
}
