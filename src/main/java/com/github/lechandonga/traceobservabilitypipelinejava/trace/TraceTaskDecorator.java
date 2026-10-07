package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import org.springframework.core.task.TaskDecorator;

/**
 * 跨线程上下文传递：任务提交时打快照，执行时整体恢复，执行完还原线程原状，
 * 保证线程池复用时既不串到别的请求，也不残留上一次上下文。
 */
public class TraceTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        TraceContext.Snapshot snapshot = TraceContext.snapshot();
        return () -> {
            TraceContext.Snapshot previous = TraceContext.snapshot();
            TraceContext.restore(snapshot);
            try {
                runnable.run();
            } finally {
                TraceContext.restore(previous);
            }
        };
    }
}
