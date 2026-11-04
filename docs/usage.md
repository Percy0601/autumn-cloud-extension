# autumn-cloud-extension 使用说明

> 适用版本：Spring Boot 4.1.x / Spring Cloud 2025.1.x / Java 17+
> 参考：spring-cloud-consul（注册/发现/自动注册模式）、autumn-gateway（LoadBalancer 扩展模式）

## 1. 架构总览

```
                        ┌─────────────────────────────────────────────┐
                        │        autumn-cloud-extension-starter       │
                        ├─────────────────────────────────────────────┤
  core（纯 Java，零 Spring 依赖，可独立作为 Java API 使用）           │
   ├─ model          InstanceRegistration / InstanceSnapshot         │
   ├─ trafficlabel   Traffic 常量 / TrafficSelector 选择器  │
   ├─ http           JsonHttp（apache-httpclient5 封装）              │
   ├─ multicast      MulticastDiscovery（UDP 组播核心）               │
   └─ util           Networks                                        │
                        ├─────────────────────────────────────────────┤
  Spring 集成层                                                        │
   ├─ config     AutumnDiscoveryProperties / AutumnRegistration /     │
   │             AutumnAutoServiceRegistration / 通用响应式适配        │
   ├─ consul     Consul HTTP API 后端（TTL 续约）                      │
   ├─ nacos      Nacos OpenAPI v1 后端（客户端心跳）                   │
   ├─ multicast  组播后端                                              │
   └─ trafficlabel  TrafficServiceInstanceListSupplier           │
                   （LoadBalancer 实例列表流量过滤）                │
                        └─────────────────────────────────────────────┘
```

三后端通过 `autumn.discovery.backend` **三选一互斥装配**（`@ConditionalOnProperty(havingValue=...)`），
同时只会有一个后端的 Bean 生效，避免冲突。

## 2. 引入依赖

```xml
<dependency>
    <groupId>com.autumn.cloud</groupId>
    <artifactId>autumn-cloud-extension-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

网关侧还需要 Spring Cloud Gateway（server-webflux）本身；服务侧只需 `spring-boot-starter-web`（或 webflux）。

## 3. 配置项总表

### 3.1 通用（三后端共享，前缀 `autumn.discovery`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `autumn.discovery.backend` | 无（必填） | `consul` / `nacos` / `multicast` 三选一 |
| `autumn.discovery.register` | `true` | 是否注册自身（网关侧设为 `false`） |
| `autumn.discovery.service` | `spring.application.name` | 注册的服务名 |
| `autumn.discovery.host` | 自动探测本机 IP | 注册地址 |
| `autumn.discovery.port` | `server.port` | 注册端口 |
| `autumn.discovery.traffic` | `default` | **本实例流量**，写入 metadata `traffic` |
| `autumn.discovery.metadata.xxx` | - | 附加元数据 |

### 3.2 Consul（前缀 `autumn.discovery.consul`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `host` | `127.0.0.1` | Consul Agent 地址 |
| `port` | `8500` | Consul HTTP 端口 |
| `scheme` | `http` | `http` / `https` |
| `token` | - | ACL Token（未启用 ACL 不用配） |
| `health-check-ttl-seconds` | `30` | TTL 健康检查周期，自动按 TTL/3 续约 |

使用的 HTTP API：`PUT /v1/agent/service/register`、`PUT /v1/agent/service/deregister/{id}`、
`PUT /v1/agent/check/pass/service:{id}`、`GET /v1/health/service/{name}?passing=true`、`GET /v1/catalog/services`。
流量写入 Service `Meta["traffic"]` 与 `Tags`。

### 3.3 Nacos（前缀 `autumn.discovery.nacos`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `server-addr` | `127.0.0.1:8848` | Nacos 服务端地址 |
| `namespace` | `public` | 命名空间 |
| `group` | `DEFAULT_GROUP` | 分组 |
| `access-token` | - | 鉴权 Token（未开启鉴权不用配） |
| `beat-interval-ms` | `5000` | 临时实例心跳间隔 |

使用的 OpenAPI v1：`POST /nacos/v1/ns/instance`（注册，ephemeral=true）、`PUT /nacos/v1/ns/instance/beat`、
`DELETE /nacos/v1/ns/instance`、`GET /nacos/v1/ns/instance/list?healthyOnly=true`、`GET /nacos/v1/ns/service/list`。
流量写入实例 `metadata["traffic"]`。

### 3.4 组播（前缀 `autumn.discovery.multicast`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `group` | `239.192.71.88` | 组播地址（224.0.0.0~239.255.255.255） |
| `port` | `47891` | 组播端口 |
| `ttl` | `1` | 1 = 仅本网段 |
| `heartbeat-interval-ms` | `3000` | 心跳广播间隔 |
| `evict-after-ms` | `15000` | 超时未心跳视为下线（建议 ≥ 3×心跳间隔） |
| `network-interface` | 自动探测 | 指定网卡名（如 `en0`），多网卡/VPN 环境建议显式指定 |

实现要点（跨平台兼容已实测）：
- IPv4 `DatagramChannel`，**bind 到组播地址本身**（macOS 上同主机多进程共享端口时，bind 通配地址只有最后启动的进程能收到）
- **独立发送通道**（未 bind），源地址为本机 IP（源地址为组播地址的包会被丢弃）
- 正常停机广播 `bye` 帧即时摘除；异常退出靠超时清扫

### 3.5 流量路由（前缀 `autumn.traffic`，网关侧）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `header-name` | `X-Traffic` | 携带流量的请求 header |
| `fallback-to-main` | `true` | 目标签无实例时是否回落 |

## 4. 流量路由规则

调用链（对齐 autumn-gateway 的 LoadBalancer 扩展模式）：

```
请求 → Gateway(lb://demo-service)
     → ReactorLoadBalancer(RoundRobin)
       → TrafficServiceInstanceListSupplier.get(request)   ← 本扩展
         → DiscoveryClientServiceInstanceListSupplier
           → Consul/Nacos/Multicast DiscoveryClient
```

匹配规则（`TrafficSelector`）：

1. 请求 header（默认 `X-Traffic`）指定标签 → 只返回 metadata `traffic` 相同的实例
2. 未携带 header → 视为 `default` 标签 → 只返回未打标/default 实例
3. 目标签无实例且 `fallback-to-main=true` → 回落 default 标签实例 → 仍无则放行全部（保可用）
4. `fallback-to-main=false` 时无匹配即 503（快速失败，避免灰度流量打到正式实例）

典型用法：

| 实例 | autumn.discovery.traffic | 请求 | 命中 |
|---|---|---|---|
| A:8101 | （缺省 default） | 无 header | A |
| B:8102 | gray | `X-Traffic: gray` | B |
| A + B | - | `X-Traffic: beta` | 回落 A |

## 5. 示例模块（autumn-cloud-extension-starter-using）

```bash
# 三个终端分别执行（组播后端，无需任何注册中心）
mvn spring-boot:run -Dspring-boot.run.main-class=com.autumn.cloud.example.provider.ProviderApplication -Dspring-boot.run.profiles=provider
mvn spring-boot:run -Dspring-boot.run.main-class=com.autumn.cloud.example.provider.ProviderApplication -Dspring-boot.run.profiles=provider-gray
mvn spring-boot:run -Dspring-boot.run.main-class=com.autumn.cloud.example.gateway.GatewayApplication -Dspring-boot.run.profiles=gateway
```

切换 Consul / Nacos：把 profile 换成 `provider,consul` / `provider-gray,consul` / `gateway,consul`（或 `,nacos`），
并确保本机运行着对应服务端。

Profile 说明：

| Profile | 作用 |
|---|---|
| `provider` | 服务名 demo-service、8101、default 标签 |
| `provider-gray` | 服务名 demo-service、8102、gray 标签 |
| `gateway` | 8080、`lb://demo-service` 路由、自身不注册 |
| `consul` / `nacos` | 后端切换（覆盖默认 multicast） |

## 6. 纯 Java API（core 包，无 Spring）

```java
// 组播发现
MulticastDiscovery discovery = new MulticastDiscovery(
        new MulticastConfig("239.192.71.88", 47891, 1, 3000, 15000, null));
discovery.start();
discovery.publish(new InstanceRegistration("demo-service", "demo-1", "10.0.0.1", 8080,
        Map.of("traffic", "gray")));

List<InstanceSnapshot> instances = discovery.getInstances("demo-service");
List<InstanceSnapshot> grayOnly = TrafficSelector.select(
        instances, "gray", InstanceSnapshot::traffic, true);
discovery.close();

// HTTP API 直连（Consul 示例）
try (JsonHttp http = new JsonHttp()) {
    String services = http.get("http://127.0.0.1:8500/v1/catalog/services", null);
}
```

## 7. 构建与测试

```bash
mvn clean package        # 全模块构建 + 单元测试（TrafficSelector / MulticastDiscovery）
mvn clean install        # 安装到本地仓库供示例/外部项目引用
```

## 8. 已知限制

- 三后端互斥：请勿尝试同时启用多个 backend（属性三选一本身即互斥）
- Nacos 走 OpenAPI v1 + accessToken 直传；暂未内置 username/password 自动登录换 token
- Consul/Nacos 的 `setStatus`（标记下线）暂未实现，摘除依赖 TTL/心跳超时
- 组播后端适用于开发/局域网场景，跨网段需调大 TTL 且依赖网络设备放行组播路由
- 网关动态路由（`spring.cloud.gateway.discovery.locator.enabled`）依赖 ReactiveDiscoveryClient，
  本 starter 已提供响应式适配（`ReactiveDiscoveryClientAdapter`）可直接使用
