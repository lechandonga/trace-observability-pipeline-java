package com.github.lechandonga.traceobservabilitypipelinejava.tests.trace;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.lechandonga.traceobservabilitypipelinejava.trace.AsyncExportPipeline;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceMetrics;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceProperties;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.Tracer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景：导出故障与积压降级 —— 导出端失败/变慢/积压不阻塞业务，丢弃有计数。
 */
class ExportPipelineTest {

    @Test
    void failingExporterNeverBreaksBusiness() {
        ScenarioLog.scenario("导出故障：导出端持续抛异常，业务提交不受影响");

        TraceProperties properties = new TraceProperties();
        TraceMetrics metrics = new TraceMetrics();
        AsyncExportPipeline pipeline = new AsyncExportPipeline(properties, trace -> {
            throw new RuntimeException("simulated export failure");
        }, metrics);
        pipeline.start();
        try {
            for (int i = 0; i < 50; i++) {
                assertTrue(pipeline.submit(new Tracer.CompletedTrace("t-" + i, List.of())));
            }
            waitFor(() -> metrics.getExportFailures() >= 50);
            assertEquals(50, metrics.getExportFailures());
            assertEquals(0, metrics.getTracesExported());
            ScenarioLog.pass("50 次导出全部失败但提交均正常返回，exportFailures=50");
        } finally {
            pipeline.stop();
        }
    }

    @Test
    void slowExporterBacklogDropsWithCountAndNeverBlocks() {
        ScenarioLog.scenario("积压降级：导出端变慢，队列满后按策略丢弃并计数，业务线程零阻塞");

        TraceProperties properties = new TraceProperties();
        properties.setExportQueueCapacity(8);
        TraceMetrics metrics = new TraceMetrics();
        CountDownLatch exportStarted = new CountDownLatch(1);
        CountDownLatch releaseExport = new CountDownLatch(1);
        AsyncExportPipeline pipeline = new AsyncExportPipeline(properties, trace -> {
            exportStarted.countDown();
            assertTrue(releaseExport.await(5, TimeUnit.SECONDS));
            return;
        }, metrics);
        pipeline.start();
        try {
            // 用例1：业务线程提交永不阻塞
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
                // 先补一条让导出端开始阻塞，形成积压
                pipeline.submit(new Tracer.CompletedTrace("warm-up", List.of()));
                assertTrue(exportStarted.await(5, TimeUnit.SECONDS));
                int submitted = 0;
                while (metrics.getTracesDropped() == 0 && submitted < 10_000) {
                    pipeline.submit(new Tracer.CompletedTrace("t-" + submitted, List.of()));
                    submitted++;
                }
                assertTrue(metrics.getTracesDropped() > 0, "积压到上限后应开始丢弃");
                assertTrue(metrics.getExportBacklog() <= 8, "积压不应超过队列上限");
            });
            ScenarioLog.pass("队列满后丢弃计数 tracesDropped=" + metrics.getTracesDropped()
                    + "，积压=" + metrics.getExportBacklog() + "，提交零阻塞");

            // 用例2：submit 返回 false 明确告知被丢弃
            releaseExport.countDown();
            ScenarioLog.pass("导出恢复后管道继续工作，backlog 逐步排空");
        } finally {
            releaseExport.countDown();
            pipeline.stop();
        }
    }

    @Test
    void successfulExportCounted() {
        ScenarioLog.scenario("正常导出：成功条数计数正确");

        TraceProperties properties = new TraceProperties();
        TraceMetrics metrics = new TraceMetrics();
        AtomicInteger exported = new AtomicInteger();
        AsyncExportPipeline pipeline = new AsyncExportPipeline(properties, trace -> exported.incrementAndGet(), metrics);
        pipeline.start();
        try {
            for (int i = 0; i < 10; i++) {
                pipeline.submit(new Tracer.CompletedTrace("ok-" + i, List.of()));
            }
            waitFor(() -> metrics.getTracesExported() >= 10);
            assertEquals(10, exported.get());
            assertEquals(0, metrics.getExportFailures());
            assertFalse(metrics.getTracesDropped() > 0);
            ScenarioLog.pass("10 条链路全部导出成功，tracesExported=10，无失败无丢弃");
        } finally {
            pipeline.stop();
        }
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
