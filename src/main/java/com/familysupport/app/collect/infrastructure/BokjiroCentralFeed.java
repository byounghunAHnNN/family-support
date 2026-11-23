package com.familysupport.app.collect.infrastructure;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitClassifier;
import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.benefit.domain.BenefitSource;
import com.familysupport.app.benefit.domain.LifeStage;
import com.familysupport.app.benefit.domain.RegionScope;
import com.familysupport.app.collect.domain.BenefitFeed;
import com.familysupport.app.collect.domain.FeedException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 복지로 중앙부처 복지서비스 — 한국사회보장정보원. 전부 전국 사업이다.
 * 목록 응답의 요약·생애주기·가구유형·상세 링크에 상세 API 문장(지원대상·선정기준·지원내용)을 붙인다.
 * 목록에 수정일이 없어 상세는 사업마다 7일에 한 번만 새로 받고, 그 사이에는 저장해 둔 값을 쓴다({@link BokjiroDetail}).
 */
@Slf4j
@Component
public class BokjiroCentralFeed implements BenefitFeed {

    static final String LIST_URL =
            "https://apis.data.go.kr/B554287/NationalWelfareInformationsV001/NationalWelfarelistV001";
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 10;

    private final PublicDataHttp http;
    private final Clock clock;

    public BokjiroCentralFeed(PublicDataHttp http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    @Override
    public BenefitSource source() {
        return BenefitSource.BOKJIRO_CENTRAL;
    }

    @Override
    public List<BenefitDraft> fetch(Function<String, Optional<Benefit>> previous) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            XmlItems.Page result = http.get("복지로 중앙 " + page + "쪽", LIST_URL,
                    Map.of("callTp", "L", "pageNo", String.valueOf(page), "numOfRows", String.valueOf(PAGE_SIZE),
                            "srchKeyCode", "003"),
                    Duration.ofSeconds(90), 3, body -> XmlItems.parse(body, "servList"));
            rows.addAll(result.items());
            if (result.items().isEmpty() || rows.size() >= result.totalCount()) {
                List<BenefitDraft> drafts = rows.stream().map(BokjiroCentralFeed::toDraft).flatMap(Optional::stream).toList();
                log.info("복지로 중앙: 전체 {}건 중 대상 {}건", rows.size(), drafts.size());
                return BokjiroDetail.enrich(http, BokjiroDetail.CENTRAL_URL, false, drafts, previous, LocalDate.now(clock));
            }
        }
        throw new FeedException("복지로 중앙 목록이 " + MAX_PAGES + "쪽을 넘습니다.");
    }

    /** 대상 가구 상황이 없으면 empty(수집하지 않음). */
    static Optional<BenefitDraft> toDraft(Map<String, String> row) {
        BenefitClassifier.Input input = new BenefitClassifier.Input(
                row.get("servNm"), row.get("servDgst"), null, null, row.get("intrsThemaArray"), null, row.get("lifeArray"));
        Set<LifeStage> stages = BenefitClassifier.classify(input);
        // 대상 상황이 비어도 애매한 사업이면 남긴다('제외' 탭용)
        if (stages.isEmpty() && !BenefitClassifier.isBorderline(input)) {
            return Optional.empty();
        }
        return Optional.of(new BenefitDraft(
                BenefitSource.BOKJIRO_CENTRAL,
                row.get("servId"),
                row.get("servNm"),
                join(row.get("jurMnofNm"), row.get("jurOrgNm")),
                RegionScope.NATIONAL,
                null,
                null,
                blankToNull(row.get("intrsThemaArray")),
                blankToNull(row.get("srvPvsnNm")),
                blankToNull(row.get("servDgst")),
                null,
                null,
                null,
                "Y".equals(row.get("onapPsbltYn")) ? "온라인 신청 가능" : null,
                null,
                blankToNull(row.get("servDtlLink")),
                blankToNull(row.get("rprsCtadr")),
                stages,
                blankToNull(row.get("trgterIndvdlArray")),
                null,
                null,   // 목록에 수정일이 없다
                blankToNull(row.get("svcfrstRegTs")),
                new BenefitDraft.RawSource("XML", XmlItems.toXml("servList", row), null)));
    }

    private static String join(String a, String b) {
        return String.join(" ", blankToEmpty(a), blankToEmpty(b)).trim();
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
