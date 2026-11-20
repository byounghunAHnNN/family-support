package com.familysupport.app.benefit.domain;

/** 사업 정보를 가져온 곳. 같은 사업이 여러 출처에 있을 수 있어 카드마다 출처를 표시한다. */
public enum BenefitSource {
    GOV24("정부24"),
    BOKJIRO_CENTRAL("복지로"),
    BOKJIRO_LOCAL("복지로");

    private final String label;

    BenefitSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
