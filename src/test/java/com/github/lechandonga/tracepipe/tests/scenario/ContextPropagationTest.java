package com.github.lechandonga.tracepipe.tests.scenario;

import com.github.lechandonga.tracepipe.tests.support.TestTracingFactory;
import com.github.lechandonga.tracepipe.tests.support.TestTracingFactory.Harness;
import com.github.lechandonga.tracepipe.tracing.ActiveSpan;
import com.github.lechandonga.tracepipe.tracing.FinishedSpan;
import com.github.lechandonga.tracepipe.tracing.TraceContext;
import com.github.lechandonga.tracepipe.tracing.TraceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 场景：并发上下文传递 —— 线程池复用、异步子任务挂接、无串链无残留。
 */
class ContextPropagationTest {

    @AfterEach
    void clear() {
        TraceContextHolder.clear();
    }

    @Test
    @DisplayName("异步任务继承父链路 traceId，父子 span 可还原，采样结果一致")
    void asyncTaskInheritsTrace() throws Exception {
        Harness h = TestTracingFactory.withInMemorySink(1.0, 10, 100);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (ActiveSpan root = h.tracer.startSpan("root")) {
            String rootTrace = root.span().traceId();

            Future<?> future = pool.submit(h.tracer.wrap(() -> {
                TraceContext captured = TraceContextHolder.get();
                try (ActiveSpan child = h.tracer.startSpan("async-child")) {
                    // startSpan 后当前上下文切到子 span，但 traceId/采样结果与父一致
                    assertEquals(rootTrace, child.span().traceId());
                    assertEquals(root.span().spanId(), child.span().parentSpanId());
                    assertEquals(captured.traceId(), TraceContextHolder.get().traceId());
                    assertEquals(child.span().spanId(), TraceContextHolder.get().spanId());
                }
                // 子 span 关闭后回到捕获的父上下文（仍在 wrap 的作用域内）
                assertEquals(rootTrace, TraceContextHolder.get().traceId());
            }));

            // 业务主线程上下文不受异步任务影响
            assertEquals(rootTrace, TraceContextHolder.get().traceId());
            future.get();
        }

        // 任务结束后工作线程必须被清理（wrap 的 finally 负责）
        assertNull(runOnPool(pool, () -> {
            assertNull(TraceContextHolder.get());
            return null;
        }));

        List<FinishedSpan> spans = new ArrayList<>(h.exported);
        assertEquals(2, spans.size());
        FinishedSpan child = spans.get(0);
        FinishedSpan root = spans.get(1);
        assertEquals(root.traceId(), child.traceId());
        assertEquals(root.spanId(), child.parentSpanId());
        assertEquals(1, spans.stream().map(FinishedSpan::traceId).distinct().count());
        pool.shutdown();
    }

    private static <T> T runOnPool(ExecutorService pool, java.util.concurrent.Callable<T> task)
            throws Exception {
        return pool.submit(task).get();
    }

    @Test
    @DisplayName("单线程串行复用：两个请求不会串链，结束后无线程上下文残留")
    void sequentialRequestsDoNotBleed() {
        Harness h = TestTracingFactory.withInMemorySink(1.0, 10, 100);
        String firstTrace;
        try (ActiveSpan root = h.tracer.startSpan("req-1")) {
            firstTrace = root.span().traceId();
        }
        assertNull(TraceContextHolder.get());
        try (ActiveSpan root = h.tracer.startSpan("req-2")) {
            assertNotEquals(firstTrace, root.span().traceId());
            assertNull(root.span().parentSpanId());
        }
        assertNull(TraceContextHolder.get());
    }

    @Test
    @DisplayName("线程池被多个请求并发复用时，每个任务只看到自己的链路")
    void pooledThreadsNeverCrossTalk() throws Exception {
        Harness h = TestTracingFactory.withInMemorySink(1.0, 100, 100);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int requests = 20;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < requests; i++) {
            final ActiveSpan root = h.tracer.startSpan("req-" + i);
            final String expectedTrace = root.span().traceId();
            Runnable captured = h.tracer.wrap(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                TraceContext ctx = TraceContextHolder.get();
                if (ctx == null || !expectedTrace.equals(ctx.traceId())) {
                    throw new IllegalStateException("context cross-talk detected");
                }
            });
            futures.add(pool.submit(() -> {
                captured.run();
                return expectedTrace;
            }));
            root.close();
        }
        start.countDown();
        for (Future<String> f : futures) {
            f.get();
        }
        pool.shutdown();
    }

    @Test
    @DisplayName("父链路不采样时，异步子链路同样不采样（采样结果全链路一致）")
    void samplingDecisionPropagatesWhenUnsampled() throws Exception {
        Harness h = TestTracingFactory.withInMemorySink(1.0, 100, 100);
        // rate=0 的独立 tracer
        Harness unsampled = TestTracingFactory.create(0.0, 10, 100, f -> { });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (ActiveSpan root = unsampled.tracer.startSpan("unsampled-root")) {
            Future<?> future = pool.submit(unsampled.tracer.wrap(() -> {
                try (ActiveSpan child = unsampled.tracer.startSpan("child")) {
                    assertEquals(root.span().traceId(), child.span().traceId());
                    assertEquals(false, child.span().sampled());
                }
            }));
            future.get();
        }
        assertEquals(0, h.stats.sampledTraces());
        pool.shutdown();
    }

}
