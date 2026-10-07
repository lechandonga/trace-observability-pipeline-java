package com.github.lechandonga.tracepipe.tracing.web;

/**
 * 当异常已被上层（如 ControllerAdvice）转成 5xx 响应、Filter 未见到原始异常时，
 * 用该标记在链路上表示失败。
 */
class TraceStatusException extends RuntimeException {
    TraceStatusException(String message) {
        super(message, null, false, false);
    }
}
