package com.github.lechandonga.tracepipe.demo;

import com.github.lechandonga.tracepipe.tracing.ActiveSpan;
import com.github.lechandonga.tracepipe.tracing.Tracer;
import com.github.lechandonga.tracepipe.tracing.TracerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 定时任务：没有上游请求，TracingTaskScheduler 会为每次触发新建一条 trace。
 */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);
    private final Tracer tracer = TracerProvider.tracer();
    private final AtomicLong runs = new AtomicLong();

    @Scheduled(fixedDelay = 60_000)
    public void heartbeat() {
        try (ActiveSpan span = tracer.startSpan("scheduled.heartbeat")) {
            long n = runs.incrementAndGet();
            log.info("scheduled heartbeat run #{}", n);
        }
    }

    public long runCount() {
        return runs.get();
    }
}
