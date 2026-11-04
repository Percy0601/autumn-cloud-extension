# autumn-cloud-extension

轻量级 **Spring Cloud 服务发现与配置管理扩展**：一个 starter 集成三种注册中心后端 + 配置中心 + 多流量（traffic）泳道路由。

> 基线：Spring Boot 4.1.x / Spring Cloud 2025.1.x / Java 17+（编译目标 17，可运行于 17~25）

## 核心能力

### 1. 服务注册与发现
- **三后端合一**（`autumn.discovery.backend` 三选一互斥装配）：
  - `consul` —— 直连 Consul HTTP API，TTL 健康检查自动续约
  - `nacos` —— 直连 Nacos OpenAPI v1，临时实例客户端心跳
  - `multicast` —— 纯 JDK UDP 组播实现，**零外部依赖**，适合开发环境
- **动态服务发现**：服务实例数量变化实时感知，无需重启
- **不引入注册中心 SDK**：全部走 HTTP API，仅依赖 JDK `HttpClient` + `jackson`

### 2. 配置中心
- **兼容 Spring Cloud Config 机制**：
  - `@RefreshScope` + `@Value` 自动刷新配置
  - 配置变更监听：Nacos 长轮询 / Consul blocking query
- **双后端支持**（`autumn.config.backend` 二选一）：
  - `nacos` —— Nacos Config API v1
  - `consul` —— Consul KV API
- **零 SDK 依赖**：纯 HTTP API 直连

### 3. 泳道路由（流量）
- **独立 Starter**：`autumn-cloud-extension-traffic-starter`，按需引入
- **HTTP Header 路由**：通过 `X-Traffic` 头将流量切到对应标签实例
- **主干回落**：无特殊用到的实例，路由到 main 主干
- **灰度发布**：支持多环境流量隔离（main / gray / beta 等）

### 4. GraalVM Native Image 友好
- 纯 Java API（common 模块零 Spring 依赖）
- 无反射路由、构造器注入
- record 类 + `AutoConfiguration.imports` 注册

## 模块结构

```
autumn-cloud-extension/
├── autumn-cloud-extension-common                    # 纯 Java 核心（零 Spring 依赖）
│   └── core/
│       ├── api/                       # 通用 API 接口
│       │   ├── ServiceDiscoveryClient # 服务发现客户端
│       │   ├── ServiceRegistryClient  # 服务注册客户端
│       │   ├── ConfigCenterClient     # 配置中心客户端
│       │   └── ConfigListener         # 配置监听器
│       ├── model/                     # 服务实例模型
│       ├── consul/                    # Consul HTTP API 客户端
│       ├── nacos/                     # Nacos HTTP API 客户端
│       ├── multicast/                 # 组播发现核心
│       ├── traffic/                   # 流量选择器（纯 Java）
│       └── http/                      # HTTP 工具
├── autumn-cloud-extension-config-consul-starter     # Consul 配置中心 Starter
├── autumn-cloud-extension-config-nacos-starter      # Nacos 配置中心 Starter
├── autumn-cloud-extension-discovery-consul-starter  # Consul 服务发现 Starter
├── autumn-cloud-extension-discovery-nacos-starter   # Nacos 服务发现 Starter
├── autumn-cloud-extension-discovery-multicast-starter # 组播服务发现 Starter
├── autumn-cloud-extension-traffic-starter           # 泳道路由 Starter（独立模块）
└── autumn-cloud-extension-starter-using             # 使用示例
    ├── provider/                      # 服务提供方示例
    ├── gateway/                       # 网关示例
    └── config/                        # 配置中心示例
```

## 快速开始

### 组播后端（零外部依赖）

```bash
mvn clean install -DskipTests

cd autumn-cloud-extension-starter-using

# 终端1：default 标签 provider（8101）
mvn spring-boot:run -Dspring-boot.run.main-class=com.autumn.cloud.example.provider.ProviderApplication -Dspring-boot.run.profiles=provider

# 终端2：gray 标签 provider（8102）
mvn spring-boot:run -Dspring-boot.run.main-class=com.autumn.cloud.example.provider.ProviderApplication -Dspring-boot.run.profiles=provider-gray

# 终端3：网关（8080）
mvn spring-boot:run -Dspring-boot.run.main-class=com.autumn.cloud.example.gateway.GatewayApplication -Dspring-boot.run.profiles=gateway
```

验证泳道路由：

```bash
# 无 header → default 实例（8101）
curl http://127.0.0.1:8080/api/hello
# {"service":"demo-service","traffic":"default","port":8101,...}

# gray 标签 → 灰度实例（8102）
curl -H "X-Traffic: gray" http://127.0.0.1:8080/api/hello
# {"service":"demo-service","traffic":"gray","port":8102,...}

# 不存在的标签 → 回落 default
curl -H "X-Traffic: beta" http://127.0.0.1:8080/api/hello
```

### 配置中心示例

```bash
# 启动配置中心示例（需先启动 Nacos 或 Consul）
mvn spring-boot:run -Dspring-boot.run.main-class=com.autumn.cloud.example.config.ConfigApplication -Dspring-boot.run.profiles=config,nacos

# 查看当前配置
curl http://127.0.0.1:8103/config

# 在 Nacos 控制台修改配置后，再次访问 /config 观察自动刷新
```

## 配置项

### 服务发现（前缀 `autumn.discovery`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `backend` | 无（必填） | `consul` / `nacos` / `multicast` 三选一 |
| `register` | `true` | 是否注册自身（网关侧设为 `false`） |
| `service` | `spring.application.name` | 注册的服务名 |
| `traffic` | `default` | 本实例流量（泳道标识） |

### 配置中心（前缀 `autumn.config`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用配置中心 |
| `backend` | 无 | `nacos` / `consul` 二选一 |
| `refresh-enabled` | `true` | 是否启用配置自动刷新 |
| `default-group` | `DEFAULT_GROUP` | 默认分组 |

### 泳道路由（前缀 `autumn.traffic`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `header` | `X-Traffic` | 携带流量的请求 header |
| `fallback-to-main` | `true` | 目标签无实例时是否回落 main |

## 接入项目

按需引入 Starter：

```xml
<!-- 服务发现（三选一） -->
<dependency>
    <groupId>com.autumn.cloud</groupId>
    <artifactId>autumn-cloud-extension-discovery-consul-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- 或 nacos / multicast -->

<!-- 配置中心（可选，二选一） -->
<dependency>
    <groupId>com.autumn.cloud</groupId>
    <artifactId>autumn-cloud-extension-config-consul-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- 泳道路由（可选，网关侧引入） -->
<dependency>
    <groupId>com.autumn.cloud</groupId>
    <artifactId>autumn-cloud-extension-traffic-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```properties
# 服务提供方
spring.application.name=demo-service
server.port=8101
autumn.discovery.backend=nacos
autumn.discovery.nacos.server-addr=127.0.0.1:8848
autumn.discovery.traffic=gray

# 配置中心
autumn.config.enabled=true
autumn.config.backend=nacos
autumn.config.refresh-enabled=true

# 网关方
autumn.discovery.register=false
spring.cloud.gateway.server.webflux.routes[0].uri=lb://demo-service
```

使用配置：

```java
@RestController
@RefreshScope  // 配置变更时自动刷新
public class MyController {
    
    @Value("${my.config.value:default}")
    private String configValue;
    
    @GetMapping("/config")
    public String getConfig() {
        return configValue;
    }
}
```

## 纯 Java API（无 Spring）

```java
// 服务发现
ServiceDiscoveryClient client = new ConsulServiceDiscoveryClient(consulClient);
List<ServiceInstance> instances = client.getInstances("user-service");

// 配置中心
ConfigCenterClient configClient = new NacosConfigCenterClient(properties);
String config = configClient.getConfig("application.yaml", "DEFAULT_GROUP", 5000);

// 监听配置变更
configClient.addListener("application.yaml", "DEFAULT_GROUP", event -> {
    System.out.println("Config changed: " + event.newContent());
});

// 组播发现（零依赖）
MulticastDiscovery discovery = new MulticastDiscovery(new MulticastConfig(...));
discovery.start();
discovery.publish(new InstanceRegistration(...));
```

## License

Apache License 2.0
