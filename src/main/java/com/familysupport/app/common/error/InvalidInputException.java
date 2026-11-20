package com.familysupport.app.common.error;

/** 잘못된 입력 — HTTP 400. 메시지는 사용자에게 그대로 보여줄 수 있는 문장으로 쓴다. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
