package com.github.lechandonga.tracepipe.tests.support;

import com.github.lechandonga.tracepipe.tracing.DefaultTracer;
import com.github.lechandonga.tracepipe.tracing.FinishedSpan;
import com.github.lechandonga.tracepipe.tracing.Sampler;
import com.github.lechandonga.tracepipe.tracing.TraceRegistry;
import com.github.lechandonga.tracepipe.tracing.TraceStats;
import com.github.lechandonga.tracepipe.tracing.TracingProperties;
import com.github.lechandonga.tracepipe.tracing.Tracer;
import com.github.lechandonga.tracepipe.tracing.TracerProvider;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** 不启动 Spring 即可构造一条追踪链路的测试辅助工厂。 */
public final class TestTracingFactory {

    public static final class Harness {
        public final Tracer tracer;
        public final TraceStats stats;
        public final TracingProperties properties;
        public final TraceRegistry registry;
        public CopyOnWriteArrayList<FinishedSpan> exported = new CopyOnWriteArrayList<>();

        Harness(Tracer tracer, TraceStats stats, TracingProperties properties, TraceRegistry registry) {
            this.tracer = tracer;
            this.stats = stats;
            this.properties = properties;
            this.registry = registry;
        }
    }

    private TestTracingFactory() {
    }

    public static Harness create(double sampleRate, int maxTraces, int maxSpansPerTrace,
                                 Consumer<FinishedSpan> sink) {
        TracingProperties props = new TracingProperties();
        props.setSampleRate(sampleRate);
        props.setMaxTraces(maxTraces);
        props.setMaxSpansPerTrace(maxSpansPerTrace);
        TraceStats stats = new TraceStats();
        TraceRegistry registry = new TraceRegistry(props, stats, sink);
        Tracer tracer = new DefaultTracer(new Sampler(sampleRate), registry, stats);
        TracerProvider.initialize(tracer);
        return new Harness(tracer, stats, props, registry);
    }

    public static Harness withInMemorySink(double sampleRate, int maxTraces, int maxSpansPerTrace) {
        CopyOnWriteArrayList<FinishedSpan> exported = new CopyOnWriteArrayList<>();
        Harness h = create(sampleRate, maxTraces, maxSpansPerTrace, exported::add);
        h.exported = exported;
        return h;
    }
}
