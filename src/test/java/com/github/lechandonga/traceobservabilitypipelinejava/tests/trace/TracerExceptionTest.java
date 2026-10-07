package com.github.lechandonga.traceobservabilitypipelinejava.tests.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.github.lechandonga.traceobservabilitypipelinejava.trace.AsyncExportPipeline;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.Span;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.SpanBuffer;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceMetrics;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceProperties;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.Tracer;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceScope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 场景：异常标注 —— 业务异常原样上抛且链路标记失败；追踪自身出错不影响业务且分开计数。
 */
class TracerExceptionTest {

    @Test
    void businessExceptionMarkedAndRethrownUnchanged() {
        ScenarioLog.scenario("异常标注：业务异常标记到链路并原样上抛");

        TraceProperties properties = new TraceProperties();
        TraceMetrics metrics = new TraceMetrics();
        List<Tracer.CompletedTrace> exported = new ArrayList<>();
        AsyncExportPipeline pipeline = new AsyncExportPipeline(properties, exported::add, metrics);
        pipeline.start();
        Tracer tracer = new Tracer(properties, new SpanBuffer(properties, metrics), pipeline, metrics);

        IllegalStateException businessError = new IllegalStateException("biz failed");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            try (TraceScope scope = tracer.startSpan("failing-op")) {
                scope.recordError(businessError);
                throw businessError;
            }
        });
        assertSame(businessError, thrown);
        ScenarioLog.pass("业务异常按原类型原实例上抛，未被追踪逻辑吞掉或包装");

        waitFor(() -> !exported.isEmpty());
        Span root = exported.get(0).spans().stream()
                .filter(s -> s.getParentSpanId() == null).findFirst().orElseThrow();
        assertEquals(Span.Status.ERROR, root.getStatus());
        assertEquals(IllegalStateException.class.getName(), root.getErrorType());
        assertEquals("biz failed", root.getErrorMessage());
        assertEquals(0, metrics.getTracingInternalErrors());
        pipeline.stop();
        ScenarioLog.pass("链路根跨度标记 ERROR，errorType/errorMessage 完整，tracingInternalErrors=0");
    }

    @Test
    void tracingInternalFailureNeverAffectsBusiness() {
        ScenarioLog.scenario("异常标注：追踪自身失败与业务失败分开计数，业务照常返回");

        TraceProperties properties = new TraceProperties();
        TraceMetrics metrics = new TraceMetrics();
        AsyncExportPipeline pipeline = new AsyncExportPipeline(properties, trace -> {
            throw new RuntimeException("export boom");
        }, metrics);
        pipeline.start();
        Tracer tracer = new Tracer(properties, new SpanBuffer(properties, metrics), pipeline, metrics);

        AtomicReference<String> result = new AtomicReference<>();
        try (TraceScope ignored = tracer.startSpan("op")) {
            result.set("business-ok");
        }
        assertEquals("business-ok", result.get(), "追踪故障不得影响业务返回");
        waitFor(() -> metrics.getExportFailures() > 0);
        assertEquals(0, metrics.getRequestsFailed());
        pipeline.stop();
        ScenarioLog.pass("导出失败计入 exportFailures=" + metrics.getExportFailures()
                + "，业务结果不受影响，两类失败可区分");
    }

    private static void waitFor(Check check) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (check.done()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("condition not met within timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean done();
    }
}
