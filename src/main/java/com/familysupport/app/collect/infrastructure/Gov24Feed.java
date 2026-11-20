package com.familysupport.app.collect.infrastructure;

import com.familysupport.app.benefit.domain.Benefit;
import com.familysupport.app.benefit.domain.BenefitClassifier;
import com.familysupport.app.benefit.domain.BenefitDraft;
import com.familysupport.app.benefit.domain.BenefitSource;
import com.familysupport.app.benefit.domain.IncomeBand;
import com.familysupport.app.benefit.domain.LifeStage;
import com.familysupport.app.benefit.domain.Region;
import com.familysupport.app.benefit.domain.SupportConditions;
import com.familysupport.app.collect.domain.BenefitFeed;
import com.familysupport.app.collect.domain.FeedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정부24 공공서비스(혜택) — 행정안전부, api.odcloud.kr.
 * 1) 전체 목록(약 1만 건)을 1,000건씩 받는다 — 소관기관명으로 거르는 조건이 API에 없어서(swagger 확인).
 * 2) 대상 지역·대상 문구에 맞는 후보만 남기고, 후보마다 지원조건(supportConditions)을 1건씩 받는다.
 *    원문 수정일이 지난번과 같으면 저장된 조건을 다시 쓴다(호출 수 절약).
 */
@Slf4j
@Component
public class Gov24Feed implements BenefitFeed {

    static final String LIST_URL = "https://api.odcloud.kr/api/gov24/v3/serviceList";
    static final String CONDITIONS_URL = "https://api.odcloud.kr/api/gov24/v3/supportConditions";
    private static final int PAGE_SIZE = 1000;
    /** 전체 약 1.1만 건 기준 여유를 둔 안전 상한(무한 반복 방지). */
    private static final int MAX_PAGES = 30;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final PublicDataHttp http;
    private final JsonMapper jsonMapper;

    public Gov24Feed(PublicDataHttp http, JsonMapper jsonMapper) {
        this.http = http;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public BenefitSource source() {
        return BenefitSource.GOV24;
    }

    @Override
    public List<BenefitDraft> fetch(Function<String, Optional<Benefit>> previous) {
        List<Map<String, Object>> rows = fetchAllRows();
        List<BenefitDraft> drafts = new ArrayList<>();
        int conditionCalls = 0;
        int conditionFailures = 0;
        for (Map<String, Object> row : rows) {
            Optional<Region.Placement> placement = Region.placeOrganization(str(row, "소관기관명"), str(row, "소관기관유형"));
            if (placement.isEmpty() || !BenefitClassifier.isCandidate(classifierInput(row, null))) {
                continue;
            }
            String sourceId = str(row, "서비스ID");
            SupportConditions conditions = reusableConditions(previous, sourceId, str(row, "수정일시")).orElse(null);
            String conditionBody = null;
            if (conditions == null) {
                conditionCalls++;
                try {
                    Map<String, Object> conditionRow = fetchConditionRow(sourceId);
                    conditions = toConditions(conditionRow);
                    conditionBody = toJson(conditionRow);
                } catch (FeedException e) {
                    // 조건 1건 실패로 사업을 버리지 않는다 — 조건 없이 저장하고 화면에 "조건 정보 없음"으로 보인다
                    conditionFailures++;
                }
            }
            // 상황이 비어도 넘긴다 — 같은 사업이 복지로에서 남으면 수집 쪽에서 맞춰 남긴다(BenefitClassifier.alignAcrossSources)
            drafts.add(toDraft(row, placement.get(), conditions)
                    .withRaw(new BenefitDraft.RawSource("JSON", toJson(row), conditionBody)));
        }
        log.info("정부24: 전체 {}건 중 후보 {}건, 지원조건 호출 {}건(실패 {}건)",
                rows.size(), drafts.size(), conditionCalls, conditionFailures);
        return drafts;
    }

    private List<Map<String, Object>> fetchAllRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, Object> body = http.get("정부24 목록 " + page + "쪽", LIST_URL,
                    Map.of("page", String.valueOf(page), "perPage", String.valueOf(PAGE_SIZE), "returnType", "JSON"),
                    TIMEOUT, 3, this::readData);
            List<Map<String, Object>> data = dataOf(body);
            rows.addAll(data);
            int total = body.get("totalCount") instanceof Number n ? n.intValue() : 0;
            if (data.isEmpty() || rows.size() >= total) {
                return rows;
            }
        }
        throw new FeedException("정부24 목록이 " + MAX_PAGES + "쪽을 넘습니다 — 상한을 확인하세요.");
    }

    private Map<String, Object> fetchConditionRow(String serviceId) {
        Map<String, Object> body = http.get("정부24 지원조건 " + serviceId, CONDITIONS_URL,
                Map.of("page", "1", "perPage", "1", "returnType", "JSON", "cond[서비스ID::EQ]", serviceId),
                Duration.ofSeconds(30), 2, this::readData);
        List<Map<String, Object>> data = dataOf(body);
        if (data.isEmpty()) {
            throw new FeedException("정부24 지원조건 없음: " + serviceId);
        }
        return data.get(0);
    }

    private String toJson(Map<String, Object> row) {
        return jsonMapper.writeValueAsString(row);
    }

    static BenefitClassifier.Input classifierInput(Map<String, Object> row, SupportConditions conditions) {
        return new BenefitClassifier.Input(str(row, "서비스명"), str(row, "서비스목적요약"), str(row, "지원대상"),
                str(row, "사용자구분"), str(row, "서비스분야"), conditions, null);
    }

    /** 원문 수정일이 그대로이고 지난번에 조건을 받았으면 그 조건을 다시 쓴다. */
    private static Optional<SupportConditions> reusableConditions(Function<String, Optional<Benefit>> previous,
            String sourceId, String modified) {
        return previous.apply(BenefitSource.GOV24.name() + ":" + sourceId)
                .filter(b -> Objects.equals(b.getSourceModified(), modified))
                .flatMap(Benefit::supportConditions);
    }

    /** 지원조건 1행 → 판단용 값. 소득 5구간이 모두 비어 있으면 소득 정보 없음(null)으로 둔다. */
    static SupportConditions toConditions(Map<String, Object> row) {
        StringBuilder bands = new StringBuilder();
        boolean anyBand = false;
        for (IncomeBand band : IncomeBand.values()) {
            boolean yes = "Y".equals(row.get(band.code()));
            anyBand |= row.get(band.code()) != null;
            bands.append(yes ? 'Y' : 'N');
        }
        Set<String> flags = new TreeSet<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("JA") && !key.startsWith("JA011") && !key.startsWith("JA020") && "Y".equals(e.getValue())) {
                flags.add(key);
            }
        }
        return new SupportConditions(toInt(row.get("JA0110")), toInt(row.get("JA0111")),
                anyBand ? bands.toString() : null, flags);
    }

    /** 대상 가구 상황이 비어 있으면(분류 규칙에서 빠짐) 호출한 쪽이 버린다. 원문(raw)은 비워 둔다. */
    static BenefitDraft toDraft(Map<String, Object> row, Region.Placement placement, SupportConditions conditions) {
        Set<LifeStage> stages = BenefitClassifier.classify(classifierInput(row, conditions));
        return new BenefitDraft(
                BenefitSource.GOV24,
                str(row, "서비스ID"),
                str(row, "서비스명"),
                str(row, "소관기관명"),
                placement.scope(),
                placement.sido(),
                placement.sigungu(),
                str(row, "서비스분야"),
                blankToNull(str(row, "지원유형")),
                blankToNull(str(row, "서비스목적요약")),
                blankToNull(str(row, "지원대상")),
                blankToNull(str(row, "선정기준")),
                blankToNull(str(row, "지원내용")),
                blankToNull(str(row, "신청방법")),
                blankToNull(str(row, "신청기한")),
                blankToNull(str(row, "상세조회URL")),
                blankToNull(str(row, "전화문의")),
                stages,
                null,
                conditions,
                blankToNull(str(row, "수정일시")),
                blankToNull(str(row, "등록일시")),
                null);
    }

    /** JSON을 읽고 data 목록이 있는지까지 확인한다(없으면 FeedException → 재시도 대상). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readData(String body) {
        Map<String, Object> map;
        try {
            map = jsonMapper.readValue(body, Map.class);
        } catch (RuntimeException e) {
            throw new FeedException("정부24 응답을 읽지 못했습니다: " + (body.length() > 200 ? body.substring(0, 200) : body));
        }
        dataOf(map);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dataOf(Map<String, Object> body) {
        Object data = body.get("data");
        if (!(data instanceof List<?>)) {
            throw new FeedException("정부24 응답에 data가 없습니다: " + body.keySet());
        }
        return (List<Map<String, Object>>) data;
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Integer toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && s.matches("\\d+")) {
            return Integer.parseInt(s);
        }
        return null;
    }
}
