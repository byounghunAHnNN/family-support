package com.familysupport.app.collect.domain;

/** 출처 호출·해석 실패. 메시지에 인증키를 넣지 않는다(수집 기록·로그에 남기 때문). */
public class FeedException extends RuntimeException {

    public FeedException(String message) {
        super(message);
    }
}
