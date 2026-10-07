package com.github.lechandonga.tracepipe.tracing;

/**
 * 未初始化时使用的空实现（例如脱离 Spring 容器的极早期阶段）。
 */
final class NoopTracer implements Tracer {

    static final NoopTracer INSTANCE = new NoopTracer();
    private final TraceStats stats = new TraceStats();

    private NoopTracer() {
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

    private ActiveSpan doStart(String name, TraceContext previous) {
        Span span = new Span(newId(), newId(), null, name, System.nanoTime(), false);
        return new ActiveSpan(span, previous, previous == null, null, null);
    }

    @Override
    public Runnable wrap(Runnable task) {
        return task;
    }

    @Override
    public ActiveSpan startScheduledSpan(String name) {
        return startSpan(name);
    }

    @Override
    public TraceStats stats() {
        return stats;
    }

    @Override
    public TraceRegistry registry() {
        throw new UnsupportedOperationException("tracing not initialized");
    }

    private static String newId() {
        return Long.toHexString(System.nanoTime()) + Long.toHexString(Thread.currentThread().getId());
    }
}
