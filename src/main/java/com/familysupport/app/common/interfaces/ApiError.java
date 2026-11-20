package com.familysupport.app.common.interfaces;

/** ApiResponse.error 내용. code = 프로그램 분기용, message = 화면 표시용. */
public record ApiError(String code, String message) {
}
