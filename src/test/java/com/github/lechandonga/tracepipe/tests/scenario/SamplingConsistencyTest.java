package com.github.lechandonga.tracepipe.tests.scenario;

import com.github.lechandonga.tracepipe.tests.support.TestTracingFactory;
import com.github.lechandonga.tracepipe.tests.support.TestTracingFactory.Harness;
import com.github.lechandonga.tracepipe.tracing.ActiveSpan;
import com.github.lechandonga.tracepipe.tracing.Sampler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景：同一条链路的采样结果必须保持一致；采样率比例符合配置。
 */
class SamplingConsistencyTest {

    @Test
    @DisplayName("50% 采样率下，同一条 trace 的所有 span 采样结果相同")
    void allSpansInTraceShareDecision() {
        Harness h = TestTracingFactory.withInMemorySink(0.5, 10000, 100);
        int sampledTraces = 0;
        int unsampledTraces = 0;
        for (int trace = 0; trace < 200; trace++) {
            boolean rootSampled;
            ActiveSpan root = h.tracer.startRootSpan("root");
            try {
                rootSampled = root.span().sampled();
                try (ActiveSpan child1 = h.tracer.startSpan("child-1")) {
                    assertEquals(rootSampled, child1.span().sampled());
                    try (ActiveSpan child2 = h.tracer.startSpan("child-2")) {
                        assertEquals(rootSampled, child2.span().sampled());
                    }
                }
            } finally {
                root.close();
            }
            if (rootSampled) {
                sampledTraces++;
            } else {
                unsampledTraces++;
            }
        }
        assertTrue(sampledTraces > 30 && sampledTraces < 170,
                "采样比例应在 50% 附近，实际采样 trace 数=" + sampledTraces);
        assertTrue(unsampledTraces > 0);
    }

    @Test
    @DisplayName("采样率 0 时全不采样，1 时全部采样")
    void boundaryRates() {
        Sampler never = new Sampler(0.0);
        Sampler always = new Sampler(1.0);
        for (int i = 0; i < 50; i++) {
            assertFalse(never.sample());
            assertTrue(always.sample());
        }
    }

    @Test
    @DisplayName("非法采样率被拒绝")
    void invalidRateRejected() {
        try {
            new Sampler(1.5);
            throw new AssertionError("应当拒绝非法采样率");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
