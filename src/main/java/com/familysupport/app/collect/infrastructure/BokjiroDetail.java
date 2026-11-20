package com.familysupport.app.collect.infrastructure;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.collect.domain.FeedException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * 복지로 상세 API — 목록에 없는 지원대상·선정기준·지원내용·신청방법 문장과 지자체 시행기간을 채운다(2026-11-20 확인:
 * 중앙 NationalWelfaredetailedV001, 지자체 LcgvWelfaredetailed. 나이·소득 같은 구조화 조건은 상세에도 없다).
 * 상세 1건 실패로 사업을 버리지 않는다 — 목록 내용만으로 저장한다.
 */
@Slf4j
final class BokjiroDetail {

    static final String CENTRAL_URL =
            "https://apis.data.go.kr/B554287/NationalWelfareInformationsV001/NationalWelfaredetailedV001";
    static final String LOCAL_URL =
            "https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations/LcgvWelfaredetailed";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String ENDLESS = "99991231";
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DOT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 상세 1건(문장은 출처 그대로, 줄바꿈만 정리). */
    record Detail(String summary, String target, String criteria, String support, String applyMethod,
            String applyPeriod, String body) {
    }

    private BokjiroDetail() {
    }

    /**
     * 초안마다 상세를 붙인다.
     *
     * @param reuseWhenUnchanged true면 원문 수정일이 지난번과 같고 상세를 받아 둔 사업은 다시 부르지 않는다(지자체).
     *                           중앙은 목록에 수정일이 없어 매번 부른다(약 110건).
     */
    static List<BenefitDraft> enrich(PublicDataHttp http, String url, boolean local, List<BenefitDraft> drafts,
            Function<String, Optional<Benefit>> previous, boolean reuseWhenUnchanged) {
        List<BenefitDraft> result = new ArrayList<>(drafts.size());
        int calls = 0;
        int failures = 0;
        int reused = 0;
        for (BenefitDraft draft : drafts) {
            Optional<Benefit> prev = previous.apply(draft.id());
            if (reuseWhenUnchanged && prev.isPresent() && prev.get().getTargetText() != null
                    && Objects.equals(prev.get().getSourceModified(), draft.sourceModified())) {
                Benefit p = prev.get();
                result.add(draft.withDetail(null, p.getTargetText(), p.getCriteriaText(), p.getSupportText(),
                        p.getApplyMethod(), p.getApplyPeriod(), null));
                reused++;
                continue;
            }
            calls++;
            try {
                Map<String, String> params = local
                        ? Map.of("servId", draft.sourceId())
                        : Map.of("callTp", "D", "servId", draft.sourceId());
                Detail d = http.get("복지로 상세 " + draft.sourceId(), url, params, TIMEOUT, 2,
                        body -> parse(body, local));
                result.add(draft.withDetail(d.summary(), d.target(), d.criteria(), d.support(), d.applyMethod(),
                        d.applyPeriod(), d.body()));
            } catch (FeedException e) {
                failures++;
                result.add(draft);
            }
        }
        log.info("복지로 상세({}): 호출 {}건(실패 {}건), 재사용 {}건", local ? "지자체" : "중앙", calls, failures, reused);
        return result;
    }

    static Detail parse(String body, boolean local) {
        XmlItems.Page page = XmlItems.parse(body, "wantedDtl");
        if (page.items().isEmpty()) {
            throw new FeedException("복지로 상세 응답에 항목이 없습니다");
        }
        Map<String, String> m = page.items().get(0);
        if (local) {
            return new Detail(null, clean(m.get("sprtTrgtCn")), clean(m.get("slctCritCn")), clean(m.get("alwServCn")),
                    clean(m.get("aplyMtdCn")), period(m.get("enfcBgngYmd"), m.get("enfcEndYmd")), body);
        }
        return new Detail(clean(m.get("wlfareInfoOutlCn")), clean(m.get("tgtrDtlCn")), clean(m.get("slctCritCn")),
                clean(m.get("alwServCn")), null, null, body);
    }

    /** 지자체 시행기간 → 신청기한 문장. 끝이 99991231이면 상시. */
    static String period(String begin, String end) {
        LocalDate from = date(begin);
        if (ENDLESS.equals(end)) {
            return from == null ? "상시" : "상시 (시행 " + from.format(DOT) + "부터)";
        }
        LocalDate to = date(end);
        if (from == null || to == null) {
            return null;
        }
        return from.format(DOT) + "~" + to.format(DOT);
    }

    private static LocalDate date(String ymd) {
        if (ymd == null || !ymd.matches("\\d{8}")) {
            return null;
        }
        try {
            return LocalDate.parse(ymd, YMD);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    /** 응답의 &#13;(CR) 줄바꿈을 정리한다. */
    private static String clean(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
