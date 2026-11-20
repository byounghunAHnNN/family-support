package com.familysupport.app.benefit.interfaces;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitCategories;
import com.familysupport.app.benefit.domain.BenefitSearchResult;
import com.familysupport.app.benefit.domain.TargetGroup;
import com.familysupport.app.benefit.domain.EligibilityStatus;
import com.familysupport.app.benefit.domain.EvaluatedBenefit;
import com.familysupport.app.benefit.domain.HouseholdProfile;
import com.familysupport.app.benefit.domain.LifeStage;
import com.familysupport.app.benefit.domain.RegionScope;
import com.familysupport.app.common.error.InvalidInputException;
import com.familysupport.app.benefit.domain.ApplyPeriod;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import org.springframework.stereotype.Component;

/** benefit 영역 변환 — Request → 도메인 입력, 도메인 → Response. 필드 옮기기만 한다. */
@Component
public class BenefitMapper {

    private static final DateTimeFormatter COLLECTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 상세에 보여줄 긴 원문 길이 상한(전체는 원문 링크에서). */
    private static final int LONG_TEXT_MAX = 1500;

    /** @throws InvalidInputException 알 수 없는 가구 상황 이름, 그 밖의 범위 위반(HouseholdProfile) */
    public HouseholdProfile toProfile(BenefitSearchRequest request) {
        return new HouseholdProfile(
                request.sido(),
                request.sigungu(),
                toStages(request.stages()),
                request.parentAge(),
                request.childAge(),
                request.householdSize(),
                request.annualIncomeManwon(),
                Boolean.TRUE.equals(request.dualIncome()),
                Boolean.TRUE.equals(request.noHome()),
                Boolean.TRUE.equals(request.multiChild()),
                Boolean.TRUE.equals(request.singleParent()),
                Boolean.TRUE.equals(request.disabled()),
                Boolean.TRUE.equals(request.lowIncome()),
                Boolean.TRUE.equals(request.multicultural()));
    }

    private static Set<LifeStage> toStages(List<String> names) {
        Set<LifeStage> stages = EnumSet.noneOf(LifeStage.class);
        if (names == null) {
            return stages;
        }
        for (String name : names) {
            try {
                stages.add(LifeStage.valueOf(name));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new InvalidInputException("알 수 없는 가구 상황입니다: " + name);
            }
        }
        return stages;
    }

    public BenefitSearchResponse toResponse(BenefitSearchResult result) {
        LocalDate today = result.today() != null ? result.today() : LocalDate.now();
        List<BenefitSearchResponse.Item> items = result.items().stream().map(e -> toItem(e, today)).toList();
        return new BenefitSearchResponse(
                items,
                result.excluded().stream().map(e -> toItem(e, today)).toList(),
                (int) items.stream().filter(i -> i.status().equals(EligibilityStatus.MATCHED.name())).count(),
                (int) items.stream().filter(i -> i.status().equals(EligibilityStatus.CHECK.name())).count(),
                result.totalBenefits(),
                result.medianIncomePercent().map(Math::round).orElse(null),
                result.lastCollectedAt().map(COLLECTED_AT::format).orElse(null));
    }

    private BenefitSearchResponse.Item toItem(EvaluatedBenefit evaluated, LocalDate today) {
        Benefit b = evaluated.benefit();
        ApplyPeriod period = b.period();
        BenefitCategories categories = b.categories();
        List<BenefitSearchResponse.Source> sources = new ArrayList<>();
        sources.add(toSource(b));
        evaluated.others().forEach(other -> sources.add(toSource(other)));
        return new BenefitSearchResponse.Item(
                b.getId(),
                b.getName(),
                b.getOrganization(),
                regionLabel(b),
                b.getScope().name(),
                b.getSource().label(),
                b.stages().stream().map(LifeStage::label).toList(),
                evaluated.eligibility().status().name(),
                evaluated.eligibility().status().label(),
                evaluated.eligibility().reasons(),
                categories.supportTypes().stream().map(Enum::name).toList(),
                categories.fields().stream().sorted().map(Enum::name).toList(),
                categories.targetGroups().stream().sorted().map(TargetGroup::label).toList(),
                period.label(today),
                period.status(today).name(),
                b.freshness(today).name(),
                sources);
    }

    private static BenefitSearchResponse.Source toSource(Benefit b) {
        return new BenefitSearchResponse.Source(
                b.getSource().label(),
                b.getOrganization(),
                b.getSummary(),
                cut(b.getTargetText()),
                cut(b.getCriteriaText()),
                cut(b.getSupportText()),
                cut(b.getApplyMethod()),
                b.getApplyPeriod(),
                b.getContact(),
                b.getDetailUrl(),
                b.getSourceModified());
    }

    private static String regionLabel(Benefit b) {
        if (b.getScope() == RegionScope.NATIONAL) {
            return "전국";
        }
        return b.getScope() == RegionScope.SIGUNGU ? b.getSido() + " " + b.getSigungu() : b.getSido();
    }

    private static String cut(String text) {
        if (text == null || text.length() <= LONG_TEXT_MAX) {
            return text;
        }
        return text.substring(0, LONG_TEXT_MAX) + "…";
    }

    public RegionsResponse toRegionsResponse(Map<String, SortedSet<String>> regions) {
        return new RegionsResponse(regions.entrySet().stream()
                .map(e -> new RegionsResponse.Sido(e.getKey(), List.copyOf(e.getValue())))
                .toList());
    }
}
