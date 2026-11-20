package com.familysupport.app.benefit.domain;

/**
 * 정부24 지원조건의 기준중위소득 구간(JA0201~JA0205). 정부24가 주는 소득 조건은 이 5구간뿐이라
 * "중위소득 150% 이하" 같은 실제 기준선은 구간 안 어딘가에 있다 — 그래서 경계 구간은 '확인 필요'로 남긴다.
 */
public enum IncomeBand {
    UP_TO_50("JA0201", "중위소득 50% 이하"),
    UP_TO_75("JA0202", "중위소득 51~75%"),
    UP_TO_100("JA0203", "중위소득 76~100%"),
    UP_TO_200("JA0204", "중위소득 101~200%"),
    OVER_200("JA0205", "중위소득 200% 초과");

    private final String code;
    private final String label;

    IncomeBand(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 중위소득 대비 비율(%) → 구간. 구간 경계는 이하(≤)로 판단한다. */
    public static IncomeBand of(double percent) {
        if (percent <= 50) {
            return UP_TO_50;
        }
        if (percent <= 75) {
            return UP_TO_75;
        }
        if (percent <= 100) {
            return UP_TO_100;
        }
        if (percent <= 200) {
            return UP_TO_200;
        }
        return OVER_200;
    }
}
