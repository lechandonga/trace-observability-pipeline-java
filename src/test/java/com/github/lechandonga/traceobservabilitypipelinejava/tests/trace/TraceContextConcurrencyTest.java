package com.github.lechandonga.traceobservabilitypipelinejava.tests.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceContext;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceTaskDecorator;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.Tracer;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.AsyncExportPipeline;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.SpanBuffer;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceMetrics;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceProperties;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceScope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景：并发上下文传递 —— 异步任务挂到发起链路、线程复用不串上下文、无残留。
 */
class TraceContextConcurrencyTest {

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    @Test
    void asyncTaskJoinsInitiatingTraceAndThreadReuseStaysClean() throws Exception {
        ScenarioLog.scenario("并发上下文传递：异步任务挂载发起链路 + 线程复用隔离");

        TraceProperties properties = new TraceProperties();
        TraceMetrics metrics = new TraceMetrics();
        AsyncExportPipeline pipeline = new AsyncExportPipeline(properties, trace -> { }, metrics);
        Tracer tracer = new Tracer(properties, new SpanBuffer(properties, metrics), pipeline, metrics);
        TraceTaskDecorator decorator = new TraceTaskDecorator();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<String> asyncTraceIds = new ArrayList<>();
            List<String> mainTraceIds = new ArrayList<>();
            int requestCount = 20;
            CountDownLatch latch = new CountDownLatch(requestCount);

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(decorator.decorate(() -> {
                    // 每个“请求”在受装饰的任务里开启自己的根跨度
                    try (TraceScope scope = tracer.startSpan("request")) {
                        String traceId = scope.traceId();
                        mainTraceIds.add(traceId);
                        // 模拟请求内再发起异步子任务：应挂到同一条链路
                        TraceContext.Snapshot snapshot = TraceContext.snapshot();
                        executor.submit(decorator.decorate(() -> {
                            TraceContext.CurrentSpan current = TraceContext.current();
                            asyncTraceIds.add(current == null ? null : current.traceId());
                            latch.countDown();
                        }));
                    } finally {
                        TraceContext.clear();
                    }
                })));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            assertTrue(latch.await(10, TimeUnit.SECONDS), "异步子任务应全部完成");

            // 用例1：异步子任务全部挂到发起它们的链路上
            assertEquals(requestCount, asyncTraceIds.size());
            asyncTraceIds.forEach(id -> assertTrue(mainTraceIds.contains(id),
                    "异步任务必须挂到发起请求的 traceId"));
            ScenarioLog.pass("异步任务挂到发起链路（" + requestCount + " 个请求全部匹配）");

            // 用例2：不同请求的 traceId 互不相同（线程池只有2个线程，复用下也不串）
            assertEquals(requestCount, mainTraceIds.stream().distinct().count());
            ScenarioLog.pass("线程复用下链路互不串扰（2 线程承载 " + requestCount + " 条链路）");

            // 用例3：任务结束后线程上无上下文残留
            Future<Boolean> residue = executor.submit(() -> TraceContext.current() == null);
            assertTrue(residue.get(5, TimeUnit.SECONDS), "工作线程上不应残留上下文");
            ScenarioLog.pass("线程复用后无上下文残留");
        } finally {
            executor.shutdownNow();
            pipeline.stop();
        }
    }

    @Test
    void restoreReplacesWholeStackWithoutLeak() {
        ScenarioLog.scenario("并发上下文传递：快照恢复整体替换，无残留");

        TraceContext.restore(new TraceContext.Snapshot(List.of(
                new TraceContext.CurrentSpan("trace-A", "span-1", true))));
        assertEquals("trace-A", TraceContext.current().traceId());

        // 恢复空快照后必须彻底清空
        TraceContext.restore(TraceContext.Snapshot.empty());
        assertNull(TraceContext.current());

        // 再次恢复另一条链路，不应看到 trace-A 的任何痕迹
        TraceContext.restore(new TraceContext.Snapshot(List.of(
                new TraceContext.CurrentSpan("trace-B", "span-9", true))));
        assertEquals("trace-B", TraceContext.current().traceId());
        assertNotEquals("trace-A", TraceContext.current().traceId());
        TraceContext.clear();
        assertNull(TraceContext.current());
        ScenarioLog.pass("快照恢复整体替换、clear 后无残留");
    }
}
