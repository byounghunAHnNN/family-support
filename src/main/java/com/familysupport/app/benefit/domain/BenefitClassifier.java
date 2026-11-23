package com.familysupport.app.benefit.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 사업이 어떤 가구 상황(LifeStage)에 해당하는지 판단한다. 비어 있으면 이 서비스의 대상이 아닌 사업으로 보고 수집하지 않는다.
 *
 * <p>규칙(설계 문서 0장 "대상 키워드 v6", 2026-11-20 확정 — 실제 수집 2,175건으로 건수를 계산해 정했다)
 * <ol>
 *   <li>사업명·출처 값만으로 버리는 사업: 정부24 사용자구분이 "법인/시설/단체"뿐인 사업(기관 대상), 사업명 제외어,
 *       가정위탁 사업, 출처 분야가 농림축산어업인 사업(강한 키워드가 없을 때).</li>
 *   <li>사업명·요약·지원대상 문구의 키워드 + 정부24 지원조건 코드 + 복지로 생애주기로 상황을 붙인다.
 *       '창업 보육'처럼 육아와 무관한 문구는 지우고 본다.</li>
 *   <li>영유아(INFANT)는 키워드가 넓어 잡음이 많으므로 영유아 전용 키워드·아이 나이 상한 7세 이하·복지로 생애주기
 *       ("영유아"이고 노년 아님, 생애주기 4개 미만) 중 하나가 있을 때만 남긴다.</li>
 *   <li>노인·학생·장애인·위탁 등 다른 대상 문구가 있고 강한 키워드가 없으면 버린다.</li>
 * </ol>
 */
public final class BenefitClassifier {

    /**
     * '정자'는 앞에 한글이 붙으면 다른 낱말이다(선정자·결정자·난민인정자·생활안정자·예정자 — 2026-11-23 실제 데이터에서
     * 예비부모 사업 437건 중 83건이 이것으로만 걸렸다). '정자 동결', '난자·정자'처럼 앞이 한글이 아닐 때만 본다.
     */
    private static final Pattern PREPARING = Pattern.compile(
            "난임|예비부모|예비부부|임신\\s?사전|임신\\s?전|가임|난자|(?<![가-힣])정자");
    private static final Pattern PREGNANT = Pattern.compile("임신|임산부|임부|태아|산전");
    private static final Pattern BIRTH = Pattern.compile("출산|출생|산후|산모|신생아|입양|첫만남");
    // "아이" 단독은 "아이디" 등 잡음이 많아 넣지 않는다. 아이 나이 조건(7세 이하)으로 보완한다.
    private static final Pattern INFANT = Pattern.compile("영유아|영아|유아|아동|육아|보육|양육|어린이집|돌봄|부모급여|기저귀");
    private static final Pattern NEWLYWED = Pattern.compile("신혼|예비신혼|혼인|결혼");

    /** 영유아 상황을 남기는 키워드 — INFANT보다 좁다('아동'·'돌봄'만으로는 남기지 않는다). */
    private static final Pattern INFANT_STRICT = Pattern.compile(
            "영유아|영아|유아|신생아|육아|보육|어린이집|부모급여|아동수당|기저귀|첫만남|아이돌봄|키즈카페|장난감|입원진료"
                    + "|아동양육비|양육비|한부모|발달재활|언어발달|가족돌봄휴가|불소|치과");
    /** 이 단어가 있으면 다른 대상 문구가 있어도 남긴다. */
    private static final Pattern STRONG = Pattern.compile(
            "임신|임산부|임부|출산|산모|산후|신생아|영유아|영아|난임|신혼|부모급여|첫만남|아동수당|육아휴직|보육료|어린이집"
                    + "|아동양육비|양육비|한부모|발달재활|언어발달|가족돌봄휴가");
    /** 다른 대상(노인·학생·위탁 등) 문구. '청소년부모·청소년한부모'는 대상이므로 빼지 않는다. */
    private static final Pattern OTHER_TARGET = Pattern.compile(
            "가정위탁|위탁아동|위탁가정|시설\\s?입소|아동양육시설|양육시설|보호종료|자립준비|노인|어르신"
                    + "|청소년(?!\\s?한?부모)|학생|대학|장애인");
    /** 육아와 무관한 '보육'(창업 보육) 등 — 문구에서 지우고 판단한다. */
    private static final Pattern NOISE = Pattern.compile("창업\\s?보육|보육\\s?센터|창업존|팁스|TIPS");
    /** 사업명에 있으면 버린다(관람료·학자금·기관 운영 등 — 실제 데이터에서 잡음으로 확인). */
    private static final Pattern NAME_EXCLUDED = Pattern.compile(
            "관람|안전\\s?보안관|학자금|응시수수료|휴양시설|교직원|직장어린이집|어린이집지원|성취포상|과학기술인|고충처리");
    private static final Pattern FOSTER = Pattern.compile("가정위탁|위탁아동|위탁가정");

    /** 아이 나이 조건의 상한이 이 값 이하이면 영유아 자녀 대상 사업으로 본다(예: 천사지원금 1~7세). */
    private static final int INFANT_MAX_AGE = 7;
    private static final String INSTITUTION_ONLY = "법인/시설/단체";
    private static final String FARM_FIELD = "농림축산어업";

    /**
     * 분류 입력.
     *
     * @param userType   정부24 사용자구분(예: 개인, 가구, 법인/시설/단체). 복지로는 null
     * @param field      출처 분야(정부24 서비스분야, 복지로 관심주제)
     * @param conditions 정부24 지원조건(없으면 null)
     * @param lifeNames  복지로 생애주기 이름("임신 · 출산", "영유아"). 정부24는 null
     */
    public record Input(String name, String summary, String targetText, String userType, String field,
            SupportConditions conditions, String lifeNames) {

        String text() {
            return String.join(" ", nz(name), nz(summary), nz(targetText));
        }
    }

    private BenefitClassifier() {
    }

    /**
     * 지원조건을 받기 전 1차 판단 — 정부24는 이 결과가 비면 조건 API를 부르지 않는다(호출 수 절약).
     * 아이 나이 조건으로만 영유아가 되는 사업도 있으므로 영유아 거르기는 여기서 하지 않는다.
     */
    public static boolean isCandidate(Input in) {
        return !excludedByName(in) && !fromText(clean(in.text())).isEmpty();
    }

    /**
     * 애매해서 빠진 사업인가 — 사업명 제외어·기관 대상 등으로 버린 것이 아니고, 키워드·조건·생애주기로는 육아 관련 후보였는데
     * 영유아 좁히기나 다른 대상 문구 때문에 상황이 비게 된 사업. 이런 사업은 버리지 않고 상황 없이 저장해
     * 화면의 '제외' 탭에서 이유와 함께 보이게 한다(2026-11-20 사용자 결정 — 다자녀 입학지원금·결식아동 급식 등).
     */
    public static boolean isBorderline(Input in) {
        if (excludedByName(in)) {
            return false;
        }
        Set<LifeStage> stages = fromText(clean(in.text()));
        addFromConditions(stages, in.conditions());
        addFromLifeNames(stages, in.lifeNames());
        return !stages.isEmpty();
    }

    /** 최종 판단. 비어 있으면 대상 상황이 없는 사업이다(애매한 사업이면 저장은 한다 — {@link #isBorderline}). */
    public static Set<LifeStage> classify(Input in) {
        Set<LifeStage> stages = EnumSet.noneOf(LifeStage.class);
        if (excludedByName(in)) {
            return stages;
        }
        String raw = in.text();
        String text = clean(raw);
        stages.addAll(fromText(text));
        addFromConditions(stages, in.conditions());
        addFromLifeNames(stages, in.lifeNames());
        if (stages.contains(LifeStage.INFANT) && !keepInfant(in, text)) {
            stages.remove(LifeStage.INFANT);
        }
        boolean strong = STRONG.matcher(text).find();
        if (!stages.isEmpty() && !strong && (OTHER_TARGET.matcher(text).find() || NOISE.matcher(raw).find())) {
            stages.clear();
        }
        return stages;
    }

    /**
     * 출처 사이 맞추기 — 같은 사업(이름·지역 같음)이 다른 출처에서 대상으로 남았으면, 여기서 빠진 초안도 그 상황으로 남긴다.
     * 출처마다 문구가 달라 한쪽만 제외 문구에 걸리는 경우(예: 정부24 "출생축하금 지원"만 빠짐)를 막는다.
     * 맞출 짝이 없는 빈 상황 초안은 그대로 둔다(애매한 사업 — 상황 없이 저장해 '제외' 탭에 보인다).
     */
    public static List<BenefitDraft> alignAcrossSources(List<BenefitDraft> drafts) {
        Map<String, Set<LifeStage>> kept = new HashMap<>();
        drafts.stream().filter(d -> !d.stages().isEmpty())
                .forEach(d -> kept.computeIfAbsent(d.groupKey(), k -> EnumSet.noneOf(LifeStage.class)).addAll(d.stages()));
        List<BenefitDraft> result = new ArrayList<>();
        for (BenefitDraft d : drafts) {
            result.add(d.stages().isEmpty() && kept.containsKey(d.groupKey())
                    ? d.withStages(kept.get(d.groupKey())) : d);
        }
        return result;
    }

    private static boolean excludedByName(Input in) {
        String name = nz(in.name());
        if (INSTITUTION_ONLY.equals(nz(in.userType()).trim())) {
            return true;
        }
        if (NAME_EXCLUDED.matcher(name).find() || FOSTER.matcher(name).find()) {
            return true;
        }
        return nz(in.field()).contains(FARM_FIELD) && !STRONG.matcher(nz(in.name()) + " " + nz(in.summary())).find();
    }

    private static boolean keepInfant(Input in, String text) {
        if (INFANT_STRICT.matcher(text).find()) {
            return true;
        }
        SupportConditions c = in.conditions();
        if (c != null && c.ageTo() != null && c.ageTo() <= INFANT_MAX_AGE) {
            return true;
        }
        String life = in.lifeNames();
        if (life == null || life.isBlank()) {
            return false;
        }
        long count = Arrays.stream(life.split("[,·]")).filter(s -> !s.isBlank()).count();
        return life.contains("영유아") && !life.contains("노년") && count < 4;
    }

    private static String clean(String text) {
        return NOISE.matcher(text).replaceAll(" ");
    }

    private static Set<LifeStage> fromText(String text) {
        Set<LifeStage> stages = EnumSet.noneOf(LifeStage.class);
        if (PREPARING.matcher(text).find()) {
            stages.add(LifeStage.PREPARING);
        }
        if (PREGNANT.matcher(text).find()) {
            stages.add(LifeStage.PREGNANT);
        }
        if (BIRTH.matcher(text).find()) {
            stages.add(LifeStage.BIRTH);
        }
        if (INFANT.matcher(text).find()) {
            stages.add(LifeStage.INFANT);
        }
        if (NEWLYWED.matcher(text).find()) {
            stages.add(LifeStage.NEWLYWED);
        }
        return stages;
    }

    /** 정부24 조건 코드 JA0301(예비부모/난임)·JA0302(임산부)·JA0303(출산/입양), 나이 상한 7세 이하 → 영유아. */
    private static void addFromConditions(Set<LifeStage> stages, SupportConditions conditions) {
        if (conditions == null) {
            return;
        }
        if (conditions.flag("JA0301")) {
            stages.add(LifeStage.PREPARING);
        }
        if (conditions.flag("JA0302")) {
            stages.add(LifeStage.PREGNANT);
        }
        if (conditions.flag("JA0303")) {
            stages.add(LifeStage.BIRTH);
        }
        if (conditions.ageTo() != null && conditions.ageTo() <= INFANT_MAX_AGE) {
            stages.add(LifeStage.INFANT);
        }
    }

    /** 복지로 생애주기 이름("임신 · 출산", "영유아"). */
    private static void addFromLifeNames(Set<LifeStage> stages, String lifeNames) {
        if (lifeNames == null) {
            return;
        }
        if (lifeNames.contains("임신")) {
            stages.add(LifeStage.PREGNANT);
            stages.add(LifeStage.BIRTH);
        }
        if (lifeNames.contains("영유아")) {
            stages.add(LifeStage.INFANT);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
