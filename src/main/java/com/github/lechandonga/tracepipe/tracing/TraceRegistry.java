package com.github.lechandonga.tracepipe.tracing;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 内存中活动链路的有界注册表，负责两道上限：
 * <ol>
 *   <li><b>活动 trace 数上限</b>（{@code maxTraces}）：超过后新 trace 不再采样，
 *       记 {@code tracesRejectedByTraceLimit}，避免长期运行无界增长；</li>
 *   <li><b>单条 trace 的 span 数上限</b>（{@code maxSpansPerTrace}）：
 *       超过的 span 被截断，记 {@code spansTruncatedBySpanLimit}。</li>
 * </ol>
 *
 * <p>根 Span 结束（请求返回）后，其异步子任务仍可能短暂产生 span，因此条目做
 * 延迟淘汰：保留 {@code retentionMillis} 后在下一次注册/访问时顺带清理。
 */
public final class TraceRegistry {

    private static final class Entry {
        final long createdAtNanos = System.nanoTime();
        final AtomicSpans spans = new AtomicSpans();
        volatile long finishedAtNanos = -1L;
    }

    private static final class AtomicSpans {
        private int value;

        private synchronized int increment() {
            return ++value;
        }

        private synchronized int get() {
            return value;
        }
    }

    private final int maxTraces;
    private final int maxSpansPerTrace;
    private final long retentionMillis;
    private final TraceStats stats;
    private final Consumer<FinishedSpan> spanSink;
    private final ConcurrentHashMap<String, Entry> active = new ConcurrentHashMap<>();

    public TraceRegistry(TracingProperties properties, TraceStats stats,
                         Consumer<FinishedSpan> spanSink) {
        this.maxTraces = properties.getMaxTraces();
        this.maxSpansPerTrace = properties.getMaxSpansPerTrace();
        this.retentionMillis = properties.getTraceRetentionMillis();
        this.stats = stats;
        this.spanSink = spanSink;
    }

    /**
     * 注册一条新的已采样 trace。容量不足时拒绝（返回 false），该 trace 降级为不采样。
     */
    public synchronized boolean register(String traceId) {
        evictExpired();
        if (active.size() >= maxTraces) {
            return false;
        }
        active.putIfAbsent(traceId, new Entry());
        return true;
    }

    /**
     * 在某条 trace 上尝试开始一个 span。返回 false 表示命中单 trace span 上限，
     * 调用方应截断该 span（仍执行业务，只是不产生链路数据）。
     */
    public boolean tryAcquireSpan(String traceId) {
        Entry entry = active.get(traceId);
        if (entry == null) {
            return false;
        }
        if (entry.spans.increment() > maxSpansPerTrace) {
            return false;
        }
        return true;
    }

    /** 根 Span 结束：标记淘汰时间点并立即导出根 Span。 */
    public void finishRoot(FinishedSpan root) {
        Entry entry = active.get(root.traceId());
        if (entry != null) {
            entry.finishedAtNanos = System.nanoTime();
        }
        spanSink.accept(root);
    }

    /** 非根 Span 结束：直接交给导出入口（异步、有界、可丢弃）。 */
    public void finishChild(FinishedSpan child) {
        spanSink.accept(child);
    }

    public int activeTraceCount() {
        return active.size();
    }

    private void evictExpired() {
        long now = System.nanoTime();
        Iterator<Map.Entry<String, Entry>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            long finishedAt = e.getValue().finishedAtNanos;
            long base = finishedAt >= 0 ? finishedAt : e.getValue().createdAtNanos;
            if (now - base > retentionMillis * 1_000_000L) {
                it.remove();
            }
        }
    }
}
