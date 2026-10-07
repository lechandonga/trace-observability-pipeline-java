package com.github.lechandonga.tracepipe.tracing.concurrent;

import com.github.lechandonga.tracepipe.tracing.ActiveSpan;
import com.github.lechandonga.tracepipe.tracing.Tracer;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务调度器包装：{@code @Scheduled} 没有上游请求上下文，
 * 每次触发都开启一条独立的 trace；异常照常向上传播给 Spring 调度器，
 * 同时在链路上标注失败。执行结束必定清理线程上下文（线程池会复用）。
 */
public final class TracingTaskScheduler extends ThreadPoolTaskScheduler {

    private final Tracer tracer;

    public TracingTaskScheduler(Tracer tracer) {
        this.tracer = tracer;
        setThreadNamePrefix("trace-scheduled-");
        setPoolSize(2);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        return super.schedule(wrapScheduled(task), startTime);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Date startTime) {
        return super.schedule(wrapScheduled(task), startTime);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long period) {
        return super.scheduleAtFixedRate(wrapScheduled(task), period);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) {
        return super.scheduleWithFixedDelay(wrapScheduled(task), delay);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        return super.schedule(wrapScheduled(task), trigger);
    }

    private Runnable wrapScheduled(Runnable task) {
        return () -> {
            ActiveSpan span = tracer.startScheduledSpan("scheduled");
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                span.close(e);
                throw e;
            } finally {
                // close 幂等；错误路径已标注失败，这里普通关闭只做上下文恢复
                span.close();
            }
        };
    }
}
