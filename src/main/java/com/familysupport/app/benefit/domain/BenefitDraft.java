package com.familysupport.app.benefit.domain;

import java.util.Set;

/**
 * 수집기가 출처 응답을 정리해 만든 사업 1건(저장 전). {@link Benefit#create}·{@link Benefit#refresh}의 입력.
 *
 * @param supportTypeText 출처의 지원 방식 값(정부24 지원유형, 복지로 제공유형) — 카테고리 ① 계산용
 * @param conditions      정부24 지원조건. 복지로이거나 조건을 받지 못했으면 null
 * @param sourceRegistered 원문 등록일(정부24 등록일시, 복지로 중앙 최초등록일). 없으면 null — NEW 표시용
 * @param raw             출처 원문(보관용). 테스트 등에서 없으면 null
 */
public record BenefitDraft(
        BenefitSource source,
        String sourceId,
        String name,
        String organization,
        RegionScope scope,
        String sido,
        String sigungu,
        String fieldName,
        String supportTypeText,
        String summary,
        String targetText,
        String criteriaText,
        String supportText,
        String applyMethod,
        String applyPeriod,
        String detailUrl,
        String contact,
        Set<LifeStage> stages,
        String householdTypes,
        SupportConditions conditions,
        String sourceModified,
        String sourceRegistered,
        RawSource raw) {

    /**
     * 출처 원문.
     *
     * @param format        JSON(정부24) / XML(복지로)
     * @param listBody      목록 응답 중 이 사업 항목
     * @param conditionBody 정부24 지원조건 응답 원문. 이번에 부르지 않았으면(수정일 그대로라 재사용) null
     */
    public record RawSource(String format, String listBody, String conditionBody) {
    }

    /** 저장 ID = 출처 + 출처의 사업 ID. 같은 사업이라도 출처가 다르면 다른 행이다(화면에서 이름·지역으로 묶는다). */
    public String id() {
        return source.name() + ":" + sourceId;
    }

    /** 같은 사업 묶기 기준({@link Benefit#groupKeyOf}). */
    public String groupKey() {
        return Benefit.groupKeyOf(name, scope, sido, sigungu);
    }

    public BenefitDraft withStages(Set<LifeStage> newStages) {
        return new BenefitDraft(source, sourceId, name, organization, scope, sido, sigungu, fieldName, supportTypeText,
                summary, targetText, criteriaText, supportText, applyMethod, applyPeriod, detailUrl, contact, newStages,
                householdTypes, conditions, sourceModified, sourceRegistered, raw);
    }

    public BenefitDraft withSupportTypeText(String newSupportTypeText) {
        return new BenefitDraft(source, sourceId, name, organization, scope, sido, sigungu, fieldName, newSupportTypeText,
                summary, targetText, criteriaText, supportText, applyMethod, applyPeriod, detailUrl, contact, stages,
                householdTypes, conditions, sourceModified, sourceRegistered, raw);
    }

    public BenefitDraft withRaw(RawSource newRaw) {
        return new BenefitDraft(source, sourceId, name, organization, scope, sido, sigungu, fieldName, supportTypeText,
                summary, targetText, criteriaText, supportText, applyMethod, applyPeriod, detailUrl, contact, stages,
                householdTypes, conditions, sourceModified, sourceRegistered, newRaw);
    }

    /**
     * 상세 응답(복지로 상세 API)으로 문장을 채운다. null인 값은 기존 값을 그대로 둔다.
     *
     * @param detailBody 상세 응답 원문(보관용, 원문 보관의 보조 응답 칸에 들어간다). 다시 부르지 않았으면 null
     */
    public BenefitDraft withDetail(String newSummary, String newTarget, String newCriteria, String newSupport,
            String newApplyMethod, String newApplyPeriod, String detailBody) {
        RawSource newRaw = raw == null ? null : new RawSource(raw.format(), raw.listBody(), detailBody);
        return new BenefitDraft(source, sourceId, name, organization, scope, sido, sigungu, fieldName, supportTypeText,
                or(newSummary, summary), or(newTarget, targetText), or(newCriteria, criteriaText), or(newSupport, supportText),
                or(newApplyMethod, applyMethod), or(newApplyPeriod, applyPeriod), detailUrl, contact, stages,
                householdTypes, conditions, sourceModified, sourceRegistered, newRaw);
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
