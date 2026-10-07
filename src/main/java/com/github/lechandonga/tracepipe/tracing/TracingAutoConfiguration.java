package com.github.lechandonga.tracepipe.tracing;

import com.github.lechandonga.tracepipe.tracing.concurrent.TraceTaskDecorator;
import com.github.lechandonga.tracepipe.tracing.concurrent.TracingTaskScheduler;
import com.github.lechandonga.tracepipe.tracing.export.AsyncSpanExporter;
import com.github.lechandonga.tracepipe.tracing.export.JsonSpanSerializer;
import com.github.lechandonga.tracepipe.tracing.export.LocalFileSpanExporter;
import com.github.lechandonga.tracepipe.tracing.export.SpanExporter;
import com.github.lechandonga.tracepipe.tracing.export.SpanSerializer;
import com.github.lechandonga.tracepipe.tracing.web.TraceStatusController;
import com.github.lechandonga.tracepipe.tracing.web.TraceWebFilter;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * 轻量链路追踪的 Spring 装配：
 * 采样 → 有界注册表 → 异步有界导出（本地文件）→ Web/异步/定时 接入。
 *
 * <p>基础设施 Bean 在 {@link BeanFactoryPostProcessor} 阶段创建并注册，
 * 保证业务 Bean 字段内联 {@link TracerProvider#tracer()} 时拿到的是真实实例。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TracingProperties.class)
public class TracingAutoConfiguration {

    @Bean
    public static BeanFactoryPostProcessor tracingInfrastructureInitializer() {
        return beanFactory -> {
            Environment environment = beanFactory.getBean(Environment.class);
            TracingProperties properties = Binder.get(environment)
                    .bindOrCreate("tracing", TracingProperties.class);
            TraceStats stats = new TraceStats();
            SpanSerializer serializer = new JsonSpanSerializer();
            SpanExporter exporter = new LocalFileSpanExporter(
                    Path.of(properties.getExportFile()), serializer);
            AsyncSpanExporter asyncExporter = new AsyncSpanExporter(exporter, serializer, stats,
                    properties.getExportQueueCapacity(),
                    properties.getExportBatchSize(),
                    properties.getExportPollTimeoutMillis());
            TraceRegistry registry = new TraceRegistry(properties, stats, asyncExporter);
            Tracer tracer = new DefaultTracer(new Sampler(properties.getSampleRate()), registry, stats);
            TracerProvider.initialize(tracer);

            beanFactory.registerSingleton("traceStats", stats);
            beanFactory.registerSingleton("spanSerializer", serializer);
            beanFactory.registerSingleton("spanExporter", exporter);
            beanFactory.registerSingleton("asyncSpanExporter", asyncExporter);
            beanFactory.registerSingleton("traceRegistry", registry);
            beanFactory.registerSingleton("tracer", tracer);
        };
    }

    @Bean
    public TraceWebFilter traceWebFilter(Tracer tracer, TraceStats stats) {
        return new TraceWebFilter(tracer, stats);
    }

    @Bean
    public TraceStatusController traceStatusController(TraceStats stats, AsyncSpanExporter exporter,
                                                       TraceRegistry registry, TracingProperties properties) {
        return new TraceStatusController(stats, exporter, registry, properties);
    }

    @Bean
    public TaskScheduler traceTaskScheduler(Tracer tracer) {
        TracingTaskScheduler scheduler = new TracingTaskScheduler(tracer);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 为 {@code @Async} 提供带上下文传播的执行器（替代默认 SimpleAsyncTaskExecutor）。
     */
    @Bean
    public AsyncConfigurer tracingAsyncConfigurer(Tracer tracer) {
        return new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
                executor.setCorePoolSize(2);
                executor.setMaxPoolSize(4);
                executor.setQueueCapacity(50);
                executor.setThreadNamePrefix("trace-async-");
                executor.setTaskDecorator(new TraceTaskDecorator(tracer));
                executor.initialize();
                return executor;
            }
        };
    }
}
