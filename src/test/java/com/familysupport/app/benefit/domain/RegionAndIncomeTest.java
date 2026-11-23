package com.familysupport.app.benefit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familysupport.app.common.error.InvalidInputException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegionAndIncomeTest {

    @Test
    void 정부24_기관명으로_지역을_정한다() {
        assertThat(Region.placeOrganization("서울특별시 송파구", "시군구"))
                .contains(new Region.Placement(RegionScope.SIGUNGU, "서울특별시", "송파구"));
        assertThat(Region.placeOrganization("인천광역시", "광역시도"))
                .contains(new Region.Placement(RegionScope.SIDO, "인천광역시", null));
        assertThat(Region.placeOrganization("인천도시공사", "공공기관"))
                .contains(new Region.Placement(RegionScope.SIDO, "인천광역시", null));
        assertThat(Region.placeOrganization("보건복지부", "중앙행정기관"))
                .contains(new Region.Placement(RegionScope.NATIONAL, null, null));
        assertThat(Region.placeOrganization("경기도 수원시 장안구", "시군구"))
                .contains(new Region.Placement(RegionScope.SIGUNGU, "경기도", "수원시"));
    }

    @Test
    void 시도_뒤에_붙어_쓴_구_공단은_그_구의_사업() {
        // 11/16 첫 수집에서 강동구 공단 사업이 서울시 전체 사업으로 잘못 나왔다
        assertThat(Region.placeOrganization("서울특별시강동구도시관리공단", "지방공기업"))
                .contains(new Region.Placement(RegionScope.SIGUNGU, "서울특별시", "강동구"));
        assertThat(Region.placeOrganization("서울특별시중구시설관리공단", "지방공기업"))
                .contains(new Region.Placement(RegionScope.SIGUNGU, "서울특별시", "중구"));
        assertThat(Region.placeOrganization("인천광역시남제물포구도시관리공단", "지방공기업"))
                .contains(new Region.Placement(RegionScope.SIGUNGU, "인천광역시", "남제물포구"));
        // 시·도 단위 기관은 그대로 시·도 사업
        assertThat(Region.placeOrganization("대전광역시시설관리공단", "지방공기업"))
                .contains(new Region.Placement(RegionScope.SIDO, "대전광역시", null));
        assertThat(Region.placeOrganization("서울특별시교육청", "교육청"))
                .contains(new Region.Placement(RegionScope.SIDO, "서울특별시", null));
        assertThat(Region.placeOrganization("충청남도여성가족청소년사회서비스원", "공공기관"))
                .contains(new Region.Placement(RegionScope.SIDO, "충청남도", null));
    }

    @Test
    void 대상_밖_시도와_지역을_모르는_지역기관은_버린다() {
        assertThat(Region.placeOrganization("부산광역시 해운대구", "시군구")).isEmpty();
        assertThat(Region.placeOrganization("송파구시설관리공단", "지방공기업")).isEmpty();
    }

    @Test
    void 복지로_시군구_칸이_시군구가_아니면_시도_사업() {
        assertThat(Region.placeLocal("인천광역시", "인천광역시교육청"))
                .contains(new Region.Placement(RegionScope.SIDO, "인천광역시", null));
        assertThat(Region.placeLocal("인천광역시", "계양구"))
                .contains(new Region.Placement(RegionScope.SIGUNGU, "인천광역시", "계양구"));
        assertThat(Region.placeLocal("부산광역시", "해운대구")).isEmpty();
    }

    @Test
    void 중위소득_환산_2026년_기준() {
        // 4인 가구 월 6,494,738원 = 연 약 7,793.7만 원 → 100%
        assertThat(MedianIncome.percentOf(4, 7_794)).hasValueSatisfying(p -> assertThat(p).isBetween(99.9, 100.1));
        assertThat(MedianIncome.percentOf(7, 10_000)).isEmpty();
        assertThat(IncomeBand.of(50)).isEqualTo(IncomeBand.UP_TO_50);
        assertThat(IncomeBand.of(50.01)).isEqualTo(IncomeBand.UP_TO_75);
        assertThat(IncomeBand.of(200)).isEqualTo(IncomeBand.UP_TO_200);
        assertThat(IncomeBand.of(200.5)).isEqualTo(IncomeBand.OVER_200);
    }

    @Test
    void 대상_밖_시도는_입력_오류() {
        assertThatThrownBy(() -> new HouseholdProfile("부산광역시", null, Set.of(), null, null, null, null,
                false, false, false, false, false, false, false)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> new HouseholdProfile("서울특별시", null, Set.of(), -1, null, null, null,
                false, false, false, false, false, false, false)).isInstanceOf(InvalidInputException.class);
    }

    private static Set<LifeStage> classify(String name) {
        return BenefitClassifier.classify(new BenefitClassifier.Input(name, null, null, null, null, null, null));
    }

    @Test
    void 분류_키워드와_조건코드() {
        assertThat(classify("임산부 교통비 지원")).contains(LifeStage.PREGNANT);
        assertThat(classify("신혼부부 전세자금")).containsExactly(LifeStage.NEWLYWED);
        assertThat(classify("노인 일자리")).isEmpty();
        // 천사지원금: 문구에 키워드가 없어도 아이 나이 1~7세 조건으로 영유아 대상
        assertThat(BenefitClassifier.classify(new BenefitClassifier.Input("2026년 천사지원금 지급", null, null, "개인",
                null, new SupportConditions(1, 7, "YYYYY", Set.of("JA0410")), null))).contains(LifeStage.INFANT);
    }

    @Test
    void 정자는_선정자_결정자_같은_낱말_안에서는_예비부모로_보지_않는다() {
        // 2026-11-23 실제 문구: "전세사기피해 결정자 100만원", "난민인정자", "졸업예정자"
        assertThat(classify("전세사기피해 결정자 긴급생계비")).doesNotContain(LifeStage.PREPARING);
        assertThat(classify("난민인정자 정착 지원")).doesNotContain(LifeStage.PREPARING);
        assertThat(classify("선정자 교육비 지원")).doesNotContain(LifeStage.PREPARING);
        // 진짜 생식세포 '정자'는 그대로
        assertThat(classify("영구 불임 예상 난자·정자 냉동 지원사업")).contains(LifeStage.PREPARING);
        assertThat(classify("정자 동결 보존 비용 지원")).contains(LifeStage.PREPARING);
    }

    @Test
    void 분류_v6_잡음은_버린다() {
        // 기관 대상(정부24 사용자구분), 창업 보육, 사업명 제외어, 가정위탁, 농림축산어업
        assertThat(BenefitClassifier.classify(new BenefitClassifier.Input("직장어린이집 설치비용 지원", null, null,
                "법인/시설/단체", null, null, null))).isEmpty();
        assertThat(classify("스포츠산업 창업보육 지원")).isEmpty();
        assertThat(classify("4대궁, 종묘 및 조선왕릉 무료 관람")).isEmpty();
        assertThat(classify("가정위탁아동 양육비 지원")).isEmpty();
        assertThat(BenefitClassifier.classify(new BenefitClassifier.Input("농식품바우처 지원", "임산부 가구", null, "개인",
                "농림축산어업", null, null))).isNotEmpty(); // 강한 키워드(임산부)가 있으면 남긴다
        assertThat(BenefitClassifier.classify(new BenefitClassifier.Input("가축개량지원", "가축 돌봄", null, "개인",
                "농림축산어업", null, null))).isEmpty();
        // '돌봄'만 있는 영유아는 빠지고, 노인 대상은 통째로 빠진다
        assertThat(classify("일상돌봄 서비스")).doesNotContain(LifeStage.INFANT);
        assertThat(classify("노인맞춤돌봄서비스")).isEmpty();
    }

    @Test
    void 분류_v6_필요한_사업은_남긴다() {
        // v3에서 '청소년'·'장애인' 제외 문구에 걸려 빠졌던 사업
        assertThat(classify("청소년부모 아동양육비 지원사업")).contains(LifeStage.INFANT);
        assertThat(classify("한부모가족 아동양육비 지원")).contains(LifeStage.INFANT);
        assertThat(BenefitClassifier.classify(new BenefitClassifier.Input("발달재활서비스 바우처 지원",
                "장애인 가구 아동의 발달재활", null, "개인", null, null, null))).contains(LifeStage.INFANT);
    }

    @Test
    void 카테고리_출처값과_사업명() {
        BenefitCategories c = BenefitCategories.of("현금(융자)||현물", "보육·교육", "다문화가족 자녀 언어발달 지원", null);
        assertThat(c.supportTypes()).containsExactly(SupportType.LOAN, SupportType.GOODS);
        assertThat(c.fields()).contains(BenefitField.CHILDCARE).doesNotContain(BenefitField.EDU);
        assertThat(c.targetGroups()).containsExactly(TargetGroup.MULTICULTURAL);
        // 대상 문구가 아니라 사업명만 본다 — 아동수당은 특정 대상 아님
        assertThat(BenefitCategories.of("현금", "보육·교육", "아동수당", "외국인 아동 포함").targetGroups()).isEmpty();
        assertThat(BenefitCategories.of(null, null, "x", null).supportTypes()).containsExactly(SupportType.ETC);
    }
}
