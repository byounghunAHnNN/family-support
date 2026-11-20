package com.familysupport.app.benefit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.familysupport.app.support.Drafts;
import com.familysupport.app.support.Profiles;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 내 상황 × 사업 조건 판정 규칙. "확실히 안 되는 것만 제외, 애매하면 확인 필요". */
class BenefitEvaluateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 5, 0);
    /** 소득 제한 없음(5구간 모두 허용) + 가구유형 제한 없음(JA0410). */
    private static final String ALL_BANDS = "YYYYY";

    private static Eligibility evaluate(Drafts.Builder draft, Profiles.Builder profile) {
        return Benefit.create(draft.build(), NOW).evaluate(profile.build());
    }

    @Nested
    class 지역 {

        @Test
        void 전국_사업은_어디서나_보인다() {
            assertThat(evaluate(Drafts.gov24("1", "첫만남이용권").conditions(null, null, ALL_BANDS, "JA0410"),
                    Profiles.of("인천광역시", "계양구")).status()).isEqualTo(EligibilityStatus.MATCHED);
        }

        @Test
        void 다른_시도_사업은_제외() {
            assertThat(evaluate(Drafts.gov24("1", "서울형 산후조리경비").sido("서울특별시"),
                    Profiles.of("인천광역시", "계양구")).excluded()).isTrue();
        }

        @Test
        void 다른_시군구_사업은_제외() {
            assertThat(evaluate(Drafts.gov24("1", "출산축하금").sigungu("서울특별시", "강남구"),
                    Profiles.seoulSongpa()).excluded()).isTrue();
        }

        @Test
        void 시군구를_고르지_않으면_같은_시도의_시군구_사업은_확인_필요() {
            Eligibility result = evaluate(
                    Drafts.gov24("1", "출산축하금").sigungu("서울특별시", "송파구").conditions(null, null, ALL_BANDS, "JA0410"),
                    Profiles.of("서울특별시", null));
            assertThat(result.status()).isEqualTo(EligibilityStatus.CHECK);
            assertThat(result.reasons()).anyMatch(r -> r.contains("송파구 주민"));
        }
    }

    @Nested
    class 가구_상황 {

        @Test
        void 고른_상황과_겹치지_않으면_제외() {
            assertThat(evaluate(Drafts.gov24("1", "난임 시술비").stages(LifeStage.PREPARING),
                    Profiles.seoulSongpa().stages(LifeStage.INFANT)).excluded()).isTrue();
        }

        @Test
        void 상황을_고르지_않으면_거르지_않는다() {
            assertThat(evaluate(Drafts.gov24("1", "난임 시술비").stages(LifeStage.PREPARING)
                            .conditions(null, null, ALL_BANDS, "JA0410"),
                    Profiles.seoulSongpa()).excluded()).isFalse();
        }
    }

    @Nested
    class 나이 {

        @Test
        void 아이_나이_범위_밖이면_제외_천사지원금_1에서_7세() {
            Drafts.Builder cheonsa = Drafts.gov24("1", "천사지원금").sido("인천광역시").stages(LifeStage.INFANT)
                    .conditions(1, 7, ALL_BANDS, "JA0410");
            assertThat(evaluate(cheonsa, Profiles.of("인천광역시", null).childAge(9)).excluded()).isTrue();
            assertThat(evaluate(cheonsa, Profiles.of("인천광역시", null).childAge(3)).status())
                    .isEqualTo(EligibilityStatus.MATCHED);
        }

        @Test
        void 아이_나이를_안_넣으면_확인_필요() {
            Eligibility result = evaluate(Drafts.gov24("1", "천사지원금").sido("인천광역시")
                            .conditions(1, 7, ALL_BANDS, "JA0410"),
                    Profiles.of("인천광역시", null));
            assertThat(result.status()).isEqualTo(EligibilityStatus.CHECK);
            assertThat(result.reasons()).anyMatch(r -> r.contains("1~7세"));
        }

        @Test
        void 신청자_나이_범위_밖은_제외하지_않고_확인_필요() {
            // 누구 나이인지 데이터에 없어서 거르지 않는다
            Eligibility result = evaluate(Drafts.gov24("1", "출산축하금").conditions(18, 44, ALL_BANDS, "JA0410"),
                    Profiles.seoulSongpa().parentAge(46));
            assertThat(result.status()).isEqualTo(EligibilityStatus.CHECK);
        }

        @Test
        void 나이_상한이_100세_이상이면_제한_없음으로_본다() {
            assertThat(evaluate(Drafts.gov24("1", "난임 시술비").conditions(18, 120, ALL_BANDS, "JA0410"),
                    Profiles.seoulSongpa().parentAge(50)).status()).isEqualTo(EligibilityStatus.MATCHED);
        }
    }

    @Nested
    class 소득 {

        /** 산모·신생아 건강관리: 200% 초과만 불가(실제 기준 150%는 101~200 구간 안). */
        private final Drafts.Builder postnatal = Drafts.gov24("1", "산모·신생아 건강관리").conditions(18, 44, "YYYYN", "JA0410");

        @Test
        void 허용하지_않는_구간이면_제외() {
            // 3인 가구 연 2억 = 월 1,667만 원 ≈ 중위 311%
            Eligibility result = evaluate(postnatal, Profiles.seoulSongpa().income(3, 20_000));
            assertThat(result.excluded()).isTrue();
            assertThat(result.reasons()).anyMatch(r -> r.contains("중위소득 200% 이하"));
        }

        @Test
        void 허용되는_가장_높은_구간이면_기준선을_확인하라고_남긴다() {
            // 3인 가구 연 9,000만 원 = 월 750만 원 ≈ 중위 140%
            Eligibility result = evaluate(postnatal, Profiles.seoulSongpa().income(3, 9_000));
            assertThat(result.status()).isEqualTo(EligibilityStatus.CHECK);
            assertThat(result.reasons()).anyMatch(r -> r.contains("101~200%") && r.contains("중위 140%"));
        }

        @Test
        void 경계가_아닌_구간이면_일치() {
            // 3인 가구 연 3,000만 원 ≈ 중위 47%
            assertThat(evaluate(postnatal, Profiles.seoulSongpa().income(3, 3_000)).status())
                    .isEqualTo(EligibilityStatus.MATCHED);
        }

        @Test
        void 소득을_안_넣으면_소득_기준이_있다고만_알린다() {
            Eligibility result = evaluate(postnatal, Profiles.seoulSongpa());
            assertThat(result.status()).isEqualTo(EligibilityStatus.CHECK);
            assertThat(result.reasons()).anyMatch(r -> r.contains("소득 기준이 있습니다"));
        }

        @Test
        void 칠인_이상_가구는_환산하지_않고_확인_필요() {
            assertThat(evaluate(postnatal, Profiles.seoulSongpa().income(7, 20_000)).status())
                    .isEqualTo(EligibilityStatus.CHECK);
        }
    }

    @Nested
    class 가구유형 {

        @Test
        void 무주택만_대상이면_무주택이_아닐_때_확인_필요() {
            Drafts.Builder housing = Drafts.gov24("1", "신혼 매입임대").stages(LifeStage.NEWLYWED)
                    .conditions(19, 44, "YYYYN", "JA0412");
            assertThat(evaluate(housing, Profiles.seoulSongpa().income(2, 3_000)).status())
                    .isEqualTo(EligibilityStatus.CHECK);
            assertThat(evaluate(housing, Profiles.seoulSongpa().income(2, 3_000).noHome()).status())
                    .isEqualTo(EligibilityStatus.MATCHED);
        }
    }

    @Nested
    class 복지로 {

        @Test
        void 조건_코드가_없어_항상_원문_확인으로_남긴다() {
            Eligibility result = evaluate(Drafts.bokjiroLocal("1", "출산축하금 지급").sigungu("서울특별시", "송파구")
                    .householdTypes("다자녀, 저소득"), Profiles.seoulSongpa());
            assertThat(result.status()).isEqualTo(EligibilityStatus.CHECK);
            assertThat(result.reasons()).anyMatch(r -> r.contains("관련 가구유형: 다자녀, 저소득"));
        }
    }

    @Test
    void 컬럼보다_긴_원문은_잘라서_저장한다() {
        // 11/16 첫 수집: 문의처 553자가 컬럼(500)을 넘어 저장 전체가 실패했다
        assertThat(Benefit.fit("가".repeat(5000), 4000)).hasSize(4000).endsWith("…");
        assertThat(Benefit.fit("짧은 값", 4000)).isEqualTo("짧은 값");
        assertThat(Benefit.fit(null, 10)).isNull();
    }

    @Test
    void 맞벌이면_맞벌이_언급을_알려준다() {
        Eligibility result = evaluate(Drafts.gov24("1", "아이돌봄").summary("맞벌이 가정 우선 지원")
                .conditions(null, null, ALL_BANDS, "JA0410"), Profiles.seoulSongpa().dualIncome());
        assertThat(result.reasons()).contains("맞벌이 가구 관련 내용이 있습니다.");
    }
}
