package com.familysupport.app.benefit.domain;

import java.util.Optional;

/**
 * 2026년 기준 중위소득(월, 원) — 보건복지부 고시, 복지로 게시 자료로 확인(2026-11-16).
 * 7인 이상은 확인한 자료에 금액이 없어 환산하지 않는다(화면에서 "직접 확인"으로 안내).
 * 매년 8월 다음 해 기준이 고시되므로 해가 바뀌면 이 표를 바꾼다.
 */
public final class MedianIncome {

    private static final long[] MONTHLY_2026 = {
            0L,           // 0인(사용 안 함)
            2_564_238L,   // 1인
            4_199_292L,   // 2인
            5_359_036L,   // 3인
            6_494_738L,   // 4인
            7_556_719L,   // 5인
            8_555_952L    // 6인
    };

    public static final int MAX_HOUSEHOLD_SIZE = MONTHLY_2026.length - 1;

    private MedianIncome() {
    }

    /**
     * 연 소득(만원, 가구 합산)을 기준 중위소득 대비 %로 바꾼다. 연 소득 ÷ 12를 월 소득으로 본 단순 환산이다 —
     * 실제 사업은 건강보험료·소득인정액 등 다른 기준을 쓰는 경우가 많아 참고용이다.
     * @return 가구원 수가 표 범위(1~6인) 밖이면 empty
     */
    public static Optional<Double> percentOf(int householdSize, long annualIncomeManwon) {
        if (householdSize < 1 || householdSize > MAX_HOUSEHOLD_SIZE) {
            return Optional.empty();
        }
        double monthly = annualIncomeManwon * 10_000d / 12d;
        return Optional.of(monthly * 100d / MONTHLY_2026[householdSize]);
    }
}
