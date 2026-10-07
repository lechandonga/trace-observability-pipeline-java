package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 只读运行时状态入口：请求量、失败量、丢弃量、积压与各项上限。
 */
@RestController
public class TraceStatusController {

    private final TraceMetrics metrics;
    private final TraceProperties properties;

    public TraceStatusController(TraceMetrics metrics, TraceProperties properties) {
        this.metrics = metrics;
        this.properties = properties;
    }

    @GetMapping("/trace/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>(metrics.snapshot());
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("samplingRate", properties.getSamplingRate());
        limits.put("maxCachedTraces", properties.getMaxCachedTraces());
        limits.put("maxSpansPerTrace", properties.getMaxSpansPerTrace());
        limits.put("exportQueueCapacity", properties.getExportQueueCapacity());
        limits.put("exportFile", properties.getExportFile());
        result.put("limits", limits);
        return result;
    }
}
