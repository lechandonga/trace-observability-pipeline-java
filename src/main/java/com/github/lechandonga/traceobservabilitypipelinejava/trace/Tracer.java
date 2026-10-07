package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;

/**
 * 追踪核心。任何追踪内部异常都被捕获并计入 tracingInternalErrors，
 * 绝不影响业务调用。
 */
public class Tracer {

    private final TraceProperties properties;
    private final SpanBuffer buffer;
    private final AsyncExportPipeline exportPipeline;
    private final TraceMetrics metrics;

    public Tracer(TraceProperties properties, SpanBuffer buffer,
                  AsyncExportPipeline exportPipeline, TraceMetrics metrics) {
        this.properties = properties;
        this.buffer = buffer;
        this.exportPipeline = exportPipeline;
        this.metrics = metrics;
    }

    /** 开启一段跨度；任何失败都退化为 NOOP，不影响业务。 */
    public TraceScope startSpan(String name) {
        try {
            TraceContext.CurrentSpan parent = TraceContext.current();
            String traceId = parent != null ? parent.traceId() : newTraceId();
            boolean sampled = parent != null ? parent.sampled() : decideSampled(traceId);
            String parentSpanId = parent != null ? parent.spanId() : null;

            Span span = new Span(traceId, parentSpanId, name);
            TraceContext.push(new TraceContext.CurrentSpan(traceId, span.getSpanId(), sampled));

            String prevTraceId = MDC.get("traceId");
            String prevSpanId = MDC.get("spanId");
            MDC.put("traceId", traceId);
            MDC.put("spanId", span.getSpanId());

            return new TraceScope(this, span, prevTraceId, prevSpanId);
        } catch (Throwable t) {
            metrics.incTracingInternalError();
            return TraceScope.NOOP;
        }
    }

    void finishSpan(Span span, String previousMdcTraceId, String previousMdcSpanId) {
        try {
            span.finish();
            TraceContext.CurrentSpan current = TraceContext.current();
            boolean isRoot = current != null
                    && current.spanId().equals(span.getSpanId())
                    && span.getParentSpanId() == null;
            boolean sampled = current != null && current.sampled();
            TraceContext.pop();
            restoreMdc("traceId", previousMdcTraceId);
            restoreMdc("spanId", previousMdcSpanId);

            if (!sampled) {
                return;
            }
            if (isRoot) {
                List<Span> spans = new ArrayList<>(buffer.drain(span.getTraceId()));
                spans.add(span);
                exportPipeline.submit(new CompletedTrace(span.getTraceId(), spans));
            } else {
                buffer.add(span);
            }
        } catch (Throwable t) {
            metrics.incTracingInternalError();
        }
    }

    /** 采样决策只依赖 traceId，保证同一条链路内结果一致。 */
    public boolean decideSampled(String traceId) {
        double rate = Math.max(0.0, Math.min(1.0, properties.getSamplingRate()));
        if (rate >= 1.0) {
            return true;
        }
        if (rate <= 0.0) {
            return false;
        }
        return Math.floorMod(traceId.hashCode(), 10_000) < rate * 10_000;
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void restoreMdc(String key, String previous) {
        try {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        } catch (Throwable ignored) {
            // MDC 不可用时静默降级
        }
    }

    /** 一条已完成待导出的链路。 */
    public record CompletedTrace(String traceId, List<Span> spans) {}
}
