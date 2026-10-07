package com.github.lechandonga.traceobservabilitypipelinejava.trace;

/**
 * 链路导出端。实现方抛出异常只会被计数，不会影响业务。
 */
public interface SpanExporter {

    void export(Tracer.CompletedTrace trace) throws Exception;

    default void close() {}
}
