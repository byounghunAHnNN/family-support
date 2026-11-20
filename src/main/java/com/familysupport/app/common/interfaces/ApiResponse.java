package com.familysupport.app.common.interfaces;

/**
 * 모든 API 응답의 공통 껍데기. 성공·실패가 같은 모양이고, HTTP 상태 코드(200/400/404…)는 그대로 쓴다.
 * <pre>
 * 성공: { "success": true,  "data": {...}, "error": null }
 * 실패: { "success": false, "data": null,  "error": { "code": "...", "message": "..." } }
 * </pre>
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 실패 응답. GlobalExceptionHandler에서만 쓴다. */
    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message));
    }
}
