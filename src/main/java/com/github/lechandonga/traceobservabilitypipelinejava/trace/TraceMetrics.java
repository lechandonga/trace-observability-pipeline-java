package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 运行时计数器。业务失败与追踪自身失败分开统计。
 */
public class TraceMetrics {

    private final AtomicLong requestsTotal = new AtomicLong();
    private final AtomicLong requestsFailed = new AtomicLong();
    private final AtomicLong spansTruncated = new AtomicLong();
    private final AtomicLong tracesEvicted = new AtomicLong();
    private final AtomicLong tracesDropped = new AtomicLong();
    private final AtomicLong tracesExported = new AtomicLong();
    private final AtomicLong exportFailures = new AtomicLong();
    private final AtomicLong tracingInternalErrors = new AtomicLong();

    private volatile Supplier<Integer> backlogSupplier = () -> 0;

    public void incRequests() { requestsTotal.incrementAndGet(); }
    public void incRequestFailed() { requestsFailed.incrementAndGet(); }
    public void incSpanTruncated() { spansTruncated.incrementAndGet(); }
    public void incTraceEvicted() { tracesEvicted.incrementAndGet(); }
    public void incTraceDropped() { tracesDropped.incrementAndGet(); }
    public void incTraceExported() { tracesExported.incrementAndGet(); }
    public void incExportFailure() { exportFailures.incrementAndGet(); }
    public void incTracingInternalError() { tracingInternalErrors.incrementAndGet(); }

    public void setBacklogSupplier(Supplier<Integer> backlogSupplier) {
        this.backlogSupplier = backlogSupplier;
    }

    public long getRequestsTotal() { return requestsTotal.get(); }
    public long getRequestsFailed() { return requestsFailed.get(); }
    public long getSpansTruncated() { return spansTruncated.get(); }
    public long getTracesEvicted() { return tracesEvicted.get(); }
    public long getTracesDropped() { return tracesDropped.get(); }
    public long getTracesExported() { return tracesExported.get(); }
    public long getExportFailures() { return exportFailures.get(); }
    public long getTracingInternalErrors() { return tracingInternalErrors.get(); }
    public int getExportBacklog() { return backlogSupplier.get(); }

    /** 只读快照，供状态端点输出。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("requestsTotal", getRequestsTotal());
        map.put("requestsFailed", getRequestsFailed());
        map.put("tracesExported", getTracesExported());
        map.put("tracesDropped", getTracesDropped());
        map.put("tracesEvicted", getTracesEvicted());
        map.put("spansTruncated", getSpansTruncated());
        map.put("exportFailures", getExportFailures());
        map.put("exportBacklog", getExportBacklog());
        map.put("tracingInternalErrors", getTracingInternalErrors());
        return map;
    }
}
