package com.familysupport.app.benefit.domain;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 시·도 이름과 서비스 대상 지역.
 * 첫 버전 대상은 서울·인천·경기·대전·세종·충북·충남. 다른 시·도는 "대상 지역 밖"으로 판단해 수집하지 않는다.
 */
public final class Region {

    /** 시·도 정식 이름과 기관명에 쓰이는 줄임말. 정식 이름이 먼저 맞는지 본다. */
    private record Sido(String name, List<String> aliases, boolean target) {
    }

    private static final List<Sido> ALL = List.of(
            new Sido("서울특별시", List.of("서울"), true),
            new Sido("인천광역시", List.of("인천"), true),
            new Sido("경기도", List.of("경기"), true),
            new Sido("대전광역시", List.of("대전"), true),
            new Sido("세종특별자치시", List.of("세종"), true),
            new Sido("충청북도", List.of("충북"), true),
            new Sido("충청남도", List.of("충남"), true),
            new Sido("부산광역시", List.of("부산"), false),
            new Sido("대구광역시", List.of("대구"), false),
            new Sido("광주광역시", List.of("광주"), false),
            new Sido("울산광역시", List.of("울산"), false),
            new Sido("강원특별자치도", List.of("강원도", "강원"), false),
            new Sido("전북특별자치도", List.of("전라북도", "전북"), false),
            new Sido("전라남도", List.of("전남"), false),
            new Sido("경상북도", List.of("경북"), false),
            new Sido("경상남도", List.of("경남"), false),
            new Sido("제주특별자치도", List.of("제주"), false));

    /** 시·도 뒤에 붙어 쓴 구·군 이름(1~4글자 + 구/군) 뒤에 기관명이 이어지는 경우. */
    private static final Pattern ATTACHED_SIGUNGU = Pattern.compile("^([가-힣]{1,4}?[구군])(?=[가-힣])");

    /** 서비스 대상 시·도 정식 이름(화면 선택 목록 순서). */
    public static final List<String> TARGET_SIDOS = ALL.stream().filter(Sido::target).map(Sido::name).toList();

    /** 사업이 놓이는 지역. 전국이면 sido·sigungu가 null, 시·도 사업이면 sigungu가 null. */
    public record Placement(RegionScope scope, String sido, String sigungu) {

        static final Placement NATIONAL = new Placement(RegionScope.NATIONAL, null, null);
    }

    private Region() {
    }

    /**
     * 정부24 소관기관명·유형으로 지역을 정한다.
     * - 기관명이 시·도로 시작하면 그 시·도(뒤에 시군구가 있으면 시군구) 사업. 대상 밖 시·도면 empty.
     * - 시·도를 알 수 없으면 중앙행정기관·공공기관만 전국 사업으로 보고, 그 밖(예: "송파구시설관리공단")은 empty —
     *   지역을 모르는 지역 기관 사업을 전국으로 잘못 보여주지 않기 위해 버린다.
     */
    public static Optional<Placement> placeOrganization(String organization, String organizationType) {
        Optional<String> sido = sidoOf(organization);
        if (sido.isPresent()) {
            if (!isTargetSido(sido.get())) {
                return Optional.empty();
            }
            return Optional.of(sigunguOf(organization, sido.get())
                    .map(sgg -> new Placement(RegionScope.SIGUNGU, sido.get(), sgg))
                    .orElseGet(() -> new Placement(RegionScope.SIDO, sido.get(), null)));
        }
        String type = organizationType == null ? "" : organizationType;
        if (type.startsWith("중앙행정기관") || type.startsWith("공공기관")) {
            return Optional.of(Placement.NATIONAL);
        }
        return Optional.empty();
    }

    /**
     * 복지로 지자체 사업의 시·도명·시군구명으로 지역을 정한다.
     * 시군구 칸이 비었거나 시·군·구가 아닌 값(예: "인천광역시교육청")이면 시·도 사업으로 본다.
     */
    public static Optional<Placement> placeLocal(String sidoName, String sigunguName) {
        if (!isTargetSido(sidoName)) {
            return Optional.empty();
        }
        String sgg = sigunguName == null ? "" : sigunguName.trim();
        if (sgg.endsWith("시") || sgg.endsWith("군") || sgg.endsWith("구")) {
            return Optional.of(new Placement(RegionScope.SIGUNGU, sidoName, sgg));
        }
        return Optional.of(new Placement(RegionScope.SIDO, sidoName, null));
    }

    public static boolean isTargetSido(String sidoName) {
        return sidoName != null && TARGET_SIDOS.contains(sidoName);
    }

    /**
     * 기관명 앞부분으로 시·도를 찾는다(예: "서울특별시 송파구" → 서울특별시, "인천도시공사" → 인천광역시).
     * 대상 밖 시·도도 찾아서 돌려준다 — 호출하는 쪽이 "다른 지역 사업"으로 걸러낼 수 있게.
     */
    public static Optional<String> sidoOf(String organization) {
        if (organization == null) {
            return Optional.empty();
        }
        String org = organization.trim();
        for (Sido sido : ALL) {
            if (org.startsWith(sido.name())) {
                return Optional.of(sido.name());
            }
        }
        for (Sido sido : ALL) {
            for (String alias : sido.aliases()) {
                if (org.startsWith(alias)) {
                    return Optional.of(sido.name());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * "서울특별시 송파구" → "송파구". 시·도 이름 뒤 첫 단어가 시·군·구로 끝날 때만 시군구로 본다
     * ("경기도 수원시 장안구" → "수원시"). 시·도 정식 이름으로 시작하지 않으면 시군구를 판단하지 않는다.
     */
    public static Optional<String> sigunguOf(String organization, String sidoName) {
        if (organization == null || sidoName == null) {
            return Optional.empty();
        }
        String org = organization.trim();
        if (!org.startsWith(sidoName)) {
            return Optional.empty();
        }
        String afterSido = org.substring(sidoName.length());
        String rest = afterSido.trim();
        if (rest.isEmpty()) {
            return Optional.empty();
        }
        if (!Character.isWhitespace(afterSido.charAt(0))) {
            // 띄어쓰기 없이 붙은 기관명(예: "서울특별시강동구도시관리공단") — 바로 뒤가 ○○구·○○군일 때만 시군구로 본다.
            // "시"는 보지 않는다: "대전광역시시설관리공단"의 '시설'처럼 잘못 잡히기 때문(11/16 실제 기관명 34종 확인).
            Matcher attached = ATTACHED_SIGUNGU.matcher(rest);
            return attached.find() ? Optional.of(attached.group(1)) : Optional.empty();
        }
        String first = rest.split("\\s+")[0];
        if (first.endsWith("시") || first.endsWith("군") || first.endsWith("구")) {
            return Optional.of(first);
        }
        return Optional.empty();
    }
}
