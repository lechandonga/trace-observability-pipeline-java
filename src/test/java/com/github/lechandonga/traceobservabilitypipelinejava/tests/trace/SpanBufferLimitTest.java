package com.github.lechandonga.traceobservabilitypipelinejava.tests.trace;

import java.util.List;

import com.github.lechandonga.traceobservabilitypipelinejava.trace.AsyncExportPipeline;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.Span;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.SpanBuffer;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceMetrics;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceProperties;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.Tracer;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceScope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景：各类上限截断 —— 单链路跨度上限、缓存链路上限、采样率边界。
 */
class SpanBufferLimitTest {

    @Test
    void spansPerTraceTruncatedAtLimit() {
        ScenarioLog.scenario("上限截断：单条链路跨度数超限被截断并计数");

        TraceProperties properties = new TraceProperties();
        properties.setMaxSpansPerTrace(4);
        TraceMetrics metrics = new TraceMetrics();
        SpanBuffer buffer = new SpanBuffer(properties, metrics);

        Tracer tracer = new Tracer(properties, buffer,
                new AsyncExportPipeline(properties, trace -> { }, metrics), metrics);
        try (TraceScope ignored = tracer.startSpan("root")) {
            for (int i = 0; i < 10; i++) {
                try (TraceScope child = tracer.startSpan("child-" + i)) {
                    // 立即结束
                }
            }
        }
        assertEquals(0, buffer.cachedTraceCount(), "根跨度结束后链路应已从缓存排空");
        assertEquals(6, metrics.getSpansTruncated(), "10 个子跨度超过上限 4，应截断 6 个");
        ScenarioLog.pass("maxSpansPerTrace=4，10 个子跨度截断 6 个，spansTruncated=6");
    }

    @Test
    void cachedTracesEvictedAtLimit() {
        ScenarioLog.scenario("上限截断：缓存链路数超限按最久未访问淘汰并计数");

        TraceProperties properties = new TraceProperties();
        properties.setMaxCachedTraces(3);
        TraceMetrics metrics = new TraceMetrics();
        SpanBuffer buffer = new SpanBuffer(properties, metrics);

        for (int t = 0; t < 10; t++) {
            String traceId = "trace-" + t;
            for (int s = 0; s < 2; s++) {
                buffer.add(new Span(traceId, null, "span-" + s));
            }
        }
        assertEquals(3, buffer.cachedTraceCount(), "缓存链路数不应超过上限 3");
        assertEquals(7, metrics.getTracesEvicted(), "10 条链路淘汰 7 条");
        // 最久未访问的 trace-0 应已被淘汰，最新的 trace-9 仍在
        assertEquals(0, buffer.drain("trace-0").size());
        assertEquals(2, buffer.drain("trace-9").size());
        ScenarioLog.pass("maxCachedTraces=3，10 条链路淘汰 7 条，tracesEvicted=7，缓存有界");
    }

    @Test
    void samplingDecisionConsistentWithinTrace() {
        ScenarioLog.scenario("采样一致性：同一 traceId 决策恒定，速率边界正确");

        TraceProperties properties = new TraceProperties();
        TraceMetrics metrics = new TraceMetrics();
        Tracer tracer = new Tracer(properties, new SpanBuffer(properties, metrics),
                new AsyncExportPipeline(properties, trace -> { }, metrics), metrics);

        properties.setSamplingRate(0.5);
        for (int i = 0; i < 200; i++) {
            String traceId = "id-" + i;
            boolean first = tracer.decideSampled(traceId);
            for (int j = 0; j < 5; j++) {
                assertEquals(first, tracer.decideSampled(traceId), "同一 traceId 采样决策必须一致");
            }
        }
        ScenarioLog.pass("200 个 traceId 各判定 6 次，结果全部一致");

        properties.setSamplingRate(1.0);
        assertTrue(tracer.decideSampled("anything"));
        properties.setSamplingRate(0.0);
        assertTrue(!tracer.decideSampled("anything"));
        ScenarioLog.pass("samplingRate=1.0 全采、0.0 全不采");
    }

    @Test
    void exportQueueCapacityRespected() {
        ScenarioLog.scenario("上限截断：导出队列容量有界，溢出即丢弃");

        TraceProperties properties = new TraceProperties();
        properties.setExportQueueCapacity(2);
        TraceMetrics metrics = new TraceMetrics();
        // 不启动 worker，队列必然积压
        AsyncExportPipeline pipeline = new AsyncExportPipeline(properties, trace -> { }, metrics);
        assertTrue(pipeline.submit(new Tracer.CompletedTrace("a", List.of())));
        assertTrue(pipeline.submit(new Tracer.CompletedTrace("b", List.of())));
        assertTrue(!pipeline.submit(new Tracer.CompletedTrace("c", List.of())), "容量 2 满后第三条应被拒");
        assertEquals(1, metrics.getTracesDropped());
        assertEquals(2, pipeline.backlog());
        pipeline.stop();
        ScenarioLog.pass("exportQueueCapacity=2，第三条被丢弃，tracesDropped=1，backlog 恒有界");
    }
}
