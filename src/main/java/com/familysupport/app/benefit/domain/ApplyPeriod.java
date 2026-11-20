package com.familysupport.app.benefit.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 신청기한 원문 해석 — 값 객체. 출처가 자유 문장이라 확실히 읽히는 형태만 해석하고 나머지는 원문을 그대로 보여준다.
 * (2026-11-20 실제 데이터: 정부24 987건 중 '상시신청' 681건, '접수기관 별 상이' 52건, 날짜 범위는 소수. 복지로는 기한 칸이 없음)
 *
 * @param kind  해석 결과 종류
 * @param start 시작일(범위일 때만)
 * @param end   마감일(범위일 때만)
 * @param text  원문 첫 줄(표시용)
 */
public record ApplyPeriod(Kind kind, LocalDate start, LocalDate end, String text) {

    public enum Kind {
        /** 상시·연중·신청 불필요. */
        ALWAYS,
        /** 출생·출산·시술 등 사유가 생긴 날부터 N일/개월 이내 — 사람마다 달라 열림으로 본다. */
        AFTER_EVENT,
        /** 날짜(또는 'YYYY년 상/하반기')로 정해진 기간. */
        RANGE,
        /** 기관별 상이·공고에 따름. */
        VARIES,
        /** 원문 없음 또는 해석 못 함. */
        UNKNOWN
    }

    /** 오늘 기준 상태. 범위가 아니면 OPEN(상시·사유 발생형) 또는 UNKNOWN. */
    public enum Status { OPEN, UPCOMING, CLOSED, UNKNOWN }

    private static final Pattern ALWAYS = Pattern.compile("상시|연중|수시|신청\\s?불필요|별도의\\s?신청\\s?절차가\\s?없");
    private static final Pattern AFTER_EVENT = Pattern.compile("\\d+\\s?(일|개월|년|주)\\s?(이내|내)|이내");
    private static final Pattern VARIES = Pattern.compile("상이|다를\\s?수|문의|따름|따라|공고|별도\\s?안내");
    /** 2025.09.15~2025.12.31 / 2025. 9. 15. ~ 12. 31. / 2025-09-15 ~ 2025-12-31 / 2025년 9월 15일 ~ 12월 31일 */
    private static final Pattern RANGE = Pattern.compile(
            "(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*일?\\.?\\s*[~∼～\\-–]\\s*"
                    + "(?:(\\d{4})\\s*[.\\-/년]\\s*)?(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");
    private static final Pattern HALF = Pattern.compile("(\\d{4})\\s*년\\s*(상|하)반기");
    private static final DateTimeFormatter DOT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public static ApplyPeriod parse(String original) {
        String text = firstLine(original);
        if (text.isEmpty()) {
            return new ApplyPeriod(Kind.UNKNOWN, null, null, null);
        }
        Matcher range = RANGE.matcher(original);
        if (range.find()) {
            try {
                int startYear = Integer.parseInt(range.group(1));
                LocalDate start = LocalDate.of(startYear, Integer.parseInt(range.group(2)), Integer.parseInt(range.group(3)));
                int endYear = range.group(4) != null ? Integer.parseInt(range.group(4)) : startYear;
                LocalDate end = LocalDate.of(endYear, Integer.parseInt(range.group(5)), Integer.parseInt(range.group(6)));
                if (!end.isBefore(start)) {
                    return new ApplyPeriod(Kind.RANGE, start, end, text);
                }
            } catch (DateTimeException | NumberFormatException e) {
                // 날짜가 아닌 숫자 — 아래 규칙으로
            }
        }
        Matcher half = HALF.matcher(original);
        if (half.find()) {
            int year = Integer.parseInt(half.group(1));
            boolean first = "상".equals(half.group(2));
            return new ApplyPeriod(Kind.RANGE, LocalDate.of(year, first ? 1 : 7, 1),
                    first ? LocalDate.of(year, 6, 30) : LocalDate.of(year, 12, 31), text);
        }
        if (ALWAYS.matcher(original).find()) {
            return new ApplyPeriod(Kind.ALWAYS, null, null, text);
        }
        if (AFTER_EVENT.matcher(original).find()) {
            return new ApplyPeriod(Kind.AFTER_EVENT, null, null, text);
        }
        if (VARIES.matcher(original).find()) {
            return new ApplyPeriod(Kind.VARIES, null, null, text);
        }
        return new ApplyPeriod(Kind.UNKNOWN, null, null, text);
    }

    public Status status(LocalDate today) {
        return switch (kind) {
            case ALWAYS, AFTER_EVENT -> Status.OPEN;
            case RANGE -> today.isBefore(start) ? Status.UPCOMING : today.isAfter(end) ? Status.CLOSED : Status.OPEN;
            case VARIES, UNKNOWN -> Status.UNKNOWN;
        };
    }

    /** 카드에 보여줄 짧은 문구. */
    public String label(LocalDate today) {
        return switch (kind) {
            case ALWAYS -> "상시 신청";
            case AFTER_EVENT -> text;
            case VARIES -> text;
            case UNKNOWN -> text == null ? "신청 기한은 원문 확인" : text;
            case RANGE -> switch (status(today)) {
                case CLOSED -> "마감됨 (" + end.format(DOT) + ")";
                case UPCOMING -> start.format(DOT) + "부터 신청";
                default -> "~ " + end.format(DOT) + " (D-" + ChronoUnit.DAYS.between(today, end) + ")";
            };
        };
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        for (String line : s.split("\\R")) {
            String t = line.replaceAll("^[\\s○●ㅇ\\-·•*]+", "").trim();
            if (!t.isEmpty()) {
                return t.length() > 60 ? t.substring(0, 59) + "…" : t;
            }
        }
        return "";
    }
}
