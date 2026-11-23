package com.familysupport.app.collect.infrastructure;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitClassifier;
import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.benefit.domain.BenefitSource;
import com.familysupport.app.benefit.domain.LifeStage;
import com.familysupport.app.benefit.domain.Region;
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
 * 복지로 지자체 복지서비스 — 한국사회보장정보원. 대상 시·도마다 100건씩 나눠 받는다.
 * 응답이 느려(인천 첫 쪽 25.6초 실측) 대기 시간을 길게 두고 다시 시도한다.
 * 시·도 하나라도 끝내 실패하면 출처 전체를 실패로 본다 — 일부만 저장하면 실패한 시·도 사업이 지워지기 때문.
 */
@Slf4j
@Component
public class BokjiroLocalFeed implements BenefitFeed {

    static final String LIST_URL =
            "https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations/LcgvWelfarelist";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final PublicDataHttp http;
    private final Clock clock;

    public BokjiroLocalFeed(PublicDataHttp http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    @Override
    public BenefitSource source() {
        return BenefitSource.BOKJIRO_LOCAL;
    }

    @Override
    public List<BenefitDraft> fetch(Function<String, Optional<Benefit>> previous) {
        List<BenefitDraft> drafts = new ArrayList<>();
        for (String sido : Region.TARGET_SIDOS) {
            List<Map<String, String>> rows = fetchSido(sido);
            List<BenefitDraft> found = rows.stream().map(BokjiroLocalFeed::toDraft).flatMap(Optional::stream).toList();
            log.info("복지로 지자체 {}: 전체 {}건 중 대상 {}건", sido, rows.size(), found.size());
            drafts.addAll(found);
        }
        // 상세(지원대상·선정기준·지원내용·시행기간) — 원문 수정일이 같으면 지난번 상세를 다시 쓴다
        return BokjiroDetail.enrich(http, BokjiroDetail.LOCAL_URL, true, drafts, previous, LocalDate.now(clock));
    }

    private List<Map<String, String>> fetchSido(String sido) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            XmlItems.Page result = http.get("복지로 지자체 " + sido + " " + page + "쪽", LIST_URL,
                    Map.of("pageNo", String.valueOf(page), "numOfRows", String.valueOf(PAGE_SIZE), "ctpvNm", sido),
                    TIMEOUT, 4, body -> XmlItems.parse(body, "servList"));
            rows.addAll(result.items());
            if (result.items().isEmpty() || rows.size() >= result.totalCount()) {
                return rows;
            }
        }
        throw new FeedException("복지로 지자체 " + sido + " 목록이 " + MAX_PAGES + "쪽을 넘습니다.");
    }

    /** 대상 지역 밖이거나 대상 가구 상황이 없으면 empty. */
    static Optional<BenefitDraft> toDraft(Map<String, String> row) {
        Optional<Region.Placement> placement = Region.placeLocal(row.get("ctpvNm"), row.get("sggNm"));
        BenefitClassifier.Input input = new BenefitClassifier.Input(
                row.get("servNm"), row.get("servDgst"), null, null, row.get("intrsThemaNmArray"), null, row.get("lifeNmArray"));
        Set<LifeStage> stages = BenefitClassifier.classify(input);
        // 대상 상황이 비어도 애매한 사업이면 남긴다('제외' 탭용)
        if (placement.isEmpty() || stages.isEmpty() && !BenefitClassifier.isBorderline(input)) {
            return Optional.empty();
        }
        return Optional.of(new BenefitDraft(
                BenefitSource.BOKJIRO_LOCAL,
                row.get("servId"),
                row.get("servNm"),
                blankToNull(row.get("bizChrDeptNm")),
                placement.get().scope(),
                placement.get().sido(),
                placement.get().sigungu(),
                blankToNull(row.get("intrsThemaNmArray")),
                blankToNull(row.get("srvPvsnNm")),
                blankToNull(row.get("servDgst")),
                null,
                null,
                null,
                blankToNull(row.get("aplyMtdNm")),
                null,
                blankToNull(row.get("servDtlLink")),
                null,
                stages,
                blankToNull(row.get("trgterIndvdlNmArray")),
                null,
                blankToNull(row.get("lastModYmd")),
                null,   // 목록에 등록일이 없다
                new BenefitDraft.RawSource("XML", XmlItems.toXml("servList", row), null)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
