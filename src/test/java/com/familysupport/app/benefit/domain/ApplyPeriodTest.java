package com.familysupport.app.benefit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.familysupport.app.support.Drafts;
import com.familysupport.app.support.Profiles;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 신청기한 원문 해석 — 값은 2026-11-20 저장 데이터의 실제 문구. */
class ApplyPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Test
    void 상시와_사유_발생형은_열림() {
        assertThat(ApplyPeriod.parse("상시신청").kind()).isEqualTo(ApplyPeriod.Kind.ALWAYS);
        assertThat(ApplyPeriod.parse("신청불필요").label(TODAY)).isEqualTo("상시 신청");
        ApplyPeriod birth = ApplyPeriod.parse("출산일로부터 1년 이내(기간내 미신청시 소멸)");
        assertThat(birth.kind()).isEqualTo(ApplyPeriod.Kind.AFTER_EVENT);
        assertThat(birth.status(TODAY)).isEqualTo(ApplyPeriod.Status.OPEN);
    }

    @Test
    void 날짜_범위는_오늘_기준으로_마감_진행_예정을_가른다() {
        ApplyPeriod closed = ApplyPeriod.parse("2025.09.15~2025.12.31");
        assertThat(closed.status(TODAY)).isEqualTo(ApplyPeriod.Status.CLOSED);
        assertThat(closed.label(TODAY)).isEqualTo("마감됨 (2025.12.31)");

        ApplyPeriod open = ApplyPeriod.parse("2026. 9. 1. ~ 12. 31.");
        assertThat(open.status(TODAY)).isEqualTo(ApplyPeriod.Status.OPEN);
        assertThat(open.label(TODAY)).isEqualTo("~ 2026.12.31 (D-41)");

        assertThat(ApplyPeriod.parse("2026년 상반기").status(TODAY)).isEqualTo(ApplyPeriod.Status.CLOSED);
        assertThat(ApplyPeriod.parse("2027.01.02 ~ 2027.02.28").status(TODAY)).isEqualTo(ApplyPeriod.Status.UPCOMING);
    }

    @Test
    void 기관별_상이와_원문_없음은_판단하지_않는다() {
        assertThat(ApplyPeriod.parse("접수기관 별 상이").status(TODAY)).isEqualTo(ApplyPeriod.Status.UNKNOWN);
        assertThat(ApplyPeriod.parse(null).label(TODAY)).isEqualTo("신청 기한은 원문 확인");
    }

    @Test
    void 마감된_사업은_조건이_맞아도_확인_필요() {
        Benefit b = Benefit.create(Drafts.gov24("1", "출산축하금").applyPeriod("2025.09.15~2025.12.31")
                .conditions(null, null, "YYYYY", "JA0410").build(), LocalDateTime.of(2026, 11, 20, 5, 0));
        assertThat(b.evaluate(Profiles.seoulSongpa().build()).status()).isEqualTo(EligibilityStatus.MATCHED);
        Eligibility withToday = b.evaluate(Profiles.seoulSongpa().build(), TODAY);
        assertThat(withToday.status()).isEqualTo(EligibilityStatus.CHECK);
        assertThat(withToday.reasons()).anyMatch(r -> r.contains("지난 것으로 보입니다"));
    }

    @Test
    void NEW는_원문_등록일_30일_이내() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 20, 5, 0);
        assertThat(Benefit.create(Drafts.gov24("1", "a").registered("20261105090000").build(), now).freshness(TODAY))
                .isEqualTo(Freshness.NEW);
        assertThat(Benefit.create(Drafts.gov24("3", "c").registered("2025-01-01").build(), now).freshness(TODAY))
                .isEqualTo(Freshness.NONE);
    }

    @Test
    void 변경은_출처_수정일이_아니라_다시_받은_내용이_실제로_바뀐_때() {
        LocalDateTime first = LocalDateTime.of(2026, 11, 1, 5, 0);
        LocalDateTime later = LocalDateTime.of(2026, 11, 15, 5, 0);
        Benefit b = Benefit.create(Drafts.gov24("1", "출산축하금").registered("20250101").modified("20261101")
                .summary("50만원 지원").build(), first);
        // 처음 저장만 했으면 변경 아님(출처 수정일이 최근이어도)
        assertThat(b.freshness(TODAY)).isEqualTo(Freshness.NONE);

        // 수정일만 바뀌고 내용이 같으면 변경 아님 — 기관의 일괄 수정일 갱신
        b.refresh(Drafts.gov24("1", "출산축하금").registered("20250101").modified("20261114").summary("50만원 지원").build(), later);
        assertThat(b.freshness(TODAY)).isEqualTo(Freshness.NONE);

        // 문장이 바뀌면 변경
        b.refresh(Drafts.gov24("1", "출산축하금").registered("20250101").modified("20261114").summary("100만원 지원").build(), later);
        assertThat(b.freshness(TODAY)).isEqualTo(Freshness.UPDATED);
        assertThat(b.freshness(TODAY.plusDays(40))).isEqualTo(Freshness.NONE);
    }

    @Test
    void 비었던_문장이_채워지거나_빠진_것은_변경이_아니다() {
        // 상세 API가 한도 초과로 실패했다가 다음 날 채워지는 경우(2026-11-23 복지로 중앙)
        Benefit b = Benefit.create(Drafts.gov24("1", "아동수당").registered("20250101").build(), LocalDateTime.of(2026, 11, 1, 5, 0));
        b.refresh(Drafts.gov24("1", "아동수당").registered("20250101").target("만 8세 미만 아동").build(), LocalDateTime.of(2026, 11, 15, 5, 0));
        assertThat(b.freshness(TODAY)).isEqualTo(Freshness.NONE);
        b.refresh(Drafts.gov24("1", "아동수당").registered("20250101").build(), LocalDateTime.of(2026, 11, 16, 5, 0));
        assertThat(b.freshness(TODAY)).isEqualTo(Freshness.NONE);
    }
}
