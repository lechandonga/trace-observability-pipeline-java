package com.github.lechandonga.tracepipe.tracing.web;

import com.github.lechandonga.tracepipe.tracing.TraceStats;
import com.github.lechandonga.tracepipe.tracing.TracingProperties;
import com.github.lechandonga.tracepipe.tracing.export.AsyncSpanExporter;
import com.github.lechandonga.tracepipe.tracing.TraceRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 只读运行时状态入口：请求量、失败量、丢弃量、积压情况与各项上限。
 */
@RestController
public final class TraceStatusController {

    private final TraceStats stats;
    private final AsyncSpanExporter exporter;
    private final TraceRegistry registry;
    private final TracingProperties properties;

    public TraceStatusController(TraceStats stats, AsyncSpanExporter exporter,
                                 TraceRegistry registry, TracingProperties properties) {
        this.stats = stats;
        this.exporter = exporter;
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping("/tracing/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>(stats.snapshot());
        body.put("exportBacklog", exporter.backlogSize());
        body.put("exportQueueCapacity", exporter.queueCapacity());
        body.put("activeTraces", registry.activeTraceCount());
        body.put("maxTraces", properties.getMaxTraces());
        body.put("maxSpansPerTrace", properties.getMaxSpansPerTrace());
        body.put("sampleRate", properties.getSampleRate());
        body.put("exportFile", properties.getExportFile());
        return body;
    }
}
