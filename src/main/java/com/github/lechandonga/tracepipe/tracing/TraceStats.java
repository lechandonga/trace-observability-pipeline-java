package com.github.lechandonga.tracepipe.tracing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 追踪组件的运行时计数。所有计数器线程安全、单调递增，供只读状态入口读取。
 *
 * <p>失败区分两类：
 * <ul>
 *   <li>{@code failedRequests} —— 被追踪的业务调用失败（异常）；</li>
 *   <li>{@code serializationFailures} / {@code exportFailures}
 *       —— 追踪链路自身失败（序列化、导出），与业务失败互不影响。</li>
 * </ul>
 */
public final class TraceStats {

    private final AtomicLong requestsStarted = new AtomicLong();
    private final AtomicLong requestsFailed = new AtomicLong();
    private final AtomicLong sampledTraces = new AtomicLong();
    private final AtomicLong spansCreated = new AtomicLong();
    private final AtomicLong spansTruncated = new AtomicLong();
    private final AtomicLong tracesRejected = new AtomicLong();
    private final AtomicLong exportedSpans = new AtomicLong();
    private final AtomicLong droppedBacklogFull = new AtomicLong();
    private final AtomicLong exportFailures = new AtomicLong();
    private final AtomicLong serializationFailures = new AtomicLong();

    public void incRequestsStarted() { requestsStarted.incrementAndGet(); }
    public void incRequestsFailed() { requestsFailed.incrementAndGet(); }
    public void incSampledTraces() { sampledTraces.incrementAndGet(); }
    public void incSpansCreated() { spansCreated.incrementAndGet(); }
    public void incSpansTruncated() { spansTruncated.incrementAndGet(); }
    public void incTracesRejected() { tracesRejected.incrementAndGet(); }
    public void incExportedSpans() { exportedSpans.incrementAndGet(); }
    public void incDroppedBacklogFull() { droppedBacklogFull.incrementAndGet(); }
    public void incExportFailures() { exportFailures.incrementAndGet(); }
    public void incSerializationFailures() { serializationFailures.incrementAndGet(); }

    public long requestsStarted() { return requestsStarted.get(); }
    public long requestsFailed() { return requestsFailed.get(); }
    public long sampledTraces() { return sampledTraces.get(); }
    public long spansCreated() { return spansCreated.get(); }
    public long spansTruncated() { return spansTruncated.get(); }
    public long tracesRejected() { return tracesRejected.get(); }
    public long exportedSpans() { return exportedSpans.get(); }
    public long droppedBacklogFull() { return droppedBacklogFull.get(); }
    public long exportFailures() { return exportFailures.get(); }
    public long serializationFailures() { return serializationFailures.get(); }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("requestsStarted", requestsStarted.get());
        map.put("requestsFailed", requestsFailed.get());
        map.put("sampledTraces", sampledTraces.get());
        map.put("spansCreated", spansCreated.get());
        map.put("spansTruncatedBySpanLimit", spansTruncated.get());
        map.put("tracesRejectedByTraceLimit", tracesRejected.get());
        map.put("exportedSpans", exportedSpans.get());
        map.put("droppedBacklogFull", droppedBacklogFull.get());
        map.put("exportFailures", exportFailures.get());
        map.put("serializationFailures", serializationFailures.get());
        return map;
    }
}
