package com.familysupport.app.benefit.interfaces;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitRepository;
import com.familysupport.app.benefit.domain.LifeStage;
import com.familysupport.app.common.config.ApiNoStoreFilter;
import com.familysupport.app.support.Drafts;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 검색 API — 트랜잭션 없이 실제 저장·조회 흐름으로 돈다(open-in-view=false에서 응답 변환이 깨지지 않는지 함께 확인).
 * 롤백이 없어서 @AfterEach에서 직접 지운다.
 */
@SpringBootTest
class BenefitApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    BenefitRepository benefitRepository;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        // webAppContextSetup은 서블릿 필터를 자동으로 붙이지 않는다 — 캐시 금지 필터를 직접 붙인다
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(ApiNoStoreFilter.class)).build();
        LocalDateTime now = LocalDateTime.of(2026, 11, 16, 5, 0);
        benefitRepository.saveAll(List.of(
                Benefit.create(Drafts.gov24("A", "첫만남이용권 지원").stages(LifeStage.BIRTH)
                        .conditions(null, null, "YYYYY", "JA0410").build(), now),
                Benefit.create(Drafts.gov24("B", "(송파구)출산축하금 지원").sigungu("서울특별시", "송파구")
                        .stages(LifeStage.BIRTH).conditions(18, 44, "YYYYY", "JA0410").build(), now),
                Benefit.create(Drafts.gov24("C", "2026년 천사지원금 지급").sido("인천광역시")
                        .stages(LifeStage.INFANT).conditions(1, 7, "YYYYY", "JA0410").build(), now),
                Benefit.create(Drafts.bokjiroLocal("D", "출산.입양 장려금 지원").sigungu("인천광역시", "계양구")
                        .stages(LifeStage.BIRTH).build(), now)));
    }

    @AfterEach
    void tearDown() {
        benefitRepository.deleteAll(benefitRepository.findAll());
    }

    @Test
    void 송파구_출산_가구는_전국과_송파구_사업만_본다() throws Exception {
        mvc.perform(post("/api/benefits/search").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sido":"서울특별시","sigungu":"송파구","stages":["BIRTH"],"householdSize":3,"annualIncomeManwon":6000}
                        """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                // 가까운 지역(시군구) 사업이 먼저
                .andExpect(jsonPath("$.data.items[0].name").value("(송파구)출산축하금 지원"))
                .andExpect(jsonPath("$.data.items[0].regionLabel").value("서울특별시 송파구"))
                .andExpect(jsonPath("$.data.items[1].name").value("첫만남이용권 지원"))
                .andExpect(jsonPath("$.data.totalBenefits").value(4))
                .andExpect(jsonPath("$.data.medianIncomePercent").value(93))
                .andExpect(jsonPath("$.data.lastCollectedAt").value("2026-11-16 05:00"));
    }

    @Test
    void 아이_나이로_천사지원금을_거른다() throws Exception {
        mvc.perform(post("/api/benefits/search").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sido":"인천광역시","sigungu":"계양구","stages":["INFANT"],"childAge":9}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(post("/api/benefits/search").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sido":"인천광역시","sigungu":"계양구","stages":["INFANT"],"childAge":3}
                        """))
                .andExpect(jsonPath("$.data.items[0].name").value("2026년 천사지원금 지급"))
                .andExpect(jsonPath("$.data.items[0].status").value("MATCHED"));
    }

    @Test
    void 검색어로_좁힌다() throws Exception {
        mvc.perform(post("/api/benefits/search").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sido":"인천광역시","sigungu":"계양구","keyword":"장려 금"}
                        """))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sourceLabel").value("복지로"))
                .andExpect(jsonPath("$.data.items[0].status").value("CHECK"));
    }

    @Test
    void 대상_밖_지역이나_잘못된_값은_400() throws Exception {
        mvc.perform(post("/api/benefits/search").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sido":"부산광역시"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mvc.perform(post("/api/benefits/search").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sido":"서울특별시","stages":["UNKNOWN"]}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/benefits/search").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sido":"서울특별시","childAge":1.5}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 지역_목록은_대상_시도_전부와_사업이_있는_시군구() throws Exception {
        mvc.perform(get("/api/benefits/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sidos.length()").value(7))
                .andExpect(jsonPath("$.data.sidos[0].name").value("서울특별시"))
                .andExpect(jsonPath("$.data.sidos[0].sigungus[0]").value("송파구"))
                .andExpect(jsonPath("$.data.sidos[1].sigungus[0]").value("계양구"));
    }

    @Test
    void 수동_수집_API는_기본으로_꺼져_있다() throws Exception {
        mvc.perform(post("/api/collect")).andExpect(status().isNotFound());
        mvc.perform(get("/api/collect/status"))
                .andExpect(status().isOk())
                // 다른 테스트가 수집 기록을 남겼을 수 있어 값 대신 존재만 확인
                .andExpect(jsonPath("$.data.status").exists());
    }
}
