package com.github.lechandonga.tracepipe.tests.scenario;

import com.github.lechandonga.tracepipe.tests.support.TestTracingFactory;
import com.github.lechandonga.tracepipe.tests.support.TestTracingFactory.Harness;
import com.github.lechandonga.tracepipe.tracing.ActiveSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 场景：各类上限截断 —— 活动 trace 上限、单 trace span 上限。
 */
class LimitTruncationTest {

    @Test
    @DisplayName("活动 trace 数达到上限后，新 trace 降级为不采样并计数")
    void maxTracesLimitRejectsNewTraces() {
        // maxTraces=2；前两条 trace 的根 span 不关闭，占住容量
        Harness h = TestTracingFactory.withInMemorySink(1.0, 2, 1000);
        List<ActiveSpan> open = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            open.add(h.tracer.startRootSpan("held-root-" + i));
        }
        try (ActiveSpan rejected = h.tracer.startRootSpan("rejected-root")) {
            assertFalse(rejected.span().sampled());
        }
        assertEquals(1, h.stats.tracesRejected());
        open.forEach(ActiveSpan::close);
    }

    @Test
    @DisplayName("单条 trace 的 span 超上限后继续截断，业务不受影响并计数")
    void maxSpansPerTraceTruncates() {
        Harness h = TestTracingFactory.withInMemorySink(1.0, 10, 5);
        try (ActiveSpan root = h.tracer.startRootSpan("root")) {
            // 上限 5：root 是第 1 个，再建 4 个采样子 span，第 5 个子 span 起截断
            List<ActiveSpan> spans = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                spans.add(h.tracer.startSpan("child-ok-" + i));
            }
            try (ActiveSpan truncated = h.tracer.startSpan("child-truncated")) {
                assertFalse(truncated.span().sampled());
            }
            spans.forEach(ActiveSpan::close);
        }
        assertEquals(1, h.stats.spansTruncated());
        // root + 4 个子 span 导出
        assertEquals(5, h.exported.size());
    }

    @Test
    @DisplayName("被截断的业务代码仍正常返回结果")
    void truncationDoesNotBreakBusiness() {
        Harness h = TestTracingFactory.withInMemorySink(1.0, 10, 1);
        String businessResult;
        try (ActiveSpan root = h.tracer.startRootSpan("root")) {
            try (ActiveSpan child = h.tracer.startSpan("child")) {
                businessResult = "business-ok";
            }
        }
        assertEquals("business-ok", businessResult);
    }
}
