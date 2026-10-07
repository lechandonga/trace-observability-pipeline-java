package com.github.lechandonga.tracepipe.tracing;

import org.slf4j.MDC;

import java.util.function.Consumer;

/**
 * 活动 Span 的作用域：进入时把 span 放入 ThreadLocal 与 MDC，关闭时恢复/清理。
 *
 * <p>无论采样与否、是否被截断，关闭时都会清掉当前线程的 MDC 与上下文，
 * 保证线程池复用不残留。非采样 span 是轻量占位对象，不注册、不导出。
 */
public final class ActiveSpan implements AutoCloseable {

    static final String MDC_TRACE_ID = "traceId";
    static final String MDC_SPAN_ID = "spanId";

    static String mdcTraceId() {
        return MDC.get(MDC_TRACE_ID);
    }

    static String mdcSpanId() {
        return MDC.get(MDC_SPAN_ID);
    }

    private final Span span;
    private final TraceContext previous;
    private final boolean root;
    private final Consumer<FinishedSpan> sink;
    private final Runnable onSinkFailure;
    private final String previousMdcTrace;
    private final String previousMdcSpan;
    private boolean closed;

    ActiveSpan(Span span, TraceContext previous, boolean root,
               Consumer<FinishedSpan> sink, Runnable onSinkFailure) {
        this.span = span;
        this.previous = previous;
        this.root = root;
        this.sink = sink;
        this.onSinkFailure = onSinkFailure;
        this.previousMdcTrace = MDC.get(MDC_TRACE_ID);
        this.previousMdcSpan = MDC.get(MDC_SPAN_ID);
        if (span.sampled()) {
            MDC.put(MDC_TRACE_ID, span.traceId());
            MDC.put(MDC_SPAN_ID, span.spanId());
        }
        TraceContextHolder.set(new TraceContext(span.traceId(), span.spanId(), span.sampled()));
    }

    public Span span() {
        return span;
    }

    /** 业务正常结束。 */
    @Override
    public void close() {
        close(null);
    }

    /**
     * 业务异常结束：在 span 上标注失败。异常由调用方继续抛出，
     * 本方法不吞异常、不改变异常类型。幂等。
     */
    public void close(Throwable error) {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (span.sampled()) {
                if (error != null) {
                    span.markError(error);
                }
                span.finish();
                FinishedSpan snapshot = span.toSnapshot();
                if (sink != null) {
                    try {
                        sink.accept(snapshot);
                    } catch (RuntimeException | Error sinkError) {
                        if (onSinkFailure != null) {
                            onSinkFailure.run();
                        }
                        throw sinkError;
                    }
                }
            }
        } catch (RuntimeException | Error tracingError) {
            // 追踪自身出错绝不能影响业务：吞掉（计数由 sink 内部完成）。
            org.slf4j.LoggerFactory.getLogger(ActiveSpan.class)
                    .debug("tracing finish error swallowed", tracingError);
        } finally {
            restoreContext();
        }
    }

    /** 兼容旧调用：显式标注错误后关闭。 */
    public void closeWithError(Throwable error) {
        close(error);
    }

    private void restoreContext() {
        if (span.sampled()) {
            if (previousMdcTrace != null) {
                MDC.put(MDC_TRACE_ID, previousMdcTrace);
            } else {
                MDC.remove(MDC_TRACE_ID);
            }
            if (previousMdcSpan != null) {
                MDC.put(MDC_SPAN_ID, previousMdcSpan);
            } else {
                MDC.remove(MDC_SPAN_ID);
            }
        }
        if (previous != null) {
            TraceContextHolder.set(previous);
        } else {
            TraceContextHolder.clear();
        }
    }
}
