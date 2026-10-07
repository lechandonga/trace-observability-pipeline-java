package com.github.lechandonga.tracepipe.tracing.export;

import com.github.lechandonga.tracepipe.tracing.FinishedSpan;

import java.util.List;

/**
 * Span 导出端。实现需保证在独立线程中被调用，可慢、可失败，
 * 但不能把异常抛给业务线程。
 */
public interface SpanExporter {

    /** 导出一批 span；失败时抛异常，由异步导出器统一计数。 */
    void export(List<FinishedSpan> batch) throws Exception;
}
