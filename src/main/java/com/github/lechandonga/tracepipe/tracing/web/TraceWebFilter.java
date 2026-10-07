package com.github.lechandonga.tracepipe.tracing.web;

import com.github.lechandonga.tracepipe.tracing.ActiveSpan;
import com.github.lechandonga.tracepipe.tracing.TraceStats;
import com.github.lechandonga.tracepipe.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每个 HTTP 请求自动开启根 span，异常时标注失败并原样重新抛出，
 * 最终在响应头回写 traceId 方便关联查询。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class TraceWebFilter extends OncePerRequestFilter {

    private final Tracer tracer;
    private final TraceStats stats;

    public TraceWebFilter(Tracer tracer, TraceStats stats) {
        this.tracer = tracer;
        this.stats = stats;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.equals("/tracing/status");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        stats.incRequestsStarted();
        String name = request.getMethod() + " " + request.getRequestURI();
        boolean failed = false;
        ActiveSpan root = tracer.startSpan(name);
        try {
            response.setHeader("X-Trace-Id", root.span().traceId());
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 500) {
                failed = true;
                root.close(new TraceStatusException("HTTP " + response.getStatus()));
            }
        } catch (Throwable businessError) {
            failed = true;
            root.close(businessError);
            // 异常类型不变、追踪不吞异常：直接往上抛
            throw businessError;
        } finally {
            // close 幂等：成功路径普通关闭；错误路径已带异常关闭
            root.close();
            if (failed) {
                stats.incRequestsFailed();
            }
        }
    }
}
