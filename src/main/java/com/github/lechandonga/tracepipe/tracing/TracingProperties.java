package com.github.lechandonga.tracepipe.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 追踪相关配置，前缀 {@code tracing}。所有上限都有默认值，防止无界增长。
 */
@ConfigurationProperties(prefix = "tracing")
public class TracingProperties {

    /** 采样率，范围 [0,1]；每条 trace 根节点决策一次，全链路一致。 */
    private double sampleRate = 1.0;

    /** 内存中最多保留多少条活动 trace，超过后新 trace 整体不采样。 */
    private int maxTraces = 1000;

    /** 单条 trace 允许的最大 span 数，超过的 span 被截断。 */
    private int maxSpansPerTrace = 200;

    /** 已结束 trace 在内存中的保留毫秒数（等异步尾巴 span）。 */
    private long traceRetentionMillis = 30_000;

    /** 导出积压队列容量（span 条数），满后丢弃最新 span。 */
    private int exportQueueCapacity = 2000;

    /** 导出批大小。 */
    private int exportBatchSize = 100;

    /** 导出线程拉取队列超时（毫秒）。 */
    private long exportPollTimeoutMillis = 200;

    /** 本地导出文件路径（JSON Lines），不需要外部服务或凭据。 */
    private String exportFile = "trace-data/spans.jsonl";

    public double getSampleRate() { return sampleRate; }
    public void setSampleRate(double sampleRate) { this.sampleRate = sampleRate; }
    public int getMaxTraces() { return maxTraces; }
    public void setMaxTraces(int maxTraces) { this.maxTraces = maxTraces; }
    public int getMaxSpansPerTrace() { return maxSpansPerTrace; }
    public void setMaxSpansPerTrace(int maxSpansPerTrace) { this.maxSpansPerTrace = maxSpansPerTrace; }
    public long getTraceRetentionMillis() { return traceRetentionMillis; }
    public void setTraceRetentionMillis(long traceRetentionMillis) { this.traceRetentionMillis = traceRetentionMillis; }
    public int getExportQueueCapacity() { return exportQueueCapacity; }
    public void setExportQueueCapacity(int exportQueueCapacity) { this.exportQueueCapacity = exportQueueCapacity; }
    public int getExportBatchSize() { return exportBatchSize; }
    public void setExportBatchSize(int exportBatchSize) { this.exportBatchSize = exportBatchSize; }
    public long getExportPollTimeoutMillis() { return exportPollTimeoutMillis; }
    public void setExportPollTimeoutMillis(long exportPollTimeoutMillis) { this.exportPollTimeoutMillis = exportPollTimeoutMillis; }
    public String getExportFile() { return exportFile; }
    public void setExportFile(String exportFile) { this.exportFile = exportFile; }
}
