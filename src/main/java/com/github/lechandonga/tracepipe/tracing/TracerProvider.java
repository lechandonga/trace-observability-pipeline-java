package com.github.lechandonga.tracepipe.tracing;

/**
 * 静态访问点：让业务代码无需注入即可拿到 {@link Tracer}，同时屏蔽追踪自身的故障。
 * 由 {@code TracingAutoConfiguration} 在启动时注入真实实例。
 */
public final class TracerProvider {

    private static volatile Tracer tracer = NoopTracer.INSTANCE;

    private TracerProvider() {
    }

    public static void initialize(Tracer tracerInstance) {
        tracer = tracerInstance;
    }

    public static Tracer tracer() {
        return tracer;
    }
}
