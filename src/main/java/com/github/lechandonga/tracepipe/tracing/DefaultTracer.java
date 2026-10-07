package com.github.lechandonga.tracepipe.tracing;

import java.util.function.Consumer;

/**
 * 默认追踪实现。
 *
 * <p>采样与截断规则：
 * <ul>
 *   <li>子 span 继承父 trace 的采样结果，保证整条链路一致；</li>
 *   <li>新 trace 由 {@link Sampler} 决策，再受活动 trace 数上限约束；</li>
 *   <li>单 trace span 数超限时，后续 span 降级为非采样占位（业务照常执行）；</li>
 *   <li>任何追踪内部异常都被吞掉并计数，业务无感知。</li>
 * </ul>
 */
public final class DefaultTracer implements Tracer {

    private final Sampler sampler;
    private final TraceRegistry registry;
    private final TraceStats stats;

    public DefaultTracer(Sampler sampler, TraceRegistry registry, TraceStats stats) {
        this.sampler = sampler;
        this.registry = registry;
        this.stats = stats;
    }

    @Override
    public ActiveSpan startSpan(String name) {
        return doStart(name, TraceContextHolder.get());
    }

    @Override
    public ActiveSpan startRootSpan(String name) {
        TraceContextHolder.clear();
        return doStart(name, null);
    }

    private ActiveSpan doStart(String name, TraceContext parent) {
        boolean root = parent == null;

        String traceId;
        String spanId = Ids.spanId();
        String parentSpanId = null;
        boolean sampled;

        if (root) {
            traceId = Ids.traceId();
            sampled = sampler.sample();
            if (sampled && !registry.register(traceId)) {
                // 活动 trace 缓存已满：这条 trace 整体降级为不采样
                sampled = false;
                stats.incTracesRejected();
            }
            if (sampled) {
                stats.incSampledTraces();
            }
        } else {
            traceId = parent.traceId();
            parentSpanId = parent.spanId();
            sampled = parent.sampled();
        }

        if (sampled && !registry.tryAcquireSpan(traceId)) {
            // 单条 trace span 数超限：截断该 span
            sampled = false;
            stats.incSpansTruncated();
        }

        stats.incSpansCreated();
        Span span = new Span(traceId, spanId, parentSpanId, name, System.nanoTime(), sampled);
        Consumer<FinishedSpan> sink = sampled
                ? (root ? registry::finishRoot : registry::finishChild)
                : null;
        return new ActiveSpan(span, parent, root, sink, stats::incExportFailures);
    }

    @Override
    public ActiveSpan startScheduledSpan(String name) {
        // 定时任务没有父上下文，startSpan 自然开新 trace；
        // 若调用方在线程上残留了上下文（防御性场景），先清掉。
        TraceContextHolder.clear();
        return startSpan(name);
    }

    @Override
    public Runnable wrap(Runnable task) {
        TraceContext captured = TraceContextHolder.get();
        String capturedMdcTrace = ActiveSpan.mdcTraceId();
        String capturedMdcSpan = ActiveSpan.mdcSpanId();
        return () -> {
            TraceContext previous = TraceContextHolder.get();
            String prevMdcTrace = org.slf4j.MDC.get(ActiveSpan.MDC_TRACE_ID);
            String prevMdcSpan = org.slf4j.MDC.get(ActiveSpan.MDC_SPAN_ID);
            try {
                if (captured != null) {
                    TraceContextHolder.set(captured);
                    if (captured.sampled()) {
                        if (capturedMdcTrace != null) {
                            org.slf4j.MDC.put(ActiveSpan.MDC_TRACE_ID, capturedMdcTrace);
                        }
                        if (capturedMdcSpan != null) {
                            org.slf4j.MDC.put(ActiveSpan.MDC_SPAN_ID, capturedMdcSpan);
                        }
                    }
                }
                task.run();
            } finally {
                // 工作线程执行完毕：必须恢复进入前状态。
                // 线程池复用时 previous 通常为 null（上一个任务也应已清理干净），
                // 此时绝不允许把本次捕获的上下文残留在线程上。
                restoreMdc(ActiveSpan.MDC_TRACE_ID, prevMdcTrace);
                restoreMdc(ActiveSpan.MDC_SPAN_ID, prevMdcSpan);
                if (previous != null) {
                    TraceContextHolder.set(previous);
                } else {
                    TraceContextHolder.clear();
                }
            }
        };
    }

    private static void restoreMdc(String key, String previousValue) {
        if (previousValue != null) {
            org.slf4j.MDC.put(key, previousValue);
        } else {
            org.slf4j.MDC.remove(key);
        }
    }

    @Override
    public TraceStats stats() {
        return stats;
    }

    @Override
    public TraceRegistry registry() {
        return registry;
    }
}
