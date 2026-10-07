package com.github.lechandonga.tracepipe.tracing;

/**
 * 一次采样判定的上下文。同一个 traceId 全链路共享同一个采样结果。
 */
public record TraceContext(String traceId, String spanId, boolean sampled) {

    public TraceContext withSpan(String newSpanId) {
        return new TraceContext(traceId, newSpanId, sampled);
    }
}
