package com.github.lechandonga.tracepipe.tests.scenario;

import com.github.lechandonga.tracepipe.tracing.FinishedSpan;
import com.github.lechandonga.tracepipe.tracing.TraceStats;
import com.github.lechandonga.tracepipe.tracing.export.AsyncSpanExporter;
import com.github.lechandonga.tracepipe.tracing.export.JsonSpanSerializer;
import com.github.lechandonga.tracepipe.tracing.export.LocalFileSpanExporter;
import com.github.lechandonga.tracepipe.tracing.export.SpanExporter;
import com.github.lechandonga.tracepipe.tracing.export.SpanSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景：导出故障与积压降级 —— 导出端变慢/失败、队列满、序列化失败，
 * 业务线程都不被阻塞，且按确定策略丢弃并留下计数。
 */
class ExportDegradationTest {

    private static FinishedSpan span(String name) {
        long now = System.nanoTime();
        return new FinishedSpan("t", "s" + name.hashCode(), "p", name,
                now, now + 1000, 1, true, false, null, null);
    }

    @Test
    @DisplayName("导出端持续失败：span 计入 exportFailures，offer 不阻塞不抛出")
    void failingExporterIsCountedAndDoesNotBreakCaller() {
        TraceStats stats = new TraceStats();
        AtomicInteger attempts = new AtomicInteger();
        SpanExporter failing = batch -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("export backend down");
        };
        AsyncSpanExporter exporter = new AsyncSpanExporter(
                failing, new JsonSpanSerializer(), stats, 100, 10, 50);
        long startNanos = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            exporter.accept(span("fail-" + i));
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        assertTrue(elapsedMillis < 500, "入队必须是非阻塞的，实际耗时=" + elapsedMillis);

        waitFor(() -> attempts.get() > 0);
        waitFor(() -> stats.exportFailures() >= 20);
        assertEquals(20, stats.exportFailures());
        exporter.close();
    }

    @Test
    @DisplayName("导出端变慢导致积压达到上限：超出的 span 被丢弃并计入 droppedBacklogFull")
    void backlogFullDropsWithCounter() throws Exception {
        TraceStats stats = new TraceStats();
        CountDownLatch unblock = new CountDownLatch(1);
        SpanExporter slow = batch -> unblock.await(5, TimeUnit.SECONDS);
        AsyncSpanExporter exporter = new AsyncSpanExporter(
                slow, new JsonSpanSerializer(), stats, 10, 5, 50);

        for (int i = 0; i < 50; i++) {
            exporter.accept(span("slow-" + i));
        }
        // 导出线程会立刻取走第一批（最多5条）阻塞住，剩余最多积压 10 条，其余丢弃
        waitFor(() -> stats.droppedBacklogFull() >= 35);
        assertTrue(exporter.backlogSize() <= 10);
        assertTrue(stats.droppedBacklogFull() >= 35,
                "应当丢弃至少 35 条，实际=" + stats.droppedBacklogFull());
        unblock.countDown();
        exporter.close();
    }

    @Test
    @DisplayName("序列化失败与导出失败分别计数，坏 span 不牵连同批好 span")
    void serializationFailuresAreSeparatelyCounted() {
        TraceStats stats = new TraceStats();
        AtomicInteger exportedBatches = new AtomicInteger();
        SpanExporter ok = batch -> exportedBatches.incrementAndGet();
        SpanSerializer flaky = new SpanSerializer() {
            private final JsonSpanSerializer delegate = new JsonSpanSerializer();

            @Override
            public String serialize(FinishedSpan s) throws Exception {
                if (s.name().startsWith("bad")) {
                    throw new IllegalStateException("serialization boom");
                }
                return delegate.serialize(s);
            }
        };
        AsyncSpanExporter exporter = new AsyncSpanExporter(ok, flaky, stats, 100, 50, 50);
        exporter.accept(span("good-1"));
        exporter.accept(span("bad-1"));
        exporter.accept(span("good-2"));
        waitFor(() -> exportedBatches.get() >= 1);
        waitFor(() -> stats.serializationFailures() >= 1);
        assertEquals(1, stats.serializationFailures());
        assertEquals(2, stats.exportedSpans());
        assertEquals(0, stats.exportFailures());
        exporter.close();
    }

    @Test
    @DisplayName("本地文件导出：span 最终以 JSON Lines 落盘")
    void localFileExporterWritesJsonLines(@TempDir Path dir) throws Exception {
        TraceStats stats = new TraceStats();
        Path file = dir.resolve("spans.jsonl");
        LocalFileSpanExporter fileExporter = new LocalFileSpanExporter(file, new JsonSpanSerializer());
        AsyncSpanExporter exporter = new AsyncSpanExporter(fileExporter, new JsonSpanSerializer(),
                stats, 100, 10, 50);
        for (int i = 0; i < 3; i++) {
            exporter.accept(span("file-" + i));
        }
        waitFor(() -> stats.exportedSpans() >= 3);
        exporter.close();
        List<String> lines = java.nio.file.Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("\"traceId\":\"t\""));
        assertTrue(lines.get(0).contains("\"name\":\"file-0\""));
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("等待导出处理超时");
    }
}
