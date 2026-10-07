# 轻量请求链路追踪（Spring Boot）

本工程在 Spring Boot（Web）内置了一套**零外部依赖、无需外部服务和凭据**的轻量链路追踪：

- HTTP 请求自动生成完整调用链（根/子 span 的父子关系、每段耗时、成败、异常类型）；
- `@Async` 异步任务、`@Scheduled` 定时任务自动挂接/开链，线程池复用不串链、不残留；
- 采样决策整条链路一致，关键日志自动带 `traceId/spanId`；
- 链路数据异步导出到**本地 JSON Lines 文件**，导出端变慢/失败/积压时按确定策略丢弃并计数，
  业务线程永不阻塞，追踪自身故障不影响业务返回；
- 内存 trace 数、单 trace span 数、导出积压均有可配置上限；
- 只读运行时状态入口 `GET /tracing/status`。

## 架构与数据流

```
HTTP 请求 ── TraceWebFilter（根 span，异常/5xx 标注失败）
              │ ThreadLocal + MDC(traceId,spanId)
              ├─ 同步业务：tracer.startSpan("name")（try/finally 关闭）
              ├─ @Async：TraceTaskDecorator 捕获/恢复上下文（@Async 线程池）
              └─ @Scheduled：TracingTaskScheduler 每次触发新建独立 trace
                     │
              DefaultTracer ── Sampler（根节点决策一次，随上下文传播）
                     │
              TraceRegistry（有界：maxTraces / maxSpansPerTrace）
                     │ span 结束（非业务线程）
              AsyncSpanExporter（有界队列 + 单守护线程 + 批量）
                     │
              LocalFileSpanExporter → tracing.export-file（JSON Lines）
```

核心代码位于 `src/main/java/com/github/lechandonga/tracepipe/tracing/`：

| 类 | 职责 |
| --- | --- |
| `TraceContext` / `TraceContextHolder` | 不可变上下文（traceId、spanId、sampled）与 ThreadLocal |
| `Span` / `FinishedSpan` / `ActiveSpan` | 活动 span 与结束快照；`ActiveSpan` 负责 MDC、上下文恢复和导出 |
| `Sampler` | 固定比例采样（每 100 次决策为一周期），根节点决策一次 |
| `DefaultTracer` | 开 span、上下文传播包装（`wrap`）、定时入口（`startScheduledSpan`） |
| `TraceRegistry` | 活动 trace 有界注册表、单 trace span 数上限、延迟淘汰 |
| `TraceStats` | 全部运行时计数（业务失败与追踪自身失败分开） |
| `export/AsyncSpanExporter` | 非阻塞入队、有界积压、批量导出、丢弃计数 |
| `export/LocalFileSpanExporter` | 本地 JSON Lines 追加写（无外部服务） |
| `export/JsonSpanSerializer` | 零依赖 JSON 序列化 |
| `web/TraceWebFilter` | 自动根 span、异常透传、5xx 标注、`X-Trace-Id` 响应头 |
| `web/TraceStatusController` | `GET /tracing/status` 只读状态 |
| `concurrent/TraceTaskDecorator` / `TracingTaskScheduler` | 异步/定时接入 |

## 采样策略

- `tracing.sample-rate` ∈ `[0,1]`，默认 `1.0`。
- **每条 trace 只在根 span 做一次采样决策**，结果（含 traceId）随 `TraceContext`
  向子 span、`@Async` 任务传播；不采样的整条链路都不产生 span（业务照常执行，开销极小）。
- 采样器实现为确定性比例（每 100 次决策取前 `round(rate*100)` 次），不依赖随机数。

## 导出与降级策略

- span 在业务线程上仅生成快照并**非阻塞** `offer` 到有界队列（`ArrayBlockingQueue`），
  由单个守护线程批量（`tracing.export-batch-size`）写本地文件。
- **积压达到上限**（`tracing.export-queue-capacity`）：策略为**丢弃当前最新 span**，
  累加 `droppedBacklogFull`，不阻塞、不报错给业务。
- **导出端失败/变慢**：本批 span **不重试直接丢弃**，累加 `exportFailures`；
  导出慢导致队列满时走上面的积压丢弃。
- **序列化失败**：逐条剔除坏 span 并累加 `serializationFailures`，不牵连同批好 span。
- 停机时最多等待 5 秒尽量导出积压，仍未导出的计入丢弃。
- 两类失败严格区分：`requestsFailed` 是**业务失败**；
  `exportFailures/serializationFailures` 是**追踪自身失败**，业务无感知。

## 各项上限（均有默认值，杜绝无界增长）

| 配置 | 默认 | 含义 | 触发后行为 |
| --- | --- | --- | --- |
| `tracing.max-traces` | `1000` | 内存中活动 trace 条数上限 | 新 trace 整体不采样，计 `tracesRejectedByTraceLimit` |
| `tracing.max-spans-per-trace` | `200` | 单条 trace 最大 span 数 | 超限 span 截断（业务继续），计 `spansTruncatedBySpanLimit` |
| `tracing.export-queue-capacity` | `2000` | 导出积压 span 条数 | 丢弃最新 span，计 `droppedBacklogFull` |
| `tracing.trace-retention-millis` | `30000` | 已结束 trace 在内存保留时间 | 到期自动淘汰，等异步尾巴 span |
| `tracing.export-batch-size` | `100` | 导出批大小 | — |
| `tracing.export-poll-timeout-millis` | `200` | 导出线程拉取超时 | — |
| `tracing.export-file` | `trace-data/spans.jsonl` | 本地导出文件（JSON Lines） | 自动建目录，无外部服务/凭据 |

## 运行时状态（只读）

```bash
curl -s http://localhost:8080/tracing/status
```

返回字段包括：`requestsStarted`（请求量）、`requestsFailed`（业务失败量）、
`droppedBacklogFull`（积压/停机丢弃量）、`exportFailures`、`serializationFailures`、
`spansTruncatedBySpanLimit`、`tracesRejectedByTraceLimit`、
`exportBacklog`/`exportQueueCapacity`（积压情况）、`activeTraces`、
以及当前各项上限与采样率配置。该接口本身不被追踪。

## 日志关联

控制台 pattern 已加入 MDC 变量，关键日志可直接按 traceId 检索：

```
... [thread] [traceId=514c59...,spanId=df1d96...] c.g.tracepipe.demo.OrderService - creating order o1
```

## 本地复现与验证

### 1. 启动与演示接口

```bash
mvn spring-boot:run

curl -s -X POST http://localhost:8080/demo/orders/order-1        # 同步链路（4 个 span）
curl -s -X POST http://localhost:8080/demo/orders/order-1/async  # 异步任务挂同一 trace
curl -s -X POST http://localhost:8080/demo/orders/bad/fail       # 业务异常 → 500，链路标失败
curl -s http://localhost:8080/tracing/status                    # 运行时状态

# 查看导出的链路（每行一个 span，可按 traceId 还原父子树和耗时）
cat trace-data/spans.jsonl | grep <X-Trace-Id 响应头里的值>
```

span JSON 字段：`traceId, spanId, parentSpanId, name, startEpochNanos, endEpochNanos,
durationMicros, sampled, error, errorType, errorMessage`。
用 `parentSpanId` 自根（`null`）向下即可还原调用树；`durationMicros` 为每段耗时（微秒）。

### 2. 跑测试（日志可见场景覆盖与每条用例结果）

```bash
mvn test
```

测试通过 JUnit 扩展在控制台打印 `SCENARIO` 日志，形如
`>>> 场景[ExportDegradationTest] 用例开始: ...` / `<<< ... => 通过(PASS)`。
覆盖场景：

| 测试类 | 覆盖场景 |
| --- | --- |
| `ContextPropagationTest` | 并发/线程池复用上下文传递、异步挂接父子链、无串链无残留、不采样传播 |
| `SamplingConsistencyTest` | 同一 trace 采样结果一致、采样率边界（0/1）、非法配置拒绝 |
| `LimitTruncationTest` | 活动 trace 上限拒绝、单 trace span 上限截断、截断不影响业务 |
| `ExportDegradationTest` | 导出端持续失败、导出变慢致积压满丢弃、序列化失败与导出失败分别计数、本地落盘 |
| `ExceptionAnnotationTest` | 业务异常原样抛出且类型不变、span 标 error/异常类型、追踪自身故障不影响业务结果 |
| `WebEndToEndTest` | HTTP 端到端：200/500、`X-Trace-Id`、异步同链路、状态接口、文件内容 |

### 3. 验证降级（手工压测思路）

- 将 `tracing.export-queue-capacity` 调小（如 10），让导出端阻塞或失败，
  高频打接口后观察 `/tracing/status` 中 `droppedBacklogFull/exportFailures` 增长，
  而接口 RT 和成功率不受影响。
- 将 `tracing.max-traces`/`max-spans-per-trace` 调小，观察
  `tracesRejectedByTraceLimit`/`spansTruncatedBySpanLimit` 计数且内存不增长。
