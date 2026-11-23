package com.familysupport.app.benefit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지원사업 1건 — Aggregate 루트(하위 엔티티 없음. 정부24 조건은 같은 행의 보조 테이블 benefit_condition).
 * 출처 1곳의 사업 1건이다. 같은 사업이 정부24·복지로에 모두 있으면 행이 둘이고, 화면에서는 이름·지역이 같으면
 * 카드 1장으로 묶는다({@link #groupKey}, 2026-11-20 A안).
 * 수집할 때만 바뀌고({@link #refresh}), 조회 쪽은 읽기만 한다.
 */
@Entity
@Table(name = "benefit")
@SecondaryTable(name = "benefit_condition", pkJoinColumns = @PrimaryKeyJoinColumn(name = "benefit_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Benefit {

    /** NEW·변경 표시 기간(일). */
    private static final int FRESH_DAYS = 30;

    /*
     * 정부24 소득 칸(중위소득 5구간)은 "소득 기준이 있다"는 표시일 뿐 모든 신청자에게 걸리는 조건이 아닌 경우가 있다.
     * 소득 제한 사업 208건을 원문과 대조한 결과(2026-11-20, 설계 문서 폴더 '소득조건-대조-20261120.txt'):
     *  - 원문에 "소득과 관계없이" 등이 명시된 사업 5건 → 소득 기준을 적용하지 않는다(사용자 결정: 조건 일치).
     *  - 소득과 무관한 대상(장애·다태아 등)이 함께 나열됐거나 "초과 시 일부 부담"인 사업 → 넘어도 제외하지 않고 확인 필요.
     */
    private static final Pattern NO_INCOME_LIMIT = Pattern.compile(
            "소득(과|에)?\\s?(관계\\s?없|상관\\s?없|무관)|소득\\s?기준\\s?(없|미적용|폐지)|소득\\s?제한\\s?(없|폐지)"
                    + "|소득수준\\s?(관계\\s?없|무관)|소득\\s?요건\\s?(없|폐지)");
    private static final Pattern INCOME_FREE_ROUTE = Pattern.compile(
            "장애|다태아|쌍둥이|쌍생아|쌍태아|희귀|북한이탈|탈북|새터민|청소년\\s?한?부모|둘째\\s?아?\\s?이상|셋째\\s?아?\\s?이상"
                    + "|다자녀|국가유공자|초과.{0,20}(본인\\s?부담|이용료|자부담|일부)");

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private BenefitSource source;

    private String sourceId;
    private String name;
    private String organization;

    @Enumerated(EnumType.STRING)
    private RegionScope scope;

    private String sido;
    private String sigungu;
    private String fieldName;
    private String summary;
    private String targetText;
    private String criteriaText;
    private String supportText;
    private String applyMethod;
    private String applyPeriod;
    private String detailUrl;
    private String contact;
    private String lifeStages;
    private String householdTypes;
    private String sourceModified;
    private String sourceRegistered;
    // 카테고리 3축(콤마 구분 enum 이름) — BenefitCategories
    private String supportTypes;
    private String fields;
    private String targetGroups;

    // 정부24 지원조건 — benefit_condition 테이블. 행이 없으면(fetched_at이 null이면) 조건 정보가 없는 것
    @Column(table = "benefit_condition")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer ageFrom;
    @Column(table = "benefit_condition")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer ageTo;
    @Column(table = "benefit_condition")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String incomeBands;
    @Column(table = "benefit_condition", name = "flags")
    private String conditionFlags;
    @Column(table = "benefit_condition", name = "fetched_at")
    private LocalDateTime conditionsFetchedAt;

    @Column(nullable = false)
    private LocalDateTime collectedAt;

    /** 우리가 다시 받았을 때 원문 내용이 실제로 바뀐 시각. 처음 저장한 뒤 바뀐 적 없으면 null. */
    private LocalDateTime contentChangedAt;

    /** 새 사업 저장용. */
    public static Benefit create(BenefitDraft draft, LocalDateTime collectedAt) {
        Benefit benefit = new Benefit();
        benefit.id = draft.id();
        benefit.source = draft.source();
        benefit.sourceId = draft.sourceId();
        benefit.refresh(draft, collectedAt);
        return benefit;
    }

    /**
     * 다시 수집한 내용으로 바꾼다. ID·출처는 그대로다.
     * 원문 문구는 컬럼 길이(db/schema.sql)에 맞춰 자른다 — 사업 1건의 긴 문구 때문에 수집 전체가 실패하지 않게.
     * 잘린 경우 끝에 "…"이 붙고, 전체 원문은 공식 링크에서 본다.
     */
    public void refresh(BenefitDraft draft, LocalDateTime collectedAt) {
        List<String> before = this.collectedAt == null ? null : contentFields();
        this.name = fit(draft.name(), 1000);
        this.organization = fit(draft.organization(), 1000);
        this.scope = draft.scope();
        this.sido = draft.sido();
        this.sigungu = draft.sigungu();
        this.fieldName = fit(draft.fieldName(), 1000);
        this.summary = fit(draft.summary(), 20000);
        this.targetText = fit(draft.targetText(), 20000);
        this.criteriaText = fit(draft.criteriaText(), 20000);
        this.supportText = fit(draft.supportText(), 20000);
        this.applyMethod = fit(draft.applyMethod(), 20000);
        this.applyPeriod = fit(draft.applyPeriod(), 4000);
        // 링크는 자르면 깨지므로 길이 초과면 버린다
        this.detailUrl = draft.detailUrl() != null && draft.detailUrl().length() <= 2000 ? draft.detailUrl() : null;
        this.contact = fit(draft.contact(), 4000);
        this.lifeStages = LifeStage.format(draft.stages());
        this.householdTypes = fit(draft.householdTypes(), 1000);
        SupportConditions conditions = draft.conditions();
        this.conditionsFetchedAt = conditions == null ? null : collectedAt;
        this.ageFrom = conditions == null ? null : conditions.ageFrom();
        this.ageTo = conditions == null ? null : conditions.ageTo();
        this.incomeBands = conditions == null ? null : conditions.incomeBands();
        this.conditionFlags = conditions == null ? null : conditions.flagsCsv();
        this.sourceModified = draft.sourceModified();
        this.sourceRegistered = draft.sourceRegistered();
        BenefitCategories categories = BenefitCategories.of(draft.supportTypeText(), draft.fieldName(),
                draft.name(), draft.summary());
        this.supportTypes = categories.supportTypesCsv();
        this.fields = categories.fieldsCsv();
        this.targetGroups = categories.targetGroupsCsv();
        this.collectedAt = collectedAt;
        if (before != null && contentChanged(before, contentFields())) {
            this.contentChangedAt = collectedAt;
        }
    }

    /**
     * '변경' 판단에 쓰는 내용 — 사용자가 보는 원문 문장과 조건만. 우리가 계산하는 값(대상 상황·카테고리)과
     * 출처의 수정일·조회수는 넣지 않는다(2026-11-23: 출처 수정일 기준이면 46%가 '변경', 원문 해시는 조회수 때문에 매일 바뀜).
     */
    private List<String> contentFields() {
        return java.util.Arrays.asList(name, summary, targetText, criteriaText, supportText, applyMethod, applyPeriod,
                contact, householdTypes, ageFrom == null ? null : ageFrom.toString(),
                ageTo == null ? null : ageTo.toString(), incomeBands);
    }

    /**
     * 전과 후 모두 값이 있는데 다르면 바뀐 것. 비었다가 채워지거나(상세 API가 늦게 성공) 값이 빠진 것(호출 실패)은
     * 출처 내용이 바뀐 게 아닐 수 있어 변경으로 보지 않는다.
     */
    static boolean contentChanged(List<String> before, List<String> after) {
        for (int i = 0; i < before.size(); i++) {
            String b = before.get(i);
            String a = after.get(i);
            if (b != null && a != null && !b.equals(a)) {
                return true;
            }
        }
        return false;
    }

    public BenefitCategories categories() {
        return BenefitCategories.parse(supportTypes, fields, targetGroups);
    }

    /**
     * 같은 사업 묶기 기준 — 이름(공백·기호·끝의 '지원사업/사업/지원/서비스' 무시) + 지역.
     * 전체 데이터에서 이 기준으로 묶이는 152묶음을 확인했을 때 다른 사업이 묶인 경우는 없었다(2026-11-20).
     */
    public String groupKey() {
        return groupKeyOf(name, scope, sido, sigungu);
    }

    public static String groupKeyOf(String name, RegionScope scope, String sido, String sigungu) {
        String n = nz(name).replaceAll("[\\s()\\[\\]·ㆍ\\-_「」『』<>]", "")
                .replaceAll("(지원사업|사업|지원|서비스)$", "");
        return n + "|" + scope + "|" + nz(sido) + "|" + nz(sigungu);
    }

    /** 조건 데이터가 있는 정부24 사업인가(묶음의 대표·판정 기준). */
    public boolean hasConditions() {
        return conditionsFetchedAt != null;
    }

    public Set<LifeStage> stages() {
        return LifeStage.parse(lifeStages);
    }

    /** 정부24 지원조건. 복지로 사업이거나 조건을 받지 못했으면 empty. */
    public Optional<SupportConditions> supportConditions() {
        if (conditionsFetchedAt == null) {
            return Optional.empty();
        }
        return Optional.of(new SupportConditions(ageFrom, ageTo, incomeBands, SupportConditions.parseFlags(conditionFlags)));
    }

    /** 검색어가 사업명·요약·지원대상·지원내용·기관명 중 어디에든 들어 있는가(대소문자·공백 무시). */
    public boolean matchesKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = normalize(keyword);
        return List.of(nz(name), nz(summary), nz(targetText), nz(supportText), nz(organization)).stream()
                .anyMatch(text -> normalize(text).contains(needle));
    }

    /**
     * 내 상황으로 이 사업을 판정한다. 확실히 안 되는 것만 EXCLUDED, 데이터로 판단할 수 없는 조건은 CHECK.
     * 순서: 지역 → 가구 상황 → 특정 대상(사업명) → (정부24) 나이·소득·가구유형 → 참고 사항.
     */
    public Eligibility evaluate(HouseholdProfile profile) {
        return evaluate(profile, null);
    }

    /** @param today 오늘(신청기한 판단용). null이면 기한을 보지 않는다. */
    public Eligibility evaluate(HouseholdProfile profile, LocalDate today) {
        Eligibility.Builder result = new Eligibility.Builder();
        evaluateRegion(profile, result);
        if (stages().isEmpty()) {
            // 애매해서 대상 상황을 붙이지 않은 사업(BenefitClassifier.isBorderline) — '제외' 탭에서만 보인다
            result.exclude("임신·출산·육아·신혼 가구 대상으로 보기 어려운 사업으로 분류했습니다 — 필요하면 원문을 확인하세요.");
        } else if (!profile.stages().isEmpty() && Collections.disjoint(stages(), profile.stages())) {
            result.exclude("선택한 가구 상황의 사업이 아닙니다.");
        }
        // 사업명에 특정 대상(장애·저소득 등)이 있는데 내 상황에 없으면 확인 필요 — 확실히 아니라고 단정하지는 않는다.
        // 카드에 이유가 2개까지만 보여 가장 중요한 이 이유를 먼저 넣는다.
        categories().targetGroups().stream().sorted()
                .filter(group -> !profile.belongsTo(group))
                .forEach(group -> result.check(group.label() + " 대상 사업입니다 — 해당되는지 확인하세요."));
        supportConditions().ifPresentOrElse(
                conditions -> evaluateConditions(conditions, profile, result),
                () -> result.check(source == BenefitSource.GOV24
                        ? "세부 조건 정보를 받지 못했습니다 — 원문에서 확인하세요."
                        : "세부 조건(나이·소득)은 원문에서 확인하세요."));
        if (today != null) {
            evaluatePeriod(today, result);
        }
        if (householdTypes != null && !householdTypes.isBlank()) {
            result.info("관련 가구유형: " + householdTypes);
        }
        if (profile.dualIncome() && containsAny("맞벌이")) {
            result.info("맞벌이 가구 관련 내용이 있습니다.");
        }
        return result.build();
    }

    /**
     * 새로 등록됐거나 최근 바뀐 사업인가(30일).
     * NEW는 출처 등록일 기준(복지로 지자체는 등록일이 없어 NEW가 없다).
     * '변경'은 출처 수정일이 아니라 우리가 다시 받아 비교했을 때 내용이 실제로 바뀐 시각({@link #contentChangedAt}) 기준이다 —
     * 기관이 수정일만 일괄로 바꾸는 경우가 많아 수정일 기준이면 46%(927건)에 '변경'이 붙었다(2026-11-20).
     */
    public Freshness freshness(LocalDate today) {
        LocalDate since = today.minusDays(FRESH_DAYS);
        if (isOnOrAfter(sourceRegistered, since)) {
            return Freshness.NEW;
        }
        return contentChangedAt != null && !contentChangedAt.toLocalDate().isBefore(since)
                ? Freshness.UPDATED : Freshness.NONE;
    }

    /** 원문 날짜(yyyyMMdd…, yyyy-MM-dd…)의 날짜 부분이 기준일 이후인가. 읽을 수 없으면 false. */
    private static boolean isOnOrAfter(String raw, LocalDate since) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            return false;
        }
        try {
            return !LocalDate.parse(digits.substring(0, 8), java.time.format.DateTimeFormatter.BASIC_ISO_DATE).isBefore(since);
        } catch (java.time.DateTimeException e) {
            return false;
        }
    }

    /** 신청기한 원문 해석 결과. */
    public ApplyPeriod period() {
        return ApplyPeriod.parse(applyPeriod);
    }

    /** 기간이 지났으면 확인 필요(해마다 다시 공고하는 사업이 많아 제외하지는 않는다), 아직 시작 전이면 참고. */
    private void evaluatePeriod(LocalDate today, Eligibility.Builder result) {
        ApplyPeriod p = period();
        switch (p.status(today)) {
            case CLOSED -> result.check("신청 기간(" + p.text() + ")이 지난 것으로 보입니다 — 새 공고가 있는지 확인하세요.");
            case UPCOMING -> result.info("신청 기간이 아직 시작되지 않았습니다(" + p.text() + ").");
            default -> {
            }
        }
    }

    /** 다른 지역 사업은 제외. 시군구를 고르지 않았으면 같은 시·도의 시군구 사업은 확인 필요로 남긴다. */
    private void evaluateRegion(HouseholdProfile profile, Eligibility.Builder result) {
        if (scope == RegionScope.NATIONAL) {
            return;
        }
        if (!profile.sido().equals(sido)) {
            result.exclude("다른 시·도의 사업입니다.");
            return;
        }
        if (scope == RegionScope.SIGUNGU) {
            if (profile.sigungu() == null) {
                result.check(sigungu + " 주민 대상입니다.");
            } else if (!profile.sigungu().equals(sigungu)) {
                result.exclude("다른 시·군·구의 사업입니다.");
            }
        }
    }

    private void evaluateConditions(SupportConditions c, HouseholdProfile profile, Eligibility.Builder result) {
        String ageRange = ageRangeText(c);
        if (c.hasChildAgeRange()) {
            // 아이 나이 조건 — 입력했으면 확실히 거를 수 있다
            if (profile.childAge() == null) {
                result.check("아이 나이 " + ageRange + " 대상입니다.");
            } else if (outOfRange(profile.childAge(), c)) {
                result.exclude("아이 나이 조건(" + ageRange + ")에 맞지 않습니다.");
            }
        } else if (c.isSeniorOnly()) {
            // 60세 이상 대상 — 내 나이가 하한보다 낮으면 확실히 아니다
            if (profile.parentAge() == null) {
                result.check(c.ageFrom() + "세 이상 대상 사업입니다 — 해당되는지 확인하세요.");
            } else if (profile.parentAge() < c.ageFrom()) {
                result.exclude(c.ageFrom() + "세 이상 대상 사업입니다(입력한 나이 " + profile.parentAge() + "세).");
            } else if (outOfRange(profile.parentAge(), c)) {
                result.check("나이 조건(" + ageRange + ")을 확인하세요.");
            }
        } else if ((c.hasAdultAgeLimit() || c.hasAdultAgeFloor())
                && profile.parentAge() != null && outOfRange(profile.parentAge(), c)) {
            // 신청자 나이로 보이지만 누구 나이인지 데이터에 없어 제외하지 않는다
            result.check("나이 조건(" + ageRange + ")을 확인하세요.");
        }

        if (c.hasIncomeLimit() && NO_INCOME_LIMIT.matcher(eligibilityText()).find()) {
            // 정부24 소득 칸과 달리 원문에 "소득과 관계없이" 지원한다고 적혀 있다 — 원문을 따른다(2026-11-20 대조: 고위험 임산부 의료비 등)
            result.info("원문에 '소득과 관계없이' 지원한다고 적혀 있어 소득 기준을 적용하지 않았습니다.");
        } else if (c.hasIncomeLimit()) {
            Optional<Double> percent = profile.medianIncomePercent();
            if (!profile.incomeEntered()) {
                result.check("소득 기준이 있습니다(" + incomeLimitText(c) + ").");
            } else if (percent.isEmpty()) {
                result.check("소득 기준이 있습니다(" + incomeLimitText(c) + ") — 7인 이상 가구는 직접 확인하세요.");
            } else {
                IncomeBand band = IncomeBand.of(percent.get());
                String mine = "내 소득 약 중위 " + Math.round(percent.get()) + "%";
                if (!c.allows(band) && INCOME_FREE_ROUTE.matcher(eligibilityText()).find()) {
                    // 소득 칸은 '대상 중 일부'에만 걸린 기준일 수 있다(예: 맘편한 산후조리비 — 다태아·장애 산모는 소득 무관)
                    result.check("소득 기준(" + incomeLimitText(c) + ")을 넘지만, 다태아·장애 등 다른 대상이면 해당될 수 있습니다 — "
                            + "원문을 확인하세요(" + mine + ").");
                } else if (!c.allows(band)) {
                    result.exclude("소득 기준(" + incomeLimitText(c) + ")을 넘습니다 — " + mine + ".");
                } else if (c.isBoundary(band)) {
                    result.check("소득 기준선이 '" + band.label() + "' 구간 안에 있습니다 — 원문 기준을 확인하세요(" + mine + ").");
                }
            }
        }

        List<String> required = c.requiredHouseholdTypes();
        if (!required.isEmpty() && required.stream().noneMatch(profile::hasHouseholdType)) {
            result.check("가구 조건: " + String.join("·", required) + " 중 하나에 해당해야 할 수 있습니다.");
        }
    }

    private static boolean outOfRange(int age, SupportConditions c) {
        return (c.ageFrom() != null && age < c.ageFrom()) || (c.ageTo() != null && age > c.ageTo());
    }

    private static String ageRangeText(SupportConditions c) {
        return (c.ageFrom() == null ? "" : c.ageFrom()) + "~" + (c.ageTo() == null ? "" : c.ageTo()) + "세";
    }

    /** 허용 구간이 아래부터 이어져 있으면 "중위소득 N% 이하", 아니면 허용 구간 이름 나열. */
    private static String incomeLimitText(SupportConditions c) {
        int[] caps = {50, 75, 100, 200};
        IncomeBand[] bands = IncomeBand.values();
        int highest = -1;
        for (int i = 0; i < bands.length && c.allows(bands[i]); i++) {
            highest = i;
        }
        boolean contiguous = highest >= 0;
        for (int i = highest + 1; i < bands.length; i++) {
            contiguous &= !c.allows(bands[i]);
        }
        if (contiguous && highest < caps.length) {
            return "중위소득 " + caps[highest] + "% 이하 구간";
        }
        return String.join(", ", java.util.Arrays.stream(bands).filter(c::allows).map(IncomeBand::label).toList());
    }

    /** 지원대상 + 선정기준 원문 — 소득 칸이 원문과 맞는지 볼 때 쓴다. */
    private String eligibilityText() {
        return nz(targetText) + " " + nz(criteriaText);
    }

    private boolean containsAny(String word) {
        return List.of(nz(name), nz(summary), nz(targetText), nz(supportText), nz(criteriaText)).stream()
                .anyMatch(text -> text.contains(word));
    }

    /** 최대 길이를 넘으면 잘라서 끝에 "…"을 붙인다. */
    static String fit(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
