package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 有界的未完成链路跨度缓存。
 * - 缓存链路数超过 maxCachedTraces 时按最久未访问淘汰；
 * - 单条链路跨度超过 maxSpansPerTrace 时截断并计数。
 */
public class SpanBuffer {

    private final int maxCachedTraces;
    private final int maxSpansPerTrace;
    private final TraceMetrics metrics;

    private final Map<String, List<Span>> spansByTrace;

    public SpanBuffer(TraceProperties properties, TraceMetrics metrics) {
        this.maxCachedTraces = properties.getMaxCachedTraces();
        this.maxSpansPerTrace = properties.getMaxSpansPerTrace();
        this.metrics = metrics;
        this.spansByTrace = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Span>> eldest) {
                boolean remove = size() > maxCachedTraces;
                if (remove) {
                    metrics.incTraceEvicted();
                }
                return remove;
            }
        };
    }

    public synchronized void add(Span span) {
        List<Span> spans = spansByTrace.computeIfAbsent(span.getTraceId(), k -> new ArrayList<>());
        if (spans.size() >= maxSpansPerTrace) {
            metrics.incSpanTruncated();
            return;
        }
        spans.add(span);
    }

    /** 取出并移除一条链路的全部跨度（根跨度结束时调用）。 */
    public synchronized List<Span> drain(String traceId) {
        List<Span> spans = spansByTrace.remove(traceId);
        return spans == null ? List.of() : spans;
    }

    public synchronized int cachedTraceCount() {
        return spansByTrace.size();
    }
}
