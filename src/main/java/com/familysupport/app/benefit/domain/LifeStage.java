package com.familysupport.app.benefit.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 서비스 대상 가구의 상황. 사업 1건은 여러 상황에 해당할 수 있다(예: 산모·신생아 = 출산 + 영유아). */
public enum LifeStage {
    PREPARING("예비부모·난임"),
    PREGNANT("임신 중"),
    BIRTH("출산·입양"),
    INFANT("영유아 자녀"),
    NEWLYWED("신혼");

    private final String label;

    LifeStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** DB 저장 형식(콤마 구분 이름) → 집합. 빈 값이면 빈 집합. */
    public static Set<LifeStage> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.noneOf(LifeStage.class);
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(LifeStage::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(LifeStage.class)));
    }

    /** 집합 → DB 저장 형식. enum 선언 순서로 정렬해 같은 집합은 항상 같은 문자열이 되게 한다. */
    public static String format(Set<LifeStage> stages) {
        return EnumSet.copyOf(stages.isEmpty() ? EnumSet.noneOf(LifeStage.class) : stages).stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }
}
