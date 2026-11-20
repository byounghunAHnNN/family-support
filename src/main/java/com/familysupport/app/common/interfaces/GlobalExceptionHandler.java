package com.familysupport.app.common.interfaces;

import com.familysupport.app.common.error.ConflictException;
import com.familysupport.app.common.error.InvalidInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 예외 → HTTP 상태 + ApiResponse.fail 변환을 이 파일 한곳에서만 한다.
 * 각 계층은 예외를 던지기만 하고, 컨트롤러에 try-catch를 두지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<ApiResponse<Void>> invalidInput(InvalidInputException e) {
        return fail(HttpStatus.BAD_REQUEST, "INVALID_INPUT", e.getMessage());
    }

    /** JSON 깨짐, 숫자 형식 오류 등 스프링이 던지는 입력 예외를 500 대신 400으로. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ApiResponse<Void>> badRequest(Exception e) {
        return fail(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiResponse<Void>> conflict(ConflictException e) {
        return fail(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return fail(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.");
    }

    private static ResponseEntity<ApiResponse<Void>> fail(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(code, message));
    }
}
