package com.github.lechandonga.tracepipe.tracing;

/**
 * 链路追踪入口。
 */
public interface Tracer {

    /** 开始一个 span：有当前上下文则作为子 span（继承采样结果），否则开新 trace。 */
    ActiveSpan startSpan(String name);

    /** 无论当前线程是否有残留上下文，都强制开始一条新 trace（主要用于测试/定时入口）。 */
    ActiveSpan startRootSpan(String name);

    /** 在当前线程之外执行任务时包装，把当前链路上下文传播过去。 */
    Runnable wrap(Runnable task);

    /** 没有父上下文的定时任务使用：开始一条独立 trace。 */
    ActiveSpan startScheduledSpan(String name);

    TraceStats stats();

    TraceRegistry registry();
}
