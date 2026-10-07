package com.github.lechandonga.tracepipe.tracing.export;

import com.github.lechandonga.tracepipe.tracing.FinishedSpan;
import com.github.lechandonga.tracepipe.tracing.TraceStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 异步、有界、可降级的 span 导出入口。
 *
 * <ul>
 *   <li><b>不阻塞业务线程</b>：{@link #offer} 为非阻塞入队；</li>
 *   <li><b>积压上限确定</b>：内部为有界队列，容量由 {@code exportQueueCapacity} 配置；
 *       队列满时执行“丢弃最新 span”策略并累加 {@code droppedBacklogFull}；</li>
 *   <li><b>导出端变慢/失败</b>：由独立守护线程批处理，失败的 span 不再重试，
 *       按 {@code exportFailures}（导出端失败）或 {@code serializationFailures}
 *       （序列化失败）分别计数；</li>
 *   <li><b>可观测</b>：{@link #backlogSize()} 暴露当前积压。</li>
 * </ul>
 */
public final class AsyncSpanExporter implements Consumer<FinishedSpan>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncSpanExporter.class);

    private final BlockingQueue<FinishedSpan> queue;
    private final SpanExporter exporter;
    private final SpanSerializer serializer;
    private final TraceStats stats;
    private final int batchSize;
    private final long pollTimeoutMillis;
    private final Thread worker;
    private volatile boolean running = true;

    public AsyncSpanExporter(SpanExporter exporter, SpanSerializer serializer, TraceStats stats,
                             int queueCapacity, int batchSize, long pollTimeoutMillis) {
        this.exporter = exporter;
        this.serializer = serializer;
        this.stats = stats;
        this.batchSize = batchSize;
        this.pollTimeoutMillis = pollTimeoutMillis;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.worker = new Thread(this::runLoop, "trace-span-exporter");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** 业务线程调用：非阻塞。入队失败即按策略丢弃并计数，绝不抛出。 */
    @Override
    public void accept(FinishedSpan span) {
        if (!running || span == null) {
            if (span != null) {
                stats.incDroppedBacklogFull();
            }
            return;
        }
        if (!queue.offer(span)) {
            // 确定策略：导出积压达上限，丢弃当前最新 span
            stats.incDroppedBacklogFull();
        }
    }

    public int backlogSize() {
        return queue.size();
    }

    public int queueCapacity() {
        return queue.size() + queue.remainingCapacity();
    }

    private void runLoop() {
        while (running || !queue.isEmpty()) {
            try {
                FinishedSpan first = queue.poll(pollTimeoutMillis, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                List<FinishedSpan> batch = new ArrayList<>(batchSize);
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                processBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) {
                    return;
                }
            } catch (RuntimeException e) {
                // 工作线程绝不能因意外异常死掉
                log.debug("trace exporter loop error swallowed", e);
            }
        }
    }

    private void processBatch(List<FinishedSpan> batch) {
        // 1) 逐条序列化：序列化失败的 span 单独计数并剔除，不牵连同批其它 span
        List<FinishedSpan> serializable = new ArrayList<>(batch.size());
        for (FinishedSpan span : batch) {
            try {
                serializer.serialize(span);
                serializable.add(span);
            } catch (Exception e) {
                stats.incSerializationFailures();
                log.debug("span serialization failed, dropped: traceId={}", span.traceId(), e);
            }
        }
        if (serializable.isEmpty()) {
            return;
        }
        // 2) 导出：导出端失败，本批按“不重试直接丢弃”处理并计数
        try {
            exporter.export(serializable);
            for (int i = 0; i < serializable.size(); i++) {
                stats.incExportedSpans();
            }
        } catch (Exception e) {
            for (int i = 0; i < serializable.size(); i++) {
                stats.incExportFailures();
            }
            log.warn("trace export failed, {} span(s) dropped by policy", serializable.size(), e);
        }
    }

    /** 优雅停机：停止接收新数据，尽量在超时内把积压导出完。 */
    @Override
    public void close() {
        running = false;
        worker.interrupt();
        try {
            worker.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 仍有积压则计入丢弃
        int remaining = queue.size();
        for (int i = 0; i < remaining; i++) {
            stats.incDroppedBacklogFull();
        }
    }
}
