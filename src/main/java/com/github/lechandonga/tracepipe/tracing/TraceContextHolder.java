package com.github.lechandonga.tracepipe.tracing;

/**
 * 基于 ThreadLocal 的上下文载体。
 *
 * <p>注意：线程池复用时必须显式 {@link #clear()}，否则会把上一次请求的链路
 * 串到下一个任务上（或残留上下文）。所有包装器（{@code TraceTaskWrappers}）
 * 都在 finally 中执行 clear。
 */
public final class TraceContextHolder {

    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private TraceContextHolder() {
    }

    public static TraceContext get() {
        return CURRENT.get();
    }

    public static void set(TraceContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
