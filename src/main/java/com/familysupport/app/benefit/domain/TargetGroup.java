package com.familysupport.app.benefit.domain;

/**
 * 특정 대상 — 누구 대상인가(카테고리 ③). 사업명에 이 대상이 있으면 붙인다.
 * 지원대상 문구까지 보면 "외국인 아동도 포함" 같은 포함·우대 문장에 걸려 아동수당까지 붙어서(실측) 사업명만 본다.
 * 내 상황에 해당 항목이 없으면 판정을 '확인 필요'로 내린다.
 */
public enum TargetGroup {
    DISABLED("장애"),
    LOW_INCOME("저소득(수급·차상위)"),
    MULTICULTURAL("다문화·외국인"),
    SINGLE_PARENT("한부모·조손"),
    VETERAN("보훈"),
    DEFECTOR("북한이탈");

    private final String label;

    TargetGroup(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
