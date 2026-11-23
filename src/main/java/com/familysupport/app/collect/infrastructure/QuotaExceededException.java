package com.familysupport.app.collect.infrastructure;

import com.familysupport.app.collect.domain.FeedException;

/**
 * 공공데이터포털 일일 호출 한도 초과(HTTP 429, LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR — 2026-11-23 실측).
 * 그날은 다시 불러도 계속 거절되므로 재시도하지 않는다.
 */
final class QuotaExceededException extends FeedException {

    static final String MARKER = "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR";

    QuotaExceededException(String name) {
        super(name + ": 일일 호출 한도 초과(HTTP 429)");
    }
}
