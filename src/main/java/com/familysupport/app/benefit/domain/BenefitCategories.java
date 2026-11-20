package com.familysupport.app.benefit.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 카테고리 3축(설계 문서 0장, 2026-11-20 확정).
 * ① 지원 방식·② 분야는 출처가 원래 갖고 있는 값(정부24 지원유형·서비스분야, 복지로 제공유형·관심주제)을 대응표로 옮기고,
 * ③ 특정 대상은 사업명 키워드로 붙인다.
 *
 * @param supportTypes 첫 번째가 카드에 보이는 대표 방식. 정할 수 없으면 ETC
 * @param fields       정할 수 없으면 ETC
 * @param targetGroups 없으면 빈 집합
 */
public record BenefitCategories(List<SupportType> supportTypes, Set<BenefitField> fields, Set<TargetGroup> targetGroups) {

    /** 지원유형 1개 값 → 방식. 위에서부터 먼저 맞는 것 하나만 쓴다("현금(융자)"는 융자, "현금(감면)"은 감면). */
    private static final Map<SupportType, Pattern> TYPE_RULES = new LinkedHashMap<>();
    /** 출처 분야 값 → 분야. */
    private static final Map<BenefitField, Pattern> FIELD_SOURCE_RULES = new LinkedHashMap<>();
    /** 사업명·요약 키워드 → 분야(보조). 육아휴직은 출처에서 '고용·창업'이라 키워드로 '일·휴가'에 넣는다. */
    private static final Map<BenefitField, Pattern> FIELD_KEYWORD_RULES = new LinkedHashMap<>();
    private static final Map<TargetGroup, Pattern> GROUP_RULES = new LinkedHashMap<>();

    static {
        TYPE_RULES.put(SupportType.LOAN, Pattern.compile("융자|현금대여"));
        TYPE_RULES.put(SupportType.DISCOUNT, Pattern.compile("감면"));
        TYPE_RULES.put(SupportType.VOUCHER, Pattern.compile("이용권|바우처"));
        TYPE_RULES.put(SupportType.CASH, Pattern.compile("현금|지역화폐"));
        TYPE_RULES.put(SupportType.SERVICE, Pattern.compile("서비스|프로그램|상담|의료지원|돌봄|일자리|기술지원"));
        TYPE_RULES.put(SupportType.GOODS, Pattern.compile("현물|시설이용|시설입소"));

        FIELD_SOURCE_RULES.put(BenefitField.PREGNANCY, Pattern.compile("임신|출산|입양"));
        FIELD_SOURCE_RULES.put(BenefitField.CHILDCARE, Pattern.compile("보육|돌봄"));
        FIELD_SOURCE_RULES.put(BenefitField.HEALTH, Pattern.compile("보건·의료|신체건강|정신건강"));
        FIELD_SOURCE_RULES.put(BenefitField.LIVING, Pattern.compile("생활안정|생활지원|에너지|서민금융|위기|행정·안전"));
        FIELD_SOURCE_RULES.put(BenefitField.HOUSING, Pattern.compile("주거"));
        FIELD_SOURCE_RULES.put(BenefitField.WORK, Pattern.compile("고용·창업|일자리"));
        // '보육·교육'의 교육은 보육 쪽이라 교육·문화로 넣지 않는다
        FIELD_SOURCE_RULES.put(BenefitField.EDU, Pattern.compile("(?<!보육·)교육|문화|여가"));

        FIELD_KEYWORD_RULES.put(BenefitField.PREGNANCY, Pattern.compile("임신|임산부|출산|산모|산후|난임|태아|입양"));
        FIELD_KEYWORD_RULES.put(BenefitField.CHILDCARE, Pattern.compile("보육|어린이집|아이돌봄|양육|육아|키즈|장난감"));
        FIELD_KEYWORD_RULES.put(BenefitField.HEALTH, Pattern.compile("의료|진료|검진|검사|예방접종|치료|건강"));
        FIELD_KEYWORD_RULES.put(BenefitField.WORK, Pattern.compile("육아휴직|출산휴가|근로시간\\s?단축|휴가"));
        FIELD_KEYWORD_RULES.put(BenefitField.HOUSING, Pattern.compile("주거|주택|전세|월세|임대"));

        GROUP_RULES.put(TargetGroup.DISABLED, Pattern.compile("장애"));
        GROUP_RULES.put(TargetGroup.LOW_INCOME, Pattern.compile("수급자|수급가구|차상위|기초생활|의료급여"));
        GROUP_RULES.put(TargetGroup.MULTICULTURAL, Pattern.compile("다문화|결혼이민|외국인|이주|귀화"));
        GROUP_RULES.put(TargetGroup.SINGLE_PARENT, Pattern.compile("한부모|조손|미혼모|청소년부모"));
        GROUP_RULES.put(TargetGroup.VETERAN, Pattern.compile("보훈|유공자"));
        GROUP_RULES.put(TargetGroup.DEFECTOR, Pattern.compile("북한이탈|탈북"));
    }

    public BenefitCategories {
        supportTypes = supportTypes.isEmpty() ? List.of(SupportType.ETC) : List.copyOf(supportTypes);
        fields = fields.isEmpty() ? Set.of(BenefitField.ETC) : Set.copyOf(fields);
        targetGroups = Set.copyOf(targetGroups);
    }

    /**
     * @param supportTypeText 정부24 지원유형("현금||현물" 형식 가능), 복지로 제공유형
     * @param field           정부24 서비스분야, 복지로 관심주제
     */
    public static BenefitCategories of(String supportTypeText, String field, String name, String summary) {
        List<SupportType> types = new ArrayList<>();
        for (String token : nz(supportTypeText).split("\\|\\||,")) {
            TYPE_RULES.entrySet().stream()
                    .filter(e -> e.getValue().matcher(token).find())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .filter(t -> !types.contains(t))
                    .ifPresent(types::add);
        }
        Set<BenefitField> fields = EnumSet.noneOf(BenefitField.class);
        FIELD_SOURCE_RULES.forEach((f, p) -> {
            if (p.matcher(nz(field)).find()) {
                fields.add(f);
            }
        });
        String head = nz(name) + " " + nz(summary);
        FIELD_KEYWORD_RULES.forEach((f, p) -> {
            if (p.matcher(head).find()) {
                fields.add(f);
            }
        });
        Set<TargetGroup> groups = EnumSet.noneOf(TargetGroup.class);
        GROUP_RULES.forEach((g, p) -> {
            if (p.matcher(nz(name)).find()) {
                groups.add(g);
            }
        });
        return new BenefitCategories(types, fields, groups);
    }

    /** DB 저장값(콤마 구분 이름) → 카테고리. 수집 전 데이터처럼 비어 있으면 기타. */
    public static BenefitCategories parse(String types, String fields, String groups) {
        return new BenefitCategories(
                parseList(types, SupportType.class),
                EnumSet.copyOf(withDefault(parseList(fields, BenefitField.class), BenefitField.ETC)),
                parseList(groups, TargetGroup.class).isEmpty()
                        ? EnumSet.noneOf(TargetGroup.class) : EnumSet.copyOf(parseList(groups, TargetGroup.class)));
    }

    public String supportTypesCsv() {
        return supportTypes.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public String fieldsCsv() {
        return fields.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    public String targetGroupsCsv() {
        return targetGroups.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    private static <E extends Enum<E>> List<E> parseList(String csv, Class<E> type) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> Enum.valueOf(type, s)).toList();
    }

    private static <E> List<E> withDefault(List<E> list, E fallback) {
        return list.isEmpty() ? List.of(fallback) : list;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
