package com.familysupport.app.benefit.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 저장된 사업 전체 — 도메인 컬렉션. 거르기·정렬·묶기를 여기서 한다(Repository는 findAll만 쓴다).
 * 사업 수가 수천 건 규모라 전부 읽어 Java로 거르는 비용을 감수한다.
 */
public record Benefits(List<Benefit> items) {

    /**
     * 정렬: 조건 일치 먼저 → 내 지역에 가까운 사업(시군구 → 시도 → 전국) → 사업명.
     * 가까운 지역 사업을 위로 올리는 이유: 전국 사업은 다른 곳에서도 알기 쉽고, 지역 사업은 놓치기 쉽다.
     */
    private static final Comparator<EvaluatedBenefit> ORDER = Comparator
            .comparing((EvaluatedBenefit e) -> e.eligibility().status())
            .thenComparing(e -> e.benefit().getScope(), Comparator.reverseOrder())
            .thenComparing(e -> e.benefit().getName());

    public Benefits {
        items = List.copyOf(items);
    }

    /**
     * 내 상황으로 판정해 제외되지 않은 사업만, 검색어가 있으면 그 단어가 들어간 사업만.
     * 이름·지역이 같은 사업(출처만 다름)은 카드 1장으로 묶는다 — 대표는 조건 데이터가 있는 정부24 사업, 없으면
     * 판정이 가장 좋은 사업이고, 묶음의 판정은 대표의 판정이다(대표가 제외면 묶음 전체가 빠진다).
     *
     * <p>제외된 사업도 내 시·도와 전국 사업이면 따로 돌려준다 — "없음"과 "있지만 내 조건에선 제외"를 화면에서
     * 구분하게 하려고(2026-11-20). 다른 시·도 사업은 수가 많고 쓸모가 적어 넣지 않는다.
     */
    public BenefitSearchResult search(HouseholdProfile profile, String keyword) {
        return search(profile, keyword, null);
    }

    /** @param today 오늘(신청기한 판단용). null이면 기한을 보지 않는다. */
    public BenefitSearchResult search(HouseholdProfile profile, String keyword, LocalDate today) {
        Map<String, List<Benefit>> groups = new LinkedHashMap<>();
        items.stream()
                .filter(b -> b.matchesKeyword(keyword))
                .forEach(b -> groups.computeIfAbsent(b.groupKey(), k -> new ArrayList<>()).add(b));
        List<EvaluatedBenefit> all = groups.values().stream()
                .map(group -> represent(group, profile, today))
                .sorted(ORDER)
                .toList();
        List<EvaluatedBenefit> found = all.stream().filter(e -> !e.eligibility().excluded()).toList();
        List<EvaluatedBenefit> excluded = all.stream()
                .filter(e -> e.eligibility().excluded())
                .filter(e -> e.benefit().getScope() == RegionScope.NATIONAL || profile.sido().equals(e.benefit().getSido()))
                .toList();
        return new BenefitSearchResult(found, excluded, today, items.size(), items.stream()
                .map(Benefit::getCollectedAt)
                .max(Comparator.<LocalDateTime>naturalOrder()),
                profile.medianIncomePercent());
    }

    private static EvaluatedBenefit represent(List<Benefit> group, HouseholdProfile profile, LocalDate today) {
        List<EvaluatedBenefit> evaluated = group.stream()
                .map(b -> new EvaluatedBenefit(b, b.evaluate(profile, today), List.of()))
                .sorted(Comparator.comparing((EvaluatedBenefit e) -> !e.benefit().hasConditions())
                        .thenComparing(e -> e.eligibility().status())
                        .thenComparing(e -> e.benefit().getSource()))
                .toList();
        EvaluatedBenefit lead = evaluated.get(0);
        List<Benefit> others = evaluated.stream().skip(1).map(EvaluatedBenefit::benefit).toList();
        return new EvaluatedBenefit(lead.benefit(), lead.eligibility(), others);
    }

    /**
     * 화면 선택 목록 — 대상 시·도 전부 + 각 시·도에서 사업이 있는 시·군·구.
     * 시군구 목록을 코드에 고정하지 않는 이유: 행정구역 개편(예: 인천 2026 개편)을 데이터가 따라가게 하려고.
     */
    public Map<String, SortedSet<String>> regions() {
        Map<String, SortedSet<String>> regions = new TreeMap<>(Comparator.comparingInt(Region.TARGET_SIDOS::indexOf));
        Region.TARGET_SIDOS.forEach(sido -> regions.put(sido, new TreeSet<>()));
        items.stream()
                .filter(b -> b.getScope() == RegionScope.SIGUNGU && regions.containsKey(b.getSido()))
                .forEach(b -> regions.get(b.getSido()).add(b.getSigungu()));
        return regions;
    }
}
