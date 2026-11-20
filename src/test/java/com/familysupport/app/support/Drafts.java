package com.familysupport.app.support;

import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.benefit.domain.BenefitSource;
import com.familysupport.app.benefit.domain.LifeStage;
import com.familysupport.app.benefit.domain.RegionScope;
import com.familysupport.app.benefit.domain.SupportConditions;
import java.util.Set;

/** 테스트용 사업 초안 만들기. 기본값 = 전국 정부24 출산 사업, 조건 없음. */
public final class Drafts {

    private Drafts() {
    }

    public static Builder gov24(String id, String name) {
        return new Builder(BenefitSource.GOV24, id, name);
    }

    public static Builder bokjiroLocal(String id, String name) {
        return new Builder(BenefitSource.BOKJIRO_LOCAL, id, name);
    }

    public static final class Builder {
        private final BenefitSource source;
        private final String id;
        private final String name;
        private RegionScope scope = RegionScope.NATIONAL;
        private String sido;
        private String sigungu;
        private Set<LifeStage> stages = Set.of(LifeStage.BIRTH);
        private SupportConditions conditions;
        private String householdTypes;
        private String summary;
        private String modified = "20261101000000";
        private String supportType;
        private String registered;
        private String target;
        private String applyPeriod = "상시신청";
        private BenefitDraft.RawSource raw;

        private Builder(BenefitSource source, String id, String name) {
            this.source = source;
            this.id = id;
            this.name = name;
        }

        public Builder sido(String sido) {
            this.scope = RegionScope.SIDO;
            this.sido = sido;
            return this;
        }

        public Builder sigungu(String sido, String sigungu) {
            this.scope = RegionScope.SIGUNGU;
            this.sido = sido;
            this.sigungu = sigungu;
            return this;
        }

        public Builder stages(LifeStage... stages) {
            this.stages = Set.of(stages);
            return this;
        }

        public Builder conditions(Integer ageFrom, Integer ageTo, String incomeBands, String... flags) {
            this.conditions = new SupportConditions(ageFrom, ageTo, incomeBands, Set.of(flags));
            return this;
        }

        public Builder householdTypes(String householdTypes) {
            this.householdTypes = householdTypes;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder applyPeriod(String applyPeriod) {
            this.applyPeriod = applyPeriod;
            return this;
        }

        public Builder registered(String registered) {
            this.registered = registered;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder supportType(String supportType) {
            this.supportType = supportType;
            return this;
        }

        public Builder raw(String listBody, String conditionBody) {
            this.raw = new BenefitDraft.RawSource("JSON", listBody, conditionBody);
            return this;
        }

        public Builder modified(String modified) {
            this.modified = modified;
            return this;
        }

        public BenefitDraft build() {
            return new BenefitDraft(source, id, name, "테스트기관", scope, sido, sigungu, null, supportType, summary,
                    target, null, null, null, applyPeriod, "https://example.org/" + id, null,
                    stages, householdTypes, conditions, modified, registered, raw);
        }
    }
}
