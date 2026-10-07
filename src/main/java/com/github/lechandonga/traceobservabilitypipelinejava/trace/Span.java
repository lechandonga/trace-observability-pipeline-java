package com.github.lechandonga.traceobservabilitypipelinejava.trace;

/**
 * 单段调用跨度。记录父子关系、耗时与成败。
 */
public final class Span {

    public enum Status { OK, ERROR }

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final long startEpochMillis;
    private volatile long endEpochMillis = -1;
    private volatile Status status = Status.OK;
    private volatile String errorType;
    private volatile String errorMessage;

    public Span(String traceId, String parentSpanId, String name) {
        this.traceId = traceId;
        this.spanId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.startEpochMillis = System.currentTimeMillis();
    }

    void finish() {
        this.endEpochMillis = System.currentTimeMillis();
    }

    void recordError(Throwable t) {
        this.status = Status.ERROR;
        this.errorType = t.getClass().getName();
        this.errorMessage = t.getMessage();
    }

    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getName() { return name; }
    public long getStartEpochMillis() { return startEpochMillis; }
    public long getEndEpochMillis() { return endEpochMillis; }
    public long getDurationMillis() { return endEpochMillis < 0 ? -1 : endEpochMillis - startEpochMillis; }
    public Status getStatus() { return status; }
    public String getErrorType() { return errorType; }
    public String getErrorMessage() { return errorMessage; }
}
