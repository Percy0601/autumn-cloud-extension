# autumn-cloud-extension 使用说明

> 适用版本：Spring Boot 4.1.x / Spring Cloud 2025.1.x / Java 17+
> 参考：spring-cloud-consul（注册/发现/自动注册模式）、autumn-gateway（LoadBalancer 扩展模式）
> 本文对应**拆分后**的 6 Starter 结构；总览见 [README.md](../README.md)

## 1. 架构总览

```
业务应用（按需引入）
 ├─ discovery-{consul|nacos|multicast}--starter   服务注册与发现（只引一个）
 │    └─ Spring Cloud DiscoveryClient / ReactiveDiscoveryClient / 自动注册
 ├─ config-{consul|nacos}--starter                配置中心（可选，二选一）
 │    └─ 启动期 PropertySource 注入 + 变更监听 + ContextRefresher 刷新
 └─ traffic-starter                              泳道路由（可选，仅网关侧）
      └─ @LoadBalancerClients → TrafficServiceInstanceListSupplier
                     │
                     ▼
       autumn-cloud-extension-common（纯 Java，零 Spring 依赖）
        ├─ model      InstanceRegistration / InstanceSnapshot
        ├─ traffic    Traffic 常量（主干 default）/ TrafficSelector
        ├─ config     ConfigSources（远程配置 .properties 解析）
        ├─ support    BackendValidator（启动期后端校验）
        ├─ http       JsonHttp（JDK 原生 HttpClient 封装）
        ├─ consul     Consul 服务发现 / KV 配置中心客户端
        ├─ nacos      Nacos 服务发现 / Config 客户端
        └─ multicast  MulticastDiscovery（UDP 组播核心）
```

- 每个 Starter 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动装配，另注册一个无条件的 `BackendGuard` 做启动期校验。
- 三种发现后端互斥：`autumn.discovery.backend` 只引一个 Starter 时可省略；多个共存时必须显式指定，否则启动失败。
- 配置中心互斥且**必须显式指定** `autumn.config.backend` 才启用。

## 2. 引入依赖

```xml
<!-- 服务发现：三选一，只引需要的那个 -->
<dependency>
    <groupId>xyz.we-win.autumn</groupId>
    <artifactId>autumn-cloud-extension-discovery-multicast-starter</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>

<!-- 配置中心：可选，二选一 -->
<dependency>
    <groupId>xyz.we-win.autumn</groupId>
    <artifactId>autumn-cloud-extension-config-nacos-starter</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>

<!-- 泳道路由：可选，网关侧引入（会带入 spring-cloud-loadbalancer） -->
<dependency>
    <groupId>xyz.we-win.autumn</groupId>
    <artifactId>autumn-cloud-extension-traffic-starter</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

网关侧还需要 Spring Cloud Gateway（`spring-cloud-starter-gateway-server-webflux`）；服务侧只需 `spring-boot-starter-web` 或 `webflux`。
使用 `@RefreshScope` 需要 `spring-cloud-context`（两个 config Starter 已传递引入）。

## 3. 配置项总表

### 3.1 服务发现通用（前缀 `autumn.discovery`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `backend` | 可省略 | `consul` / `nacos` / `multicast`；只引一个 Starter 时自动激活，多个共存时必填 |
| `register` | `true` | 是否注册自身（网关侧设为 `false`） |
| `service` | `spring.application.name` | 注册的服务名 |
| `host` | 自动探测本机 IP | 注册地址 |
| `port` | `server.port` | 注册端口 |
| `traffic` | `default` | 本实例泳道标签，写入 metadata `traffic`；未打标同样视为 `default` |
| `metadata.xxx` | - | 附加元数据 |

### 3.2 Consul 服务发现（前缀 `autumn.discovery.consul`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `host` | `127.0.0.1` | Consul Agent 地址 |
| `port` | `8500` | Consul HTTP 端口 |
| `scheme` | `http` | `http` / `https` |
| `token` | - | ACL Token |
| `namespace` | - | Consul Enterprise Namespace（`X-Consul-Namespace` 请求头） |
| `health-check-ttl-seconds` | `30` | TTL 健康检查周期，按 TTL/3 自动续约 |

使用的 HTTP API：`PUT /v1/agent/service/register`、`PUT /v1/agent/service/deregister/{id}`、
`PUT /v1/agent/check/pass/service:{id}`、`GET /v1/health/service/{name}?passing=true`、`GET /v1/catalog/services`。
泳道写入 Service `Meta["traffic"]` 与 `Tags`。

### 3.3 Nacos 服务发现（前缀 `autumn.discovery.nacos`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `server-addr` | `127.0.0.1:8848` | Nacos 服务端地址 |
| `namespace` | `public` | 命名空间 |
| `group` | `DEFAULT_GROUP` | 分组 |
| `access-token` | - | 鉴权 Token |
| `beat-interval-ms` | `5000` | 临时实例心跳间隔 |

使用的 OpenAPI v1：`POST /nacos/v1/ns/instance`（ephemeral=true）、`PUT /nacos/v1/ns/instance/beat`、
`DELETE /nacos/v1/ns/instance`、`GET /nacos/v1/ns/instance/list?healthyOnly=true`、`GET /nacos/v1/ns/service/list`。
泳道写入实例 `metadata["traffic"]`。

### 3.4 组播服务发现（前缀 `autumn.discovery.multicast`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `group` | `239.192.71.88` | 组播地址 |
| `port` | `47891` | 组播端口 |
| `ttl` | `1` | 1 = 仅本网段 |
| `heartbeat-interval-ms` | `3000` | 心跳广播间隔 |
| `evict-after-ms` | `15000` | 超时未心跳视为下线（建议 ≥ 3×心跳间隔） |
| `network-interface` | 自动探测 | 多网卡/VPN 环境建议显式指定 |

实现要点：IPv4 `DatagramChannel` bind 到组播地址本身（同主机多进程共享端口）、
独立未 bind 的发送通道（避免源地址为组播地址被丢弃）、停机广播 `bye` 帧即时摘除、异常退出靠超时清扫。

### 3.5 配置中心（前缀 `autumn.config`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | `false` 时完全不装配 |
| `backend` | 无 | `nacos` / `consul`，**必须显式设置才启用** |
| `data-id` | `${spring.application.name}.properties` | 远程 dataId，内容按 `.properties` 解析 |
| `group` | 空 | 本次拉取分组，优先于 `default-group` |
| `default-group` | `DEFAULT_GROUP` | 通用默认分组 |
| `default-namespace` | 后端默认值 | Nacos namespace / Consul datacenter |
| `refresh-enabled` | `true` | 是否注册变更监听并触发上下文刷新 |

后端专属：

| 前缀 | 主要配置 |
|---|---|
| `autumn.config.nacos` | `server-addr`(127.0.0.1:8848)、`namespace`(public)、`default-group`、`access-token`、`polling-timeout-ms`(30000) |
| `autumn.config.consul` | `host`(127.0.0.1)、`port`(8500)、`scheme`(http)、`token`、`datacenter`(dc1)、`default-group` |

加载与刷新流程：

```
启动期（BeanFactoryPostProcessor，早于任何单例 Bean 创建）
  → ConfigCenterClient.getConfig(dataId, group)
  → ConfigSources.parse(.properties)
  → Environment.getPropertySources().addFirst(MapPropertySource("autumnConfigCenter"))
  → @Value 可直接读到远程值（远程优先于本地）

运行期（refresh-enabled=true）
  → addListener(dataId, group, Consumer<String>)
  → Nacos 长轮询 / Consul blocking query 检测到变化
  → 原地更新该 PropertySource 的 Map
  → ContextRefresher.refresh() → @RefreshScope Bean 重新绑定
```

远程不可达、内容为空或格式非法时只记录告警，应用继续使用本地配置。

### 3.6 泳道路由（前缀 `autumn.traffic`，网关侧）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `header` | `X-Traffic` | 携带泳道标签的请求 header（**不是** `header-name` / `X-Autumn-Traffic-Label`） |
| `fallback-to-main` | `true` | 目标标签无实例时回落到 `default` 主干（键名保留兼容） |

## 4. 流量路由规则

调用链（对齐 autumn-gateway 的 LoadBalancer 扩展模式）：

```
请求 → Gateway(lb://demo-service)
     → ReactorLoadBalancer(RoundRobin)
       → TrafficServiceInstanceListSupplier.get(request)   ← traffic-starter
         → DiscoveryClientServiceInstanceListSupplier
           → Consul / Nacos / Multicast DiscoveryClient
```

匹配规则（`TrafficSelector`，主干标签 `default`）：

1. 请求 header（默认 `X-Traffic`）指定标签 → 只返回 metadata `traffic` 相同的实例
2. 未携带 header / 实例未打标 → 视为 `default` 主干
3. 目标标签无实例且 `fallback-to-main=true` → 回落 `default` 主干 → 仍无则放行全部（保可用）
4. `fallback-to-main=false` 时无匹配即返回空列表（快速失败，避免灰度流量打到主干）

典型用法：

| 实例 | autumn.discovery.traffic | 请求 | 命中 |
|---|---|---|---|
| A:8101 | `default` | 无 header | A |
| B:8102 | `gray` | `X-Traffic: gray` | B |
| A + B | - | `X-Traffic: beta` | 回落 A |

> 注意：实例标签请与主干名保持一致，用 `default` 而不是 `main`，
> 否则主干请求匹配不到实例会走到"放行全部"，灰度实例可能被普通流量命中。

## 5. 启动期诊断（`BackendValidator`）

| 场景 | 行为 |
|---|---|
| 只引一个 discovery Starter、未写 `backend` | 自动激活 |
| 多个 discovery Starter 共存、未写 `backend` | 启动失败，提示必须显式指定 |
| `backend` 值不在合法集合 | 启动失败，列出合法值 |
| `backend` 指定了但 classpath 没有对应 Starter | 启动失败，提示缺少依赖 |
| 未写 `autumn.config.backend` | 输出 WARN，配置中心不启用 |
| `autumn.config.enabled=false` | 不装配配置中心 |

## 6. 示例模块（autumn-cloud-extension-starter-using）

示例模块为演示后端切换引入了全部 Starter，**实际项目请勿照抄**。

```bash
# 三个终端分别执行（组播后端，无需任何注册中心）
mvn spring-boot:run -Dspring-boot.run.main-class=xyz.wewin.autumn.cloud.example.provider.ProviderApplication -Dspring-boot.run.profiles=provider
mvn spring-boot:run -Dspring-boot.run.main-class=xyz.wewin.autumn.cloud.example.provider.ProviderApplication -Dspring-boot.run.profiles=provider-gray
mvn spring-boot:run -Dspring-boot.run.main-class=xyz.wewin.autumn.cloud.example.gateway.GatewayApplication -Dspring-boot.run.profiles=gateway
```

切换 Consul / Nacos：把 profile 换成 `provider,consul` / `provider-gray,consul` / `gateway,consul`（或 `,nacos`），并确保本机运行着对应服务端。

| Profile | 作用 |
|---|---|
| `provider` | 服务名 demo-service、8101、`default` 标签 |
| `provider-gray` | 服务名 demo-service、8102、`gray` 标签 |
| `gateway` | 8080、`lb://demo-service` 路由、自身不注册 |
| `consul` / `nacos` | 发现后端切换（覆盖默认 multicast） |
| `config` + `config-nacos` / `config-consul` | 配置中心示例（8103） |

```bash
# 配置中心示例：注意 profile 是 config,config-nacos，不是 config,nacos
# config,nacos 会同时激活发现后端的 nacos profile，本机没有 Nacos 时注册会失败
mvn spring-boot:run -Dspring-boot.run.main-class=xyz.wewin.autumn.cloud.example.config.ConfigApplication -Dspring-boot.run.profiles=config,config-nacos
```

配置中心示例需要先在 Nacos/Consul 中创建 `dataId=config-demo.properties`、`group=DEFAULT_GROUP`，内容为 `.properties`：

```properties
demo.config.value=from-remote
demo.config.timeout=5000
```

## 7. 纯 Java API（common 模块，无 Spring）

```java
import xyz.wewin.autumn.cloud.extension.core.model.InstanceRegistration;
import xyz.wewin.autumn.cloud.extension.core.model.InstanceSnapshot;
import xyz.wewin.autumn.cloud.extension.core.multicast.MulticastConfig;
import xyz.wewin.autumn.cloud.extension.core.multicast.MulticastDiscovery;
import xyz.wewin.autumn.cloud.extension.core.traffic.TrafficSelector;

import java.util.Map;

MulticastDiscovery discovery = new MulticastDiscovery(
        new MulticastConfig("239.192.71.88", 47891, 1, 3000, 15000, null));
discovery.start();
discovery.publish(new InstanceRegistration("demo-service", "demo-1", "10.0.0.1", 8080,
        Map.of("traffic", "gray")));

var grayOnly = TrafficSelector.select(
        discovery.getInstances("demo-service"), "gray", InstanceSnapshot::traffic, true);
discovery.close();

// 配置中心客户端（回调参数是新配置内容 String）
// ConfigCenterClient client = new NacosConfigCenterClient(NacosConfigSettings.defaults());
// client.addListener("demo.properties", "DEFAULT_GROUP", newContent -> { ... });
```

`ServiceDiscoveryClient` / `ServiceRegistryClient` 是抽象接口，Spring 侧实现在各 `discovery-*` Starter 中；
纯 Java 场景直接使用 `ConsulClient` / `NacosClient` / `MulticastDiscovery`。

## 8. 构建与测试

```bash
mvn clean test      # 全模块单元测试（TrafficSelector / MulticastDiscovery / BackendValidator / ConfigSources）
mvn clean install   # 安装到本地仓库供示例/外部项目引用
```

## 9. 已知限制

- 远程配置只支持 `.properties` 格式；Nacos 走 OpenAPI v1 + accessToken 直传，未内置账号密码换 token
- `setStatus`（主动标记下线）未实现，摘除依赖 TTL / 心跳超时
- 组播后端适用于开发/局域网，跨网段需调大 TTL 且依赖网络设备放行组播路由
- `common` 打包了全部后端的纯 Java 客户端（约 54 KB），装配层已隔离但字节码未按后端隔离
- 尚无 Spring 上下文集成测试与端到端自动化冒烟，建议改动后按第 6 节手工验证一次
