package com.familysupport.app.support;

import com.familysupport.app.benefit.domain.HouseholdProfile;
import com.familysupport.app.benefit.domain.LifeStage;
import java.util.Set;

/** 테스트용 내 상황. 기본값 = 서울 송파구, 나머지 비움. */
public final class Profiles {

    private Profiles() {
    }

    public static Builder seoulSongpa() {
        return new Builder("서울특별시", "송파구");
    }

    public static Builder of(String sido, String sigungu) {
        return new Builder(sido, sigungu);
    }

    public static final class Builder {
        private final String sido;
        private final String sigungu;
        private Set<LifeStage> stages = Set.of();
        private Integer parentAge;
        private Integer childAge;
        private Integer householdSize;
        private Long income;
        private boolean dualIncome;
        private boolean noHome;
        private boolean multiChild;
        private boolean disabled;

        private Builder(String sido, String sigungu) {
            this.sido = sido;
            this.sigungu = sigungu;
        }

        public Builder stages(LifeStage... stages) {
            this.stages = Set.of(stages);
            return this;
        }

        public Builder parentAge(int age) {
            this.parentAge = age;
            return this;
        }

        public Builder childAge(int age) {
            this.childAge = age;
            return this;
        }

        public Builder income(int householdSize, long annualManwon) {
            this.householdSize = householdSize;
            this.income = annualManwon;
            return this;
        }

        public Builder dualIncome() {
            this.dualIncome = true;
            return this;
        }

        public Builder noHome() {
            this.noHome = true;
            return this;
        }

        public Builder multiChild() {
            this.multiChild = true;
            return this;
        }

        public Builder disabled() {
            this.disabled = true;
            return this;
        }

        public HouseholdProfile build() {
            return new HouseholdProfile(sido, sigungu, stages, parentAge, childAge, householdSize, income,
                    dualIncome, noHome, multiChild, false, disabled, false, false);
        }
    }
}
