package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异步导出管道：业务线程只做非阻塞 offer，队列满时按确定策略丢弃并计数；
 * 导出端慢、失败都只影响后台线程，绝不阻塞或搞挂业务。
 */
public class AsyncExportPipeline {

    private static final Logger log = LoggerFactory.getLogger(AsyncExportPipeline.class);

    private final BlockingQueue<Tracer.CompletedTrace> queue;
    private final SpanExporter exporter;
    private final TraceMetrics metrics;
    private final Thread worker;
    private volatile boolean running = true;

    public AsyncExportPipeline(TraceProperties properties, SpanExporter exporter, TraceMetrics metrics) {
        this.queue = new ArrayBlockingQueue<>(properties.getExportQueueCapacity());
        this.exporter = exporter;
        this.metrics = metrics;
        this.metrics.setBacklogSupplier(queue::size);
        this.worker = new Thread(this::drainLoop, "trace-export-worker");
        this.worker.setDaemon(true);
    }

    public void start() {
        worker.start();
    }

    /** 非阻塞提交；队列满则丢弃并计数，返回是否受理。 */
    public boolean submit(Tracer.CompletedTrace trace) {
        boolean accepted = queue.offer(trace);
        if (!accepted) {
            metrics.incTraceDropped();
        }
        return accepted;
    }

    public int backlog() {
        return queue.size();
    }

    private void drainLoop() {
        while (running || !queue.isEmpty()) {
            Tracer.CompletedTrace trace;
            try {
                trace = queue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (trace == null) {
                continue;
            }
            try {
                exporter.export(trace);
                metrics.incTraceExported();
            } catch (Throwable t) {
                // 导出失败仅计数，不反压业务
                metrics.incExportFailure();
                log.warn("trace export failed, traceId={}: {}", trace.traceId(), t.toString());
            }
        }
    }

    public void stop() {
        running = false;
        try {
            worker.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        exporter.close();
    }
}
