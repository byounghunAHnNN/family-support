package com.familysupport.app.benefit.domain;

import com.familysupport.app.common.error.InvalidInputException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 사용자가 입력한 "내 상황". 서버에 저장하지 않고 검색 한 번에만 쓴다.
 * 거주 시·도만 필수이고 나머지는 비워 둘 수 있다 — 비운 항목은 그 조건으로 거르지 않는다.
 *
 * @param stages             해당하는 가구 상황(비우면 모든 상황)
 * @param parentAge          신청자(본인) 나이
 * @param childAge           (가장 어린) 아이 나이 — 0세 = 첫돌 전
 * @param householdSize      가구원 수
 * @param annualIncomeManwon 가구 합산 연 소득(만원, 세전)
 * @param disabled           장애인이 있는 가구
 * @param lowIncome          기초생활수급·차상위 가구
 * @param multicultural      다문화 가구
 */
public record HouseholdProfile(
        String sido,
        String sigungu,
        Set<LifeStage> stages,
        Integer parentAge,
        Integer childAge,
        Integer householdSize,
        Long annualIncomeManwon,
        boolean dualIncome,
        boolean noHome,
        boolean multiChild,
        boolean singleParent,
        boolean disabled,
        boolean lowIncome,
        boolean multicultural) {

    private static final int MAX_AGE = 120;
    private static final int MAX_HOUSEHOLD = 20;
    /** 입력 실수 방지용 상한(연 100억 원). */
    private static final long MAX_INCOME_MANWON = 1_000_000L;

    /**
     * @throws InvalidInputException 시·도가 없거나 대상 지역 밖, 나이·가구원 수·소득이 범위 밖
     */
    public HouseholdProfile {
        if (!Region.isTargetSido(sido)) {
            throw new InvalidInputException("거주 시·도를 선택해 주세요. (현재 서울·인천·경기·대전·세종·충북·충남만 지원합니다)");
        }
        sigungu = sigungu == null || sigungu.isBlank() ? null : sigungu.trim();
        stages = stages == null || stages.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(LifeStage.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(stages));
        checkRange(parentAge, 0, MAX_AGE, "본인 나이");
        checkRange(childAge, 0, MAX_AGE, "아이 나이");
        checkRange(householdSize, 1, MAX_HOUSEHOLD, "가구원 수");
        if (annualIncomeManwon != null && (annualIncomeManwon < 0 || annualIncomeManwon > MAX_INCOME_MANWON)) {
            throw new InvalidInputException("연 소득은 0 이상, 1,000,000만원 이하로 입력해 주세요.");
        }
    }

    private static void checkRange(Integer value, int min, int max, String name) {
        if (value != null && (value < min || value > max)) {
            throw new InvalidInputException(name + "는 " + min + "~" + max + " 사이로 입력해 주세요.");
        }
    }

    /** 소득·가구원 수가 모두 있고 표 범위 안이면 중위소득 대비 %. */
    public Optional<Double> medianIncomePercent() {
        if (annualIncomeManwon == null || householdSize == null) {
            return Optional.empty();
        }
        return MedianIncome.percentOf(householdSize, annualIncomeManwon);
    }

    public boolean incomeEntered() {
        return annualIncomeManwon != null && householdSize != null;
    }

    /** 이 가구가 해당하는 정부24 가구유형(입력으로 알 수 있는 것만). */
    public boolean hasHouseholdType(String label) {
        return switch (label) {
            case "다자녀가구" -> multiChild;
            case "무주택세대" -> noHome;
            case "한부모·조손가정" -> singleParent;
            case "1인가구" -> householdSize != null && householdSize == 1;
            default -> false;
        };
    }

    /** 특정 대상에 해당한다고 입력했는가. 보훈·북한이탈은 입력 칸이 없어 늘 '확인 필요'로 둔다. */
    public boolean belongsTo(TargetGroup group) {
        return switch (group) {
            case DISABLED -> disabled;
            case LOW_INCOME -> lowIncome;
            case MULTICULTURAL -> multicultural;
            case SINGLE_PARENT -> singleParent;
            case VETERAN, DEFECTOR -> false;
        };
    }
}
