package com.github.lechandonga.tracepipe.tracing;

/**
 * 已结束 Span 的不可变快照，供导出器使用（导出在业务线程之外完成）。
 * durationMicros 单位为微秒；startEpochNanos 可换算绝对时间。
 */
public record FinishedSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        long startEpochNanos,
        long endEpochNanos,
        long durationMicros,
        boolean sampled,
        boolean error,
        String errorType,
        String errorMessage) {
}
