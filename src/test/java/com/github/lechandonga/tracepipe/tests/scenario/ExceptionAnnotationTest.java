package com.github.lechandonga.tracepipe.tests.scenario;

import com.github.lechandonga.tracepipe.tests.support.TestTracingFactory;
import com.github.lechandonga.tracepipe.tests.support.TestTracingFactory.Harness;
import com.github.lechandonga.tracepipe.tracing.ActiveSpan;
import com.github.lechandonga.tracepipe.tracing.FinishedSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景：异常标注 —— 业务异常原样抛出、类型不变，span 标记失败及异常类型。
 */
class ExceptionAnnotationTest {

    @Test
    @DisplayName("业务抛出的 IllegalStateException 原样上抛，根 span 记录 error 与异常类型")
    void businessExceptionPropagatesAndMarksSpan() {
        Harness h = TestTracingFactory.withInMemorySink(1.0, 10, 100);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            ActiveSpan root = h.tracer.startRootSpan("failing-op");
            try {
                throw new IllegalStateException("boom");
            } catch (RuntimeException e) {
                root.close(e);
                throw e;
            } finally {
                root.close();
            }
        });
        assertEquals("boom", thrown.getMessage());

        FinishedSpan root = h.exported.get(0);
        assertTrue(root.error());
        assertEquals(IllegalStateException.class.getName(), root.errorType());
        assertEquals("boom", root.errorMessage());
        assertEquals(0, h.stats.exportFailures());
    }

    @Test
    @DisplayName("子 span 异常时父子 span 都可标注失败，异常类型不被包装")
    void childExceptionKeepsType() {
        Harness h = TestTracingFactory.withInMemorySink(1.0, 10, 100);
        assertThrows(IllegalArgumentException.class, () -> {
            ActiveSpan root = h.tracer.startRootSpan("root");
            try {
                ActiveSpan child = h.tracer.startSpan("child");
                try {
                    throw new IllegalArgumentException("bad arg");
                } catch (RuntimeException e) {
                    child.close(e);
                    throw e;
                } finally {
                    child.close();
                }
            } catch (RuntimeException e) {
                root.close(e);
                throw e;
            } finally {
                root.close();
            }
        });
        FinishedSpan child = h.exported.get(0);
        FinishedSpan root = h.exported.get(1);
        assertTrue(child.error());
        assertEquals(IllegalArgumentException.class.getName(), child.errorType());
        // 子 span 与根 span 都看到了同一异常，都标注失败
        assertEquals(2, h.exported.stream().filter(FinishedSpan::error).count());
        assertTrue(root.error());
    }

    @Test
    @DisplayName("追踪自身出错（导出 sink 抛异常）不改变业务的正常返回")
    void tracingFailureDoesNotAffectBusiness() {
        Harness h = TestTracingFactory.create(1.0, 10, 100, span -> {
            throw new RuntimeException("tracing sink broken");
        });
        String result;
        ActiveSpan root = h.tracer.startRootSpan("root");
        try {
            result = "business-result";
        } finally {
            root.close();
        }
        assertEquals("business-result", result);
        assertTrue(h.stats.exportFailures() >= 1);
    }

}
