package com.familysupport.app.benefit.domain;

/**
 * 내 상황과 사업 조건을 비교한 결과.
 * 조건 데이터가 거칠어서 "확실히 안 되는 것"만 제외하고, 애매하면 확인 필요로 남긴다.
 */
public enum EligibilityStatus {
    /** 확인 가능한 조건이 모두 맞음(최종 판단은 공고 원문). */
    MATCHED("조건 일치"),
    /** 일부 조건을 데이터로 판단할 수 없음 — 원문 확인 필요. */
    CHECK("확인 필요"),
    /** 확실히 해당하지 않음 — 기본 목록에서 빼고, '제외' 탭에서 이유와 함께 보여준다. */
    EXCLUDED("제외");

    private final String label;

    EligibilityStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
