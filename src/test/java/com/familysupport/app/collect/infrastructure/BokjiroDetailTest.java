package com.familysupport.app.collect.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.support.Drafts;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 상세 호출 줄이기 — 받아 둔 상세 재사용, 중앙 7일 순번, 일일 한도 초과 시 중단(2026-11-23 429 실측). */
class BokjiroDetailTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 5, 0);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final String DETAIL = "<wantedDtl><resultCode>0</resultCode><servId>X</servId>"
            + "<tgtrDtlCn>새 지원대상</tgtrDtlCn><slctCritCn>새 선정기준</slctCritCn></wantedDtl>";
    private static final String QUOTA = "<OpenAPI_ServiceResponse><cmmMsgHeader>"
            + "<errMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</errMsg></cmmMsgHeader></OpenAPI_ServiceResponse>";

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    /** n번째 호출에 돌려줄 (상태코드, 본문). 마지막 것을 계속 쓴다. */
    private List<Map.Entry<Integer, String>> responses;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/detail", exchange -> {
            Map.Entry<Integer, String> r = responses.get(Math.min(calls.getAndIncrement(), responses.size() - 1));
            byte[] body = r.getValue().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(r.getKey(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/detail";
    }

    private static Benefit saved(BenefitDraft draft) {
        return Benefit.create(draft, NOW);
    }

    /** 오늘이 순번 날이 아닌 중앙 사업 ID를 찾는다. */
    private static String centralIdNotDueToday() {
        int today = Math.floorMod(TODAY.toEpochDay(), BokjiroDetail.CENTRAL_REFRESH_DAYS);
        for (int i = 0; ; i++) {
            String id = "WLF" + i;
            if (BokjiroDetail.refreshSlot(id) != today) {
                return id;
            }
        }
    }

    @Test
    void 처음_보는_사업과_상세가_빈_사업은_부른다() {
        BenefitDraft draft = Drafts.bokjiroCentral("WLF1", "아동수당").build();
        assertThat(BokjiroDetail.needsCall(draft, null, false, TODAY)).isTrue();
        assertThat(BokjiroDetail.needsCall(draft, saved(draft), false, TODAY)).isTrue();
    }

    @Test
    void 중앙은_상세가_있으면_7일_중_자기_순번_날에만_부른다() {
        String id = centralIdNotDueToday();
        BenefitDraft draft = Drafts.bokjiroCentral(id, "아동수당").build();
        Benefit prev = saved(Drafts.bokjiroCentral(id, "아동수당").target("지난번 지원대상").build());

        assertThat(BokjiroDetail.needsCall(draft, prev, false, TODAY)).isFalse();
        long due = java.util.stream.IntStream.range(0, 7)
                .filter(d -> BokjiroDetail.needsCall(draft, prev, false, TODAY.plusDays(d))).count();
        assertThat(due).isEqualTo(1);
    }

    @Test
    void 지자체는_원문_수정일이_바뀌었을_때만_부른다() {
        Benefit prev = saved(Drafts.bokjiroLocal("L1", "산후조리비").modified("20261101").target("대상").build());
        assertThat(BokjiroDetail.needsCall(Drafts.bokjiroLocal("L1", "산후조리비").modified("20261101").build(),
                prev, true, TODAY)).isFalse();
        assertThat(BokjiroDetail.needsCall(Drafts.bokjiroLocal("L1", "산후조리비").modified("20261120").build(),
                prev, true, TODAY)).isTrue();
    }

    @Test
    void 한도_초과가_나면_다시_시도하지_않고_남은_사업은_지난번_상세를_쓴다() {
        responses = List.of(Map.entry(200, DETAIL), Map.entry(429, QUOTA));
        List<BenefitDraft> drafts = List.of(
                Drafts.bokjiroCentral("A", "가").build(),
                Drafts.bokjiroCentral("B", "나").build(),
                Drafts.bokjiroCentral("C", "다").build());
        Benefit prevC = saved(Drafts.bokjiroCentral("C", "다").target("C 지난번 대상").summary("C 요약").build());
        // C는 상세가 있지만 B 다음 차례 — 순번과 무관하게 한도 초과 뒤라 부르지 않아야 한다
        Map<String, Optional<Benefit>> prev = Map.of("BOKJIRO_CENTRAL:C", Optional.of(prevC));

        List<BenefitDraft> result = BokjiroDetail.enrich(new PublicDataHttp("KEY"), url(), false, drafts,
                id -> prev.getOrDefault(id, Optional.empty()), TODAY);

        assertThat(calls.get()).isEqualTo(2);                       // A 성공, B 429 한 번(재시도 없음), C 호출 안 함
        assertThat(result.get(0).targetText()).isEqualTo("새 지원대상");
        assertThat(result.get(1).targetText()).isNull();             // 지난번 값도 없으면 목록 내용만
        assertThat(result.get(2).targetText()).isEqualTo("C 지난번 대상");
        assertThat(result.get(2).summary()).isEqualTo("C 요약");
    }
}
