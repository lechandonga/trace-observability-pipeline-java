# 轻量请求链路追踪（trace-observability-pipeline-java）

自包含的轻量链路追踪：不依赖任何外部 APM 服务或真实凭据，链路数据异步导出到本地文件（JSON Lines）。

## 核心能力

- **自动串链**：`TraceFilter` 为每个 HTTP 请求开启根跨度；`@Async` 异步任务与 `@Scheduled` 定时任务通过 `TraceTaskDecorator` 自动挂到发起它们的链路上。
- **线程安全**：上下文基于 ThreadLocal 栈，跨线程以不可变快照整体替换；请求结束强制 `TraceContext.clear()`，线程池复用不会串链也不会残留。
- **采样一致**：采样决策只由 `traceId` 哈希决定，同一条链路内所有跨度决策一致。
- **链路还原**：每个跨度记录 `traceId / spanId / parentSpanId / 起止时间 / 耗时 / OK|ERROR / 异常类型与消息`。
- **日志串联**：跨度开启时写入 MDC（`traceId`、`spanId`），控制台日志 pattern 已带上链路标识，可直接 `grep traceId=xxx`。
- **异常语义**：业务异常只标注后原样上抛（同类型同实例）；追踪自身异常被捕获并计入 `tracingInternalErrors` / `exportFailures`，两类失败分开计数。

## 配置项（`application.properties`，前缀 `trace.`）

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| `trace.sampling-rate` | `1.0` | 采样率 0.0~1.0；按 traceId 哈希判定，链路内一致 |
| `trace.max-cached-traces` | `1024` | 内存中未完成链路缓存上限，超出按最久未访问淘汰，计 `tracesEvicted` |
| `trace.max-spans-per-trace` | `256` | 单条链路跨度上限，超出截断，计 `spansTruncated` |
| `trace.export-queue-capacity` | `4096` | 导出队列积压上限，满后新链路直接丢弃，计 `tracesDropped`，业务线程零阻塞 |
| `trace.export-file` | `./trace-export.jsonl` | 本地导出文件（JSON Lines，每行一条完整链路） |

## 导出与降级策略

- 业务线程只做**非阻塞 offer** 到有界队列；后台单守护线程消费并写本地文件。
- 导出端变慢 → 队列积压到上限后**丢弃新链路**并计 `tracesDropped`，绝不阻塞/拖垮业务。
- 导出端抛异常 → 仅计 `exportFailures` 并打 warn 日志，不反压业务。
- 导出端初始化失败（如文件不可写）→ 降级为丢弃导出，应用照常启动。
- 所有缓存与队列均有上限，长跑不会无界增长。

## 运行时状态（只读）

```
GET /trace/status
```

返回 `requestsTotal / requestsFailed / tracesExported / tracesDropped / tracesEvicted / spansTruncated / exportFailures / exportBacklog / tracingInternalErrors` 及各项上限配置。

## 本地复现验证

```bash
# 跑全部测试（日志中 [SCENARIO]/[PASS] 可看到每个场景、用例与结果）
mvn test

# 启动应用并观察
mvn spring-boot:run
curl http://localhost:8080/trace/status   # 查看请求量/丢弃量/积压
cat trace-export.jsonl                     # 查看导出的链路（含父子关系、耗时、成败）
```

## 测试覆盖

| 测试类 | 场景 |
| --- | --- |
| `TraceContextConcurrencyTest` | 并发上下文传递：异步任务挂载发起链路、线程复用隔离、无残留 |
| `ExportPipelineTest` | 导出故障不影响业务、积压上限丢弃计数、正常导出计数 |
| `SpanBufferLimitTest` | 单链路跨度截断、缓存链路淘汰、导出队列有界、采样一致性 |
| `TracerExceptionTest` | 业务异常原样上抛并标记失败、追踪自身失败分开计数 |
| `TraceFilterWebTest` | Web 集成：请求自动入链、状态端点、请求后上下文清理 |
