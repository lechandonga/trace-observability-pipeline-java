package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 链路追踪配置。所有上限均有默认值，保证长跑不会无界增长。
 */
@ConfigurationProperties(prefix = "trace")
public class TraceProperties {

    /** 采样率 0.0~1.0；同一条链路内采样决策一致（由 traceId 哈希决定）。 */
    private double samplingRate = 1.0;

    /** 内存中缓存的未完成链路最大条数，超出按最久未访问淘汰。 */
    private int maxCachedTraces = 1024;

    /** 单条链路允许缓存的最大跨度数，超出部分截断并计数。 */
    private int maxSpansPerTrace = 256;

    /** 导出队列积压上限，满了按策略丢弃新链路并计数，绝不阻塞业务线程。 */
    private int exportQueueCapacity = 4096;

    /** 本地导出文件路径（JSON Lines）。 */
    private String exportFile = "./trace-export.jsonl";

    public double getSamplingRate() { return samplingRate; }
    public void setSamplingRate(double samplingRate) { this.samplingRate = samplingRate; }
    public int getMaxCachedTraces() { return maxCachedTraces; }
    public void setMaxCachedTraces(int maxCachedTraces) { this.maxCachedTraces = maxCachedTraces; }
    public int getMaxSpansPerTrace() { return maxSpansPerTrace; }
    public void setMaxSpansPerTrace(int maxSpansPerTrace) { this.maxSpansPerTrace = maxSpansPerTrace; }
    public int getExportQueueCapacity() { return exportQueueCapacity; }
    public void setExportQueueCapacity(int exportQueueCapacity) { this.exportQueueCapacity = exportQueueCapacity; }
    public String getExportFile() { return exportFile; }
    public void setExportFile(String exportFile) { this.exportFile = exportFile; }
}
