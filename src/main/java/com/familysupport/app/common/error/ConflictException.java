package com.familysupport.app.common.error;

/** 지금 처리할 수 없는 상태 — HTTP 409(예: 수집이 이미 진행 중). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
