package com.familysupport.app.benefit.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 판정 결과와 그 이유.
 *
 * @param reasons 화면에 보여줄 문장(제외 이유, 확인할 조건, 참고 사항)
 */
public record Eligibility(EligibilityStatus status, List<String> reasons) {

    public Eligibility {
        reasons = List.copyOf(reasons);
    }

    public boolean excluded() {
        return status == EligibilityStatus.EXCLUDED;
    }

    /** 판정 중에 이유를 모으는 도구. 제외 > 확인 필요 > 일치 순으로 상태가 정해진다. */
    static final class Builder {
        private final List<String> excludes = new ArrayList<>();
        private final List<String> checks = new ArrayList<>();
        private final List<String> infos = new ArrayList<>();

        Builder exclude(String reason) {
            excludes.add(reason);
            return this;
        }

        Builder check(String reason) {
            checks.add(reason);
            return this;
        }

        Builder info(String reason) {
            infos.add(reason);
            return this;
        }

        Eligibility build() {
            if (!excludes.isEmpty()) {
                return new Eligibility(EligibilityStatus.EXCLUDED, excludes);
            }
            List<String> all = new ArrayList<>(checks);
            all.addAll(infos);
            return new Eligibility(checks.isEmpty() ? EligibilityStatus.MATCHED : EligibilityStatus.CHECK, all);
        }
    }
}
