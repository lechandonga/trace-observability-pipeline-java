package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 追踪装配。导出端初始化失败时降级为丢弃导出，不影响应用启动。
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(TraceProperties.class)
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    @Bean
    public TraceMetrics traceMetrics() {
        return new TraceMetrics();
    }

    @Bean
    public SpanBuffer spanBuffer(TraceProperties properties, TraceMetrics metrics) {
        return new SpanBuffer(properties, metrics);
    }

    @Bean
    public SpanExporter spanExporter(TraceProperties properties, TraceMetrics metrics) {
        try {
            return new LocalFileSpanExporter(properties.getExportFile());
        } catch (IOException e) {
            // 导出端初始化失败：降级为丢弃导出并计数，不阻塞应用启动
            log.warn("local file exporter unavailable, falling back to dropping exporter: {}", e.toString());
            return trace -> {
                throw new IllegalStateException("exporter disabled: " + e.getMessage());
            };
        }
    }

    @Bean(destroyMethod = "stop")
    public AsyncExportPipeline asyncExportPipeline(TraceProperties properties, SpanExporter exporter,
                                                   TraceMetrics metrics) {
        AsyncExportPipeline pipeline = new AsyncExportPipeline(properties, exporter, metrics);
        pipeline.start();
        return pipeline;
    }

    @Bean
    public Tracer tracer(TraceProperties properties, SpanBuffer buffer,
                         AsyncExportPipeline pipeline, TraceMetrics metrics) {
        return new Tracer(properties, buffer, pipeline, metrics);
    }

    @Bean
    public FilterRegistrationBean<TraceFilter> traceFilter(Tracer tracer, TraceMetrics metrics) {
        FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>(new TraceFilter(tracer, metrics));
        registration.setOrder(Integer.MIN_VALUE + 10);
        return registration;
    }

    /** 异步任务（@Async 及应用 TaskExecutor）上下文传递，Boot 自动装配会拾取该 Bean。 */
    @Bean
    public TaskDecorator traceTaskDecorator() {
        return new TraceTaskDecorator();
    }

    /** 定时任务调度器，同样挂上下文装饰器。 */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(TaskDecorator traceTaskDecorator) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("trace-scheduler-");
        scheduler.setTaskDecorator(traceTaskDecorator);
        return scheduler;
    }

    @Bean
    public TraceStatusController traceStatusController(TraceMetrics metrics, TraceProperties properties) {
        return new TraceStatusController(metrics, properties);
    }
}
