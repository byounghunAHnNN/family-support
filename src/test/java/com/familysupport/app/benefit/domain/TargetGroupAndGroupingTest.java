package com.familysupport.app.benefit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.familysupport.app.support.Drafts;
import com.familysupport.app.support.Profiles;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 특정 대상(사업명) 판정과 출처 중복 카드 묶기(2026-11-20 확정). */
class TargetGroupAndGroupingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 5, 0);

    @Test
    void 특정_대상_사업은_내_상황에_없으면_확인_필요() {
        Benefit b = Benefit.create(Drafts.gov24("1", "발달장애인 부모상담지원사업")
                .conditions(null, null, "YYYYY", "JA0410").build(), NOW);

        Eligibility mine = b.evaluate(Profiles.seoulSongpa().build());
        assertThat(mine.status()).isEqualTo(EligibilityStatus.CHECK);
        assertThat(mine.reasons().get(0)).contains("장애 대상");

        assertThat(b.evaluate(Profiles.seoulSongpa().disabled().build()).status()).isEqualTo(EligibilityStatus.MATCHED);
    }

    @Test
    void 이름과_지역이_같으면_한_장으로_묶고_정부24_판정을_쓴다() {
        Benefit gov = Benefit.create(Drafts.gov24("1", "첫만남이용권 지원").conditions(null, null, "YYYYY", "JA0410").build(), NOW);
        Benefit bok = Benefit.create(Drafts.bokjiroLocal("W1", "첫만남이용권").build(), NOW);
        Benefit other = Benefit.create(Drafts.gov24("2", "출산축하금").conditions(null, null, "YYYYY", "JA0410").build(), NOW);

        BenefitSearchResult result = new Benefits(List.of(bok, gov, other)).search(Profiles.seoulSongpa().build(), null);

        assertThat(result.items()).hasSize(2);
        EvaluatedBenefit first = result.items().stream()
                .filter(e -> e.benefit().getName().startsWith("첫만남")).findFirst().orElseThrow();
        assertThat(first.benefit().getSource()).isEqualTo(BenefitSource.GOV24);
        assertThat(first.eligibility().status()).isEqualTo(EligibilityStatus.MATCHED);
        assertThat(first.others()).extracting(Benefit::getSource).containsExactly(BenefitSource.BOKJIRO_LOCAL);
    }

    @Test
    void 지역이_다르면_묶지_않는다() {
        Benefit a = Benefit.create(Drafts.gov24("1", "출산장려금 지원").sigungu("서울특별시", "송파구").build(), NOW);
        Benefit b = Benefit.create(Drafts.gov24("2", "출산장려금 지원").sido("서울특별시").build(), NOW);
        assertThat(a.groupKey()).isNotEqualTo(b.groupKey());
    }

    @Test
    void 한쪽_출처만_빠진_같은_사업은_다른_출처의_상황으로_남기고_짝이_없으면_상황_없이_남긴다() {
        BenefitDraft govDropped = Drafts.gov24("1", "출생축하금 지원").sigungu("인천광역시", "계양구").stages().build();
        BenefitDraft bokKept = Drafts.bokjiroLocal("W1", "출생축하금").sigungu("인천광역시", "계양구").stages(LifeStage.BIRTH).build();
        BenefitDraft lonely = Drafts.gov24("2", "구민 정보화교육").stages().build();

        List<BenefitDraft> result = BenefitClassifier.alignAcrossSources(List.of(govDropped, bokKept, lonely));

        assertThat(result).extracting(BenefitDraft::id).containsExactly("GOV24:1", "BOKJIRO_LOCAL:W1", "GOV24:2");
        assertThat(result.get(0).stages()).containsExactly(LifeStage.BIRTH);
        // 짝이 없는 애매한 사업은 상황 없이 남는다 → 판정에서 '제외'(이유와 함께)
        assertThat(result.get(2).stages()).isEmpty();
        Eligibility lonelyResult = Benefit.create(result.get(2), NOW).evaluate(Profiles.seoulSongpa().build());
        assertThat(lonelyResult.excluded()).isTrue();
        assertThat(lonelyResult.reasons().get(0)).contains("대상으로 보기 어려운");
    }

    @Test
    void 제외된_사업은_내_시도와_전국만_이유와_함께_따로_돌려준다() {
        Benefit otherGu = Benefit.create(Drafts.gov24("1", "산후조리비 지원").sigungu("서울특별시", "강남구").build(), NOW);
        Benefit otherSido = Benefit.create(Drafts.gov24("2", "산후조리비 지원").sido("인천광역시").build(), NOW);
        Benefit mine = Benefit.create(Drafts.gov24("3", "출산축하금").sigungu("서울특별시", "송파구")
                .conditions(null, null, "YYYYY", "JA0410").build(), NOW);

        BenefitSearchResult result = new Benefits(List.of(otherGu, otherSido, mine)).search(Profiles.seoulSongpa().build(), null);

        assertThat(result.items()).extracting(e -> e.benefit().getId()).containsExactly("GOV24:3");
        assertThat(result.excluded()).extracting(e -> e.benefit().getId()).containsExactly("GOV24:1");
        assertThat(result.excluded().get(0).eligibility().reasons()).anyMatch(r -> r.contains("다른 시·군·구"));
    }

    @Test
    void 원문에_소득과_관계없이라고_적혀_있으면_소득_칸을_적용하지_않는다() {
        Benefit b = Benefit.create(Drafts.gov24("1", "고위험 임산부 의료비 지원")
                .target("○ 19대 고위험 임신질환으로 입원치료 받은 임산부(*’24년부터 가구 소득과 관계없이 지원)")
                .conditions(null, null, "YYYYN", "JA0410").build(), NOW);

        Eligibility result = b.evaluate(Profiles.seoulSongpa().income(3, 30_000).build()); // 중위 약 470%

        assertThat(result.status()).isEqualTo(EligibilityStatus.MATCHED);
        assertThat(result.reasons()).anyMatch(r -> r.contains("소득과 관계없이"));
    }

    @Test
    void 소득_무관_대상이_나열된_사업은_소득을_넘어도_확인_필요() {
        Drafts.Builder mompyeon = Drafts.gov24("1", "맘편한 산후조리비 지원").sido("서울특별시")
                .target("취약계층 산모: 기초생활수급자, 차상위계층, 장애인 또는 그 배우자, 다태아 이상 출산 산모")
                .conditions(null, null, "YYNNN", "JA0410");
        Eligibility result = Benefit.create(mompyeon.build(), NOW).evaluate(Profiles.seoulSongpa().income(3, 7_000).build());
        assertThat(result.status()).isEqualTo(EligibilityStatus.CHECK);
        assertThat(result.reasons().get(0)).contains("다른 대상이면 해당될 수 있습니다");

        // 다른 대상이 나열되지 않은 소득 제한 사업은 지금처럼 제외
        Benefit plain = Benefit.create(Drafts.gov24("2", "저소득 출산가정 지원").sido("서울특별시")
                .target("기준중위소득 75% 이하 출산가정").conditions(null, null, "YYNNN", "JA0410").build(), NOW);
        assertThat(plain.evaluate(Profiles.seoulSongpa().income(3, 7_000).build()).excluded()).isTrue();
    }
}
