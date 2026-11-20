package com.familysupport.app.benefit.domain;

/** 사업이 적용되는 지역 범위. 전국 사업은 어디 살든, 시도·시군구 사업은 그 지역 주민만. */
public enum RegionScope {
    NATIONAL("전국"),
    SIDO("시·도"),
    SIGUNGU("시·군·구");

    private final String label;

    RegionScope(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
