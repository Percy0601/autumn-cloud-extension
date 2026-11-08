# 请求头透传（`autumn-cloud-extension-propagation`）

> 目标：让 `X-Traffic`、`X-Tenant-Id`、`X-Request-Id` 这类**业务上下文**在
> `网关 → 服务 A → 服务 B` 的级联调用中不丢；同时**不碰**链路追踪中间件掌管的那部分 header。

## 1. 为什么需要它

Spring 的声明式 HTTP 接口（`@HttpExchange` + `HttpServiceProxyFactory`）**不会**继承入站请求的 header：

- `HttpServiceMethod#invoke()` 只做两件事：把注解上的静态 headers 放进 `HttpRequestValues`，再按方法参数跑一遍 `HttpServiceArgumentResolver`（`RequestHeader` / `PathVariable` / `RequestParam` …）；整条链路不读 `RequestContextHolder`，也没有"当前请求"的概念。
- 客户端代理是单例 Bean，与某一次具体请求无关。
- 官方文档对 `@RequestHeader` 的口径是「**Add** a request header」——只往本次请求加，不负责从上下文取。

所以级联链路里只有第一跳（网关自己转发的请求）天然带着 `X-Traffic`；一旦服务内部换成 `@HttpExchange` 调下一跳，标签就断了，下游会按"无标签"回落 `default` 主干。

三个容易被误判为"已经透传"的假象：

| 现象 | 实际由谁负责 |
|---|---|
| `traceparent` / `b3` 能在下游看到 | Micrometer Observation 给 `RestClient` / `WebClient` 打点时注入 |
| OAuth2 token 能在下游看到 | Spring Security 7 的 `@ClientRegistrationId` + `OAuth2AuthorizedClientManager` |
| `X-Forwarded-*` 第一跳正确 | Gateway 的 `Filter` 直接操作 `ServerWebExchange` |

## 2. 设计原则

1. **默认关闭**：`autumn.propagation.enabled=false`。开关在每次调用时读取，配置中心（Consul blocking query / Nacos 长轮询）刷新后立即生效。
2. **白名单优先**：默认只带 `X-Traffic`。不做"整包透传"，避免 header 随着跳数越滚越大、把内部签名 / 内部路由信息带到不该去的地方。
3. **绝不覆盖**：方法上 `@RequestHeader` 显式声明的、以及底层客户端拦截器已经写入的同名 header，一律保留原值。
4. **边界硬隔离**：
   - 传输层 / 逐跳 header（`Content-Length`、`Host`、`Transfer-Encoding`、`Connection` …）永不带，复制会破坏报文语义；
   - `Cookie` / `Set-Cookie` 永不带，属于凭据越权；
   - 链路追踪 header 默认永不带（原因见第 6 节）。
5. **有上限**：`max-headers` 默认 32，超出按出现顺序截断。

## 3. 配置项（前缀 `autumn.propagation`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `false` | 总开关 |
| `mode` | `WHITELIST` | `WHITELIST` 只带 `include`；`BLACKLIST` 除 `exclude` 外全带（内部可信链路才建议用） |
| `include` | `X-Traffic` | 白名单，大小写不敏感；命中即放行（可对冲 `exclude`） |
| `exclude` | 空 | 排除项（主要给 `BLACKLIST` 模式做减法） |
| `max-headers` | `32` | 单次最多透传个数，`0` 不限制 |
| `allow-tracing-headers` | `false` | 允许透传追踪 header（见 6.5） |

## 4. 接入方式

### 4.1 servlet（Spring MVC 服务，`@HttpExchange` + `RestClient`）

```java
@Configuration
@EnableConfigurationProperties(HeaderPropagationProperties.class)
class HeaderPropagationConfig {

    @Bean
    HeaderPropagation headerPropagation(HeaderPropagationProperties properties) {
        // 用 supplier 而不是固定实例，配置刷新后策略自动重建
        return HeaderPropagation.of(() -> properties);
    }

    /** Spring 7 的 HTTP 服务分组：把处理器挂到所有分组的代理工厂上 */
    @Bean
    HttpServiceGroupConfigurer<RestClient.Builder> propagationConfigurer(HeaderPropagation propagation) {
        return groups -> groups.forEachGroup((group, clientBuilder, factoryBuilder) ->
                factoryBuilder.httpRequestValuesProcessor(propagation.processor()));
    }
}
```

手工 `new HttpServiceProxyFactory` 的场景：

```java
HttpServiceProxyFactory factory = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .httpRequestValuesProcessor(propagation.processor())
        .build();
```

### 4.2 reactive（WebFlux / Gateway 内部调用）

```java
@Bean
ExchangeFilterFunction headerPropagationFilter(HeaderPropagation propagation) {
    return new ReactiveHeaderPropagationFilter(propagation);
}

// 挂到具体的 WebClient 上
WebClient client = WebClient.builder()
        .filter(headerPropagationFilter)
        .build();
```

WebFlux 下当前请求不在 ThreadLocal 里，过滤器从 Reactor Context 取 `ServerWebExchange`（key 为 `ServerWebExchange.class`）。链路里需要有把 exchange 写入 context 的过滤器（Gateway 自带）；取不到时**静默跳过**，不影响请求。

如果 context key 与框架版本不一致，构造器支持自定义：

```java
new ReactiveHeaderPropagationFilter(propagation, ctx -> ctx.hasKey(MyKeys.EXCHANGE) ? (ServerWebExchange) ctx.get(MyKeys.EXCHANGE) : null);
```

> 注意：Reactor 的 `ContextView#get()` 在 key 不存在时抛 `NoSuchElementException`（不是返回 `null`），自定义 lookup 里要先 `hasKey`。

### 4.3 手工透传（自研客户端 / 非请求线程）

```java
propagation.apply(targetHeaders, inboundHeaders);   // 不覆盖已有值
propagation.preview(inboundNames, presetNames);     // 调试：看这次会带哪些名字
```

## 5. header 处理矩阵

| 类别 | 例子 | 默认行为 |
|---|---|---|
| 传输层 / 逐跳 | `Host`、`Content-Length`、`Transfer-Encoding`、`Connection`、`TE`、`Upgrade`、`Proxy-*` | **永不** |
| Cookie 类 | `Cookie`、`Set-Cookie` | **永不** |
| 追踪（W3C） | `traceparent`、`tracestate`、`baggage` | **永不** |
| 追踪（B3 / Brave） | `b3`、`X-B3-TraceId`、`X-B3-SpanId`、`X-B3-ParentSpanId`、`X-B3-Sampled`、`X-B3-Flags` | **永不** |
| 追踪（Jaeger / SkyWalking / APM） | `uber-trace-id`、`jaeger-debug-id`、`x-ot-span-context`、`sw8`、`sw8-correlation`、`elastic-apm-traceparent` | **永不** |
| 业务（白名单内） | `X-Traffic` | 透传（不覆盖已有） |
| 业务（白名单外） | `X-Internal-Sign`、`X-User-Id` … | 不透传 |

## 6. 与链路追踪中间件的兼容

### 6.1 各家到底写哪些 header

| 方案 | 传输 header |
|---|---|
| Micrometer Tracing + Brave（Jaeger / Zipkin 后端） | 默认 W3C：`traceparent` + `tracestate`；也可切 B3：`b3` 或 `X-B3-*` |
| Micrometer Tracing + OTel bridge（OTLP → Jaeger / Tempo） | `traceparent` + `tracestate`（+ 若启用 `baggage`） |
| SkyWalking Agent | `sw8`（+ `sw8-correlation`） |
| 自研 traceId | 通常是 `X-Request-Id` 之类的自定义名，**不属于**上面的追踪集合 |

### 6.2 为什么必须隔离追踪 header

- `traceparent` 表达的是「当前 span 的 id + parent id + 采样标记」这一组**父子关系契约**。把入站的 `traceparent` 原样复制到出站请求，会让下游把网关 / 上游的 span 当成自己的父 span，结果是链路层级错乱、span 重复、耗时统计对不上。
- `sampled` / `flags` 是采样决策，复制会把上游的采样结论强加给下游，或被下游中间件改写导致同一条链路内不一致。
- `baggage` 会随调用链一路累加，不做上限控制会持续膨胀。

正确做法是：**谁产生 span，谁写 `traceparent`**。这正是我们默认排除、且"不覆盖已有值"的原因。

### 6.3 三条兼容约定

1. **默认排除**：第 5 节那张表里的追踪 header 一律不带，白名单也开不了（除非显式打开 `allow-tracing-headers`）。
2. **不覆盖**：即便顺序意外反转（我们的扩展点先跑了），目标请求上已有的同名 header 也不动，中间件写入的最终值生效。
3. **执行时机在上游**：`HttpRequestValues.Processor` 在参数解析之后、底层客户端执行之前运行，因此 Micrometer / OTel 的注入一定发生在我们之后，最终 `traceparent` 归中间件所有。

### 6.4 顺序建议

| 场景 | 建议顺序（由外到内） |
|---|---|
| MVC + `RestClient` | `RestClient` 的 observation 拦截器（Boot 自动装配）→ 业务拦截器；我们的 Processor 在两者之前 |
| WebFlux + `WebClient` | `WebClient.Builder#filter(our)` 与 observation 过滤器顺序不重要，因为"不覆盖 + 默认排除"两者都成立 |
| Gateway 自身转发 | 由 Gateway 的 filter 负责；本库只用于 Gateway **内部**发起 `@HttpExchange` / `WebClient` 调用 |

### 6.5 老系统没有 instrumentation 时的兜底

如果某一跳确实没有 tracing 中间件（例如遗留系统仍靠手工传递 `uber-trace-id`），可以：

```properties
autumn.propagation.allow-tracing-headers=true
autumn.propagation.include=uber-trace-id
```

- 只是"解除默认排除"，**仍然遵守不覆盖**，也不会连带放行其它追踪 header；
- 这种用法等于由本库替中间件承担传播职责，只应作为过渡手段，应尽快让该跳接入 Micrometer Tracing。

### 6.6 后续治理演进（预留方向）

- **SPI 化 HeaderSource**：把"从哪个上下文取 header"抽象成接口，供自研网关、异步任务、消息消费线程注入自己的上下文；
- **响应头回传**：目前只做请求方向；下游返回的 `X-Trace-Id` / `Set-Cookie` 需要单独的回传策略，不能复用本库；
- **网关侧清洗**：外部请求的 `X-Traffic` / `X-Tenant-Id` 必须由网关重写而不是信任客户端传入，本库只负责内部跳之间的补齐；
- **与日志关联**：把 `traceId` / `X-Request-Id` 注入 MDC，与 tracing 中间件的 log correlation 对齐；
- **与负载均衡联动**：`X-Traffic` 同时是 `traffic-starter` 的泳道 hint，透传后第二跳的 `ServiceInstanceListSupplier` 才能选到同泳道实例。

## 7. 排障清单

| 症状 | 排查顺序 |
|---|---|
| 完全没生效 | `autumn.propagation.enabled` 是否为 `true`（默认关闭）；配置是否真的刷新到 |
| 只带了部分 header | 是否在 `WHITELIST` 模式下漏配 `include`；是否被 `max-headers` 截断；是否命中传输层 / 追踪硬阻断 |
| 值不是预期的 | 方法上是否已有 `@RequestHeader` 显式声明（它优先）；底层拦截器是否已写过同名 header |
| WebFlux 完全不生效 | Reactor Context 里有没有 `ServerWebExchange`；是否只挂了 Processor（servlet 专用）却没用 filter |
| 第二跳还是回落主干 | 透传是否挂到了**那个** client 分组；调用是否发生在请求线程；目标实例是否真的打了同泳道标签 |

## 8. 已知限制

- 只覆盖 `@HttpExchange` / `RestClient` / `WebClient`；Feign、自研 HTTP 客户端需走 `apply(...)`；
- `@Async` / 线程池 / 定时任务里 `RequestContextHolder` 为空，会静默跳过（需要的话用 `TaskDecorator` 传递）；
- 仅请求方向，不做响应头回传；
- 依赖 Spring Framework 7 的 `HttpRequestValues.Builder#configureHeaders`；Framework 6.x 只能用 `addHeader`，且拿不到已有 header 视图。
