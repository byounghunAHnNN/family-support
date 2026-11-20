package com.familysupport.app.benefit.domain;

/** 지원 방식 — 무엇을 받나(카테고리 ①). 출처의 지원유형·제공유형 값에서 정한다. */
public enum SupportType {
    CASH("현금"),
    DISCOUNT("감면"),
    VOUCHER("바우처·이용권"),
    SERVICE("서비스"),
    GOODS("현물·시설"),
    LOAN("융자"),
    ETC("기타");

    private final String label;

    SupportType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
