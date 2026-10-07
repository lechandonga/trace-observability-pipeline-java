package com.github.lechandonga.tracepipe.demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 演示用异常映射：业务异常转 500。异常类型信息保留在响应体中，
 * TraceWebFilter 已在抛出时把异常标注到链路。
 */
@RestControllerAdvice
public class DemoExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", e.getClass().getName(),
                        "message", String.valueOf(e.getMessage())));
    }
}
