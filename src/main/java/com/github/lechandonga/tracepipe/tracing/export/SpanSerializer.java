package com.github.lechandonga.tracepipe.tracing.export;

import com.github.lechandonga.tracepipe.tracing.FinishedSpan;

/**
 * Span 序列化抽象。序列化失败与导出失败分别计数，便于区分两类追踪自身故障。
 */
public interface SpanSerializer {

    String serialize(FinishedSpan span) throws Exception;
}
