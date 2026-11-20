package com.familysupport.app.benefit.domain;

/** 분야 — 어떤 일에 쓰나(카테고리 ②). 출처 분야 값 + 사업명·요약 키워드로 정한다. 한 사업이 여러 분야일 수 있다. */
public enum BenefitField {
    PREGNANCY("임신·출산"),
    CHILDCARE("보육·돌봄"),
    HEALTH("의료·건강"),
    LIVING("생활·에너지"),
    HOUSING("주거"),
    WORK("일·휴가"),
    EDU("교육·문화"),
    ETC("기타");

    private final String label;

    BenefitField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
