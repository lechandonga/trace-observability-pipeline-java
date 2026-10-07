package com.github.lechandonga.tracepipe.tracing;

/**
 * 一个可变的活动 Span，finish 后导出为不可变快照 {@link FinishedSpan}。
 */
public final class Span {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final long startNanos;
    private final boolean sampled;

    private volatile long endNanos;
    private volatile boolean finished;
    private volatile boolean error;
    private volatile String errorType;
    private volatile String errorMessage;

    Span(String traceId, String spanId, String parentSpanId, String name,
         long startNanos, boolean sampled) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.startNanos = startNanos;
        this.sampled = sampled;
    }

    public void markError(Throwable throwable) {
        this.error = true;
        if (throwable != null) {
            this.errorType = throwable.getClass().getName();
            this.errorMessage = throwable.getMessage();
        }
    }

    void finish() {
        if (finished) {
            return;
        }
        finished = true;
        this.endNanos = System.nanoTime();
    }

    FinishedSpan toSnapshot() {
        return new FinishedSpan(traceId, spanId, parentSpanId, name,
                startNanos, endNanos, (endNanos - startNanos) / 1000L,
                sampled, error, errorType, errorMessage);
    }

    public String traceId() {
        return traceId;
    }

    public String spanId() {
        return spanId;
    }

    public String parentSpanId() {
        return parentSpanId;
    }

    public boolean sampled() {
        return sampled;
    }
}
