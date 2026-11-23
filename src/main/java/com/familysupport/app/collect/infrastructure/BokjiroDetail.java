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
     * 초안마다 상세를 붙인다. 공공데이터포털 일일 호출 한도가 있어(2026-11-23 98건 성공 뒤 HTTP 429 실측),
     * 받아 둔 상세(DB에 저장된 지난번 값)는 다시 쓰고 필요한 것만 부른다 — {@link #needsCall} 참고.
     * 한도 초과가 한 번 나면 그날 남은 사업은 부르지 않고 지난번 값을 그대로 둔다.
     *
     * @param local true면 지자체(원문 수정일로 판단), false면 중앙(목록에 수정일이 없어 요일 순번으로 판단)
     * @param today 중앙의 요일 순번 계산용
     */
    static List<BenefitDraft> enrich(PublicDataHttp http, String url, boolean local, List<BenefitDraft> drafts,
            Function<String, Optional<Benefit>> previous, LocalDate today) {
        List<BenefitDraft> result = new ArrayList<>(drafts.size());
        int calls = 0;
        int reused = 0;
        int skippedByQuota = 0;
        List<String> failures = new ArrayList<>();
        boolean quotaExceeded = false;
        for (BenefitDraft draft : drafts) {
            Optional<Benefit> prev = previous.apply(draft.id());
            if (quotaExceeded || !needsCall(draft, prev.orElse(null), local, today)) {
                if (quotaExceeded) {
                    skippedByQuota++;
                } else {
                    reused++;
                }
                result.add(reuse(draft, prev.orElse(null), local));
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
            } catch (QuotaExceededException e) {
                quotaExceeded = true;
                failures.add(e.getMessage());
                result.add(reuse(draft, prev.orElse(null), local));
            } catch (FeedException e) {
                failures.add(e.getMessage());
                result.add(reuse(draft, prev.orElse(null), local));
            }
        }
        String kind = local ? "지자체" : "중앙";
        log.info("복지로 상세({}): 호출 {}건(실패 {}건), 재사용 {}건, 한도 초과로 건너뜀 {}건",
                kind, calls, failures.size(), reused, skippedByQuota);
        failures.forEach(f -> log.warn("복지로 상세({}) 실패: {}", kind, f));
        return result;
    }

    /** 중앙 상세를 다시 받는 주기(일) — 사업마다 요일을 나눠 하루 약 1/7씩 새로 받는다. */
    static final int CENTRAL_REFRESH_DAYS = 7;

    /**
     * 상세 API를 불러야 하는가.
     * <ul>
     *   <li>지난번 상세가 없으면(처음 보는 사업·지난번 실패) 부른다.</li>
     *   <li>지자체: 원문 수정일이 지난번과 다르면 부른다.</li>
     *   <li>중앙: 목록에 수정일이 없어 바뀜을 알 수 없다 → 사업마다 정해진 요일에만 새로 받는다(7일에 한 번).</li>
     * </ul>
     */
    static boolean needsCall(BenefitDraft draft, Benefit prev, boolean local, LocalDate today) {
        if (prev == null || prev.getTargetText() == null) {
            return true;
        }
        if (local) {
            return !Objects.equals(prev.getSourceModified(), draft.sourceModified());
        }
        return refreshSlot(draft.sourceId()) == Math.floorMod(today.toEpochDay(), CENTRAL_REFRESH_DAYS);
    }

    static int refreshSlot(String sourceId) {
        return Math.floorMod(sourceId.hashCode(), CENTRAL_REFRESH_DAYS);
    }

    /**
     * 지난번에 저장해 둔 상세를 다시 쓴다. 없으면 목록 내용만.
     * 중앙은 요약도 상세본(wlfareInfoOutlCn)으로 바꿔 저장했으므로 지난번 요약을 그대로 쓴다 — 날마다 목록본과 오가지 않게.
     */
    private static BenefitDraft reuse(BenefitDraft draft, Benefit prev, boolean local) {
        if (prev == null || prev.getTargetText() == null) {
            return draft;
        }
        return draft.withDetail(local ? null : prev.getSummary(), prev.getTargetText(), prev.getCriteriaText(), prev.getSupportText(),
                prev.getApplyMethod(), prev.getApplyPeriod(), null);
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
