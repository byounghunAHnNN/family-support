package com.familysupport.app.collect.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.benefit.domain.LifeStage;
import com.familysupport.app.benefit.domain.Region;
import com.familysupport.app.benefit.domain.RegionScope;
import com.familysupport.app.benefit.domain.SupportConditions;
import com.familysupport.app.collect.domain.FeedException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 출처 응답 해석. 값은 2026-11-16 실제 호출 응답에서 가져왔다. */
class FeedParsingTest {

    @Test
    void 정부24_지원조건_산모신생아_건강관리() {
        // 실제 응답: 200% 초과(JA0205)만 빠져 있음, 나이 18~44
        Map<String, Object> row = new HashMap<>();
        row.put("서비스ID", "PTR000050390");
        row.put("JA0102", "Y");
        row.put("JA0110", 18);
        row.put("JA0111", 44);
        row.put("JA0201", "Y");
        row.put("JA0202", "Y");
        row.put("JA0203", "Y");
        row.put("JA0204", "Y");
        row.put("JA0205", null);
        row.put("JA0302", "Y");
        row.put("JA0303", "Y");
        row.put("JA0410", "Y");

        SupportConditions c = Gov24Feed.toConditions(row);

        assertThat(c.ageFrom()).isEqualTo(18);
        assertThat(c.ageTo()).isEqualTo(44);
        assertThat(c.incomeBands()).isEqualTo("YYYYN");
        assertThat(c.flags()).containsExactlyInAnyOrder("JA0102", "JA0302", "JA0303", "JA0410");
        assertThat(c.requiredHouseholdTypes()).isEmpty();
    }

    @Test
    void 정부24_소득_구간이_모두_비면_소득_정보_없음() {
        assertThat(Gov24Feed.toConditions(Map.of("JA0110", 1, "JA0111", 7)).incomeBands()).isNull();
    }

    @Test
    void 정부24_행을_사업_초안으로() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("서비스ID", "628000000781");
        row.put("서비스명", "2026년 천사지원금 지급");
        row.put("서비스목적요약", "2026년 천사지원금 지급");
        row.put("지원대상", "1~7세 아동");
        row.put("소관기관명", "인천광역시");
        row.put("소관기관유형", "광역시도");
        row.put("신청기한", "아동의 생일 이후 120일 이내");
        row.put("수정일시", "20260703120000");
        Region.Placement placement = Region.placeOrganization("인천광역시", "광역시도").orElseThrow();

        BenefitDraft draft = Gov24Feed.toDraft(row, placement,
                new SupportConditions(1, 7, "YYYYY", java.util.Set.of("JA0322", "JA0410")));

        assertThat(draft.id()).isEqualTo("GOV24:628000000781");
        assertThat(draft.scope()).isEqualTo(RegionScope.SIDO);
        assertThat(draft.sido()).isEqualTo("인천광역시");
        assertThat(draft.stages()).contains(LifeStage.INFANT);
        assertThat(draft.applyPeriod()).isEqualTo("아동의 생일 이후 120일 이내");
    }

    @Test
    void 복지로_XML_목록() {
        String xml = """
                <wantedList><resultCode>0</resultCode><resultMessage>SUCCESS</resultMessage>
                <totalCount>14</totalCount><pageNo>1</pageNo><numOfRows>100</numOfRows>
                <servList><servId>WLF00005538</servId><servNm>천사지원금 지급</servNm>
                <ctpvNm>인천광역시</ctpvNm><sggNm></sggNm><lifeNmArray>영유아</lifeNmArray>
                <servDtlLink>https://www.bokjiro.go.kr/x</servDtlLink><lastModYmd>20260703</lastModYmd></servList>
                <servList><servId>WLF1</servId><servNm>장수축하금</servNm><ctpvNm>인천광역시</ctpvNm>
                <sggNm>계양구</sggNm><lifeNmArray>노년</lifeNmArray></servList>
                </wantedList>""";

        XmlItems.Page page = XmlItems.parse(xml, "servList");

        assertThat(page.totalCount()).isEqualTo(14);
        assertThat(page.items()).hasSize(2);
        Optional<BenefitDraft> cheonsa = BokjiroLocalFeed.toDraft(page.items().get(0));
        assertThat(cheonsa).hasValueSatisfying(d -> {
            assertThat(d.scope()).isEqualTo(RegionScope.SIDO);
            assertThat(d.stages()).containsExactly(LifeStage.INFANT);
        });
        // 노년 사업은 대상이 아니라 버린다
        assertThat(BokjiroLocalFeed.toDraft(page.items().get(1))).isEmpty();
    }

    @Test
    void 복지로_인증_오류_응답은_실패로() {
        String xml = """
                <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg><returnReasonCode>30</returnReasonCode>
                </cmmMsgHeader></OpenAPI_ServiceResponse>""";
        assertThatThrownBy(() -> XmlItems.parse(xml, "servList"))
                .isInstanceOf(FeedException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    void 외부_엔티티가_든_XML은_읽지_않는다() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///c:/windows/win.ini\">]><x>&e;</x>";
        assertThatThrownBy(() -> XmlItems.parse(xml, "servList")).isInstanceOf(FeedException.class);
    }

    @Test
    void 쿼리_인코딩_정부24_조건_이름은_괄호와_콜론을_유지() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("serviceKey", "a+b/c=");
        params.put("cond[서비스ID::EQ]", "123");
        assertThat(PublicDataHttp.query(params))
                .isEqualTo("serviceKey=a%2Bb%2Fc%3D&cond[%EC%84%9C%EB%B9%84%EC%8A%A4ID::EQ]=123");
    }

    @Test
    void 복지로_지자체_상세_시행기간과_문장() {
        // 2026-11-20 실제 응답(맘편한 산후조리비 지원)에서 줄였다
        String xml = """
                <wantedDtl><resultCode>0</resultCode><resultMessage>SUCCESS</resultMessage>
                <servId>WLF00006046</servId><enfcBgngYmd>20250203</enfcBgngYmd><enfcEndYmd>99991231</enfcEndYmd>
                <sprtTrgtCn>인천시에 1년 이상 거주한 취약계층 산모를 지원합니다.&#13;*취약계층 : 수급자, 다태아 이상 출산 산모</sprtTrgtCn>
                <slctCritCn>출생일 기준 1년 전부터 인천시에 주민등록</slctCritCn>
                <alwServCn>인천e음 150만원</alwServCn><aplyMtdCn>정부24 신청</aplyMtdCn></wantedDtl>""";

        BokjiroDetail.Detail d = BokjiroDetail.parse(xml, true);

        assertThat(d.target()).contains("다태아").contains("다.\n*취약계층");
        assertThat(d.applyPeriod()).isEqualTo("상시 (시행 2025.02.03부터)");
        assertThat(d.applyMethod()).isEqualTo("정부24 신청");
        assertThat(BokjiroDetail.period("20260101", "20261231")).isEqualTo("2026.01.01~2026.12.31");
    }
}
