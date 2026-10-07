package com.github.lechandonga.tracepipe.tracing.concurrent;

import com.github.lechandonga.tracepipe.tracing.Tracer;
import org.springframework.core.task.TaskDecorator;

/**
 * 用于 {@code @Async} 线程池：提交任务时捕获当前链路上下文，
 * 在线程池工作线程执行前后恢复/清理，杜绝复用串链与残留。
 */
public final class TraceTaskDecorator implements TaskDecorator {

    private final Tracer tracer;

    public TraceTaskDecorator(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        return tracer.wrap(runnable);
    }
}
