package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求入口：自动开启根跨度，标注成败，结束时彻底清理上下文防止线程复用残留。
 */
public class TraceFilter extends OncePerRequestFilter {

    private final Tracer tracer;
    private final TraceMetrics metrics;

    public TraceFilter(Tracer tracer, TraceMetrics metrics) {
        this.tracer = tracer;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        metrics.incRequests();
        try (TraceScope scope = tracer.startSpan("HTTP " + request.getMethod() + " " + request.getRequestURI())) {
            try {
                filterChain.doFilter(request, response);
                if (response.getStatus() >= 500) {
                    metrics.incRequestFailed();
                    scope.recordError(new HttpStatusError(response.getStatus()));
                }
            } catch (Throwable t) {
                // 标注后原样继续抛，类型不变
                metrics.incRequestFailed();
                scope.recordError(t);
                throw t;
            }
        } finally {
            TraceContext.clear();
        }
    }

    /** 仅用于标注 HTTP 5xx 的内部错误类型，不参与业务流转。 */
    static final class HttpStatusError extends RuntimeException {
        HttpStatusError(int status) {
            super("HTTP status " + status);
        }
    }
}
