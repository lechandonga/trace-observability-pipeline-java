package com.github.lechandonga.traceobservabilitypipelinejava.trace;

/**
 * 跨度作用域，try-with-resources 使用。close() 绝不抛异常。
 */
public final class TraceScope implements AutoCloseable {

    static final TraceScope NOOP = new TraceScope(null, null, null, null);

    private final Tracer tracer;
    private final Span span;
    private final String previousMdcTraceId;
    private final String previousMdcSpanId;
    private boolean closed;

    TraceScope(Tracer tracer, Span span, String previousMdcTraceId, String previousMdcSpanId) {
        this.tracer = tracer;
        this.span = span;
        this.previousMdcTraceId = previousMdcTraceId;
        this.previousMdcSpanId = previousMdcSpanId;
    }

    public String traceId() {
        return span == null ? null : span.getTraceId();
    }

    /** 记录业务异常：仅标注，不吞不改。 */
    public void recordError(Throwable t) {
        if (span != null && t != null) {
            span.recordError(t);
        }
    }

    @Override
    public void close() {
        if (closed || tracer == null) {
            return;
        }
        closed = true;
        tracer.finishSpan(span, previousMdcTraceId, previousMdcSpanId);
    }
}
