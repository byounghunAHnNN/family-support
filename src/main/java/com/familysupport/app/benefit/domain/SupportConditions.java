package com.familysupport.app.benefit.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 정부24 지원조건(supportConditions)에서 판단에 쓰는 부분.
 * - 나이: JA0110~JA0111. 신청자 나이인지 아이 나이인지 데이터에 없어서, 상한이 18세 이하면 아이 나이로 본다.
 * - 소득: JA0201~JA0205 각 구간 허용 여부를 "YYYYN" 5글자로 보관. 정보가 없으면 null.
 * - 가구유형: JA04xx 중 Y인 코드(다자녀·무주택·한부모 등). JA0410(해당사항없음)이 있으면 가구유형 제한이 없는 것.
 * - 그 밖의 Y 코드(JA0301 등)는 {@link #flags}에 그대로 둔다(분류용).
 *
 * @param flags 값이 Y인 코드 전체(나이 제외)
 */
public record SupportConditions(Integer ageFrom, Integer ageTo, String incomeBands, Set<String> flags) {

    /** 아이 나이 조건으로 보는 상한. 이보다 크면 신청자(부모) 나이 조건으로 본다. */
    static final int CHILD_AGE_MAX = 18;
    /** 나이 상한이 이 값 이상이면 사실상 제한 없음(예: 18~120세). */
    private static final int NO_AGE_LIMIT = 100;
    /** 나이 하한이 이 값 이상이면 노인 대상 사업으로 본다(정부24 저장분: 60·65·75·80·90세 이상 등). */
    static final int SENIOR_AGE_FROM = 60;

    /** 가구유형 조건 코드 → 화면 문구. */
    static final List<String[]> HOUSEHOLD_CODES = List.of(
            new String[]{"JA0401", "다문화가족"},
            new String[]{"JA0402", "북한이탈주민"},
            new String[]{"JA0403", "한부모·조손가정"},
            new String[]{"JA0404", "1인가구"},
            new String[]{"JA0411", "다자녀가구"},
            new String[]{"JA0412", "무주택세대"},
            new String[]{"JA0413", "신규전입"},
            new String[]{"JA0414", "확대가족"});

    public SupportConditions {
        flags = flags == null ? Collections.emptySet() : Collections.unmodifiableSet(new TreeSet<>(flags));
    }

    public boolean flag(String code) {
        return flags.contains(code);
    }

    public boolean hasChildAgeRange() {
        return ageTo != null && ageTo <= CHILD_AGE_MAX;
    }

    public boolean hasAdultAgeLimit() {
        return ageTo != null && ageTo > CHILD_AGE_MAX && ageTo < NO_AGE_LIMIT;
    }

    /**
     * 상한 없이 하한만 있는 성인 나이 조건(예: 40~120세). 정부24는 "○세 이상"을 상한 120으로 준다 —
     * 상한만 보면 제한 없음이라 하한을 놓친다(2026-11-23: 65세 이상 폐렴구균이 30세에게 조건 일치로 나옴).
     */
    public boolean hasAdultAgeFloor() {
        return ageFrom != null && ageFrom > CHILD_AGE_MAX && (ageTo == null || ageTo >= NO_AGE_LIMIT);
    }

    /** 노인 대상(하한 60세 이상) — 임신·육아 가구의 신청자 나이로 보면 확실히 다르다. */
    public boolean isSeniorOnly() {
        return ageFrom != null && ageFrom >= SENIOR_AGE_FROM;
    }

    /** 해당 구간이 허용되는가. 소득 정보가 없으면 true(막지 않는다). */
    public boolean allows(IncomeBand band) {
        if (incomeBands == null || incomeBands.length() != IncomeBand.values().length) {
            return true;
        }
        return incomeBands.charAt(band.ordinal()) == 'Y';
    }

    /** 소득 제한이 있는가(허용 안 하는 구간이 하나라도 있는가). */
    public boolean hasIncomeLimit() {
        return incomeBands != null && incomeBands.indexOf('N') >= 0;
    }

    /**
     * 이 구간이 허용되는 가장 높은 구간인가 — 실제 기준선이 이 구간 안에 있어(예: 150%) 구간만으로는 판단할 수 없다.
     */
    public boolean isBoundary(IncomeBand band) {
        int next = band.ordinal() + 1;
        return allows(band) && next < IncomeBand.values().length && !allows(IncomeBand.values()[next]);
    }

    /**
     * 가구유형 제한 목록. JA0410(해당사항없음)이 Y이거나 Y인 가구유형이 없으면 제한 없음(빈 목록).
     */
    public List<String> requiredHouseholdTypes() {
        if (flag("JA0410")) {
            return List.of();
        }
        return HOUSEHOLD_CODES.stream().filter(c -> flag(c[0])).map(c -> c[1]).toList();
    }

    /** DB 저장용 — flags를 콤마로. */
    public String flagsCsv() {
        return String.join(",", flags);
    }

    public static Set<String> parseFlags(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
