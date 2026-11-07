# autumn-cloud-extension

轻量级 **Spring Cloud 服务发现与配置管理扩展**：按能力拆分的 6 个 Starter，按需引入即可获得注册发现、配置中心、泳道路由能力。

> 基线：Spring Boot 4.1.x / Spring Cloud 2025.1.x / Java 17+（编译目标 17，可运行于 17~25）

## 核心能力

### 1. 服务注册与发现（`discovery-*` 三选一）
- `consul` —— 直连 Consul HTTP API，TTL 健康检查自动续约（支持 Enterprise Namespace）
- `nacos` —— 直连 Nacos OpenAPI v1，临时实例客户端心跳
- `multicast` —— 纯 JDK UDP 组播实现，**零外部依赖**，适合开发与局域网环境
- 动态感知实例上下线，无需重启；提供 `ReactiveDiscoveryClient` 适配
- **不引入注册中心 SDK**：全部走 HTTP API，仅依赖 JDK `HttpClient` + `jackson`

### 2. 配置中心（`config-*` 二选一，可选）
- 启动时从远程拉取配置，注入 Spring `Environment`（`PropertySource` 置于本地配置之前）
- 配置变更时原地更新 `PropertySource` 并触发 `ContextRefresher.refresh()`，`@RefreshScope` + `@Value` 生效
- 监听方式：Nacos 长轮询（真实 MD5）/ Consul blocking query
- **必须显式设置** `autumn.config.backend` 才启用；未设置会输出明确 WARN，不会静默跳过
- 远程配置内容约定为 `.properties` 格式，例如 `demo.config.value=from-remote`
- 远程不可达时只告警、继续使用本地配置，不阻断启动

### 3. 泳道路由（`traffic-starter`，按需引入）
- 通过 `X-Traffic` 请求头把流量切到对应标签实例
- **主干标签为 `default`**：实例未打标、请求未带头都归属 `default`
- 目标标签无实例时回落 `default` 主干；关闭回落则快速失败（503）
- 支持灰度 / 多环境流量隔离（`default` / `gray` / `beta` …）

### 4. 轻量与 Native Image 友好
- `common` 模块零 Spring 依赖，仅 `jackson-databind`，可作为纯 Java API 独立使用
- 每个 Starter 只引入自己需要的 Spring 构件：`spring-cloud-loadbalancer` 仅 traffic 引入，`spring-cloud-context` 仅 config 引入
- record 类 + `AutoConfiguration.imports` 注册，无反射路由

## 模块结构

```
autumn-cloud-extension/
├── autumn-cloud-extension-common                      # 纯 Java 核心（零 Spring 依赖，约 54 KB）
│   └── core/
│       ├── api/         # ServiceDiscoveryClient / ServiceRegistryClient / ConfigCenterClient 等接口
│       ├── model/       # InstanceRegistration / InstanceSnapshot
│       ├── consul/      # Consul 服务发现与 KV 配置中心客户端
│       ├── nacos/       # Nacos 服务发现与 Config 客户端
│       ├── multicast/   # UDP 组播发现核心
│       ├── config/      # 远程配置内容解析
│       ├── support/     # backend 启动期校验
│       ├── traffic/     # Traffic 常量 / TrafficSelector（主干 default）
│       └── http/        # JsonHttp（JDK HttpClient 封装）
├── autumn-cloud-extension-discovery-consul-starter    # Consul 服务发现
├── autumn-cloud-extension-discovery-nacos-starter     # Nacos 服务发现
├── autumn-cloud-extension-discovery-multicast-starter # 组播服务发现
├── autumn-cloud-extension-config-consul-starter       # Consul KV 配置中心
├── autumn-cloud-extension-config-nacos-starter        # Nacos 配置中心
├── autumn-cloud-extension-traffic-starter             # 泳道路由（仅网关侧需要）
└── autumn-cloud-extension-starter-using               # 使用示例（为演示多后端切换，引了全部 Starter）
```

> 说明：装配层已按能力隔离，但 `common` 仍打包了全部后端的纯 Java 客户端实现，
> 引任一 Starter 都会带上这一份约 54 KB 的字节码；依赖本身很轻（无 SDK、无 httpclient5）。

## 快速开始

### 组播后端（零外部依赖）

```bash
mvn clean install -DskipTests
cd autumn-cloud-extension-starter-using

# 终端1：default 标签 provider（8101）
mvn spring-boot:run -Dspring-boot.run.main-class=xyz.wewin.autumn.cloud.example.provider.ProviderApplication -Dspring-boot.run.profiles=provider

# 终端2：gray 标签 provider（8102）
mvn spring-boot:run -Dspring-boot.run.main-class=xyz.wewin.autumn.cloud.example.provider.ProviderApplication -Dspring-boot.run.profiles=provider-gray

# 终端3：网关（8080）
mvn spring-boot:run -Dspring-boot.run.main-class=xyz.wewin.autumn.cloud.example.gateway.GatewayApplication -Dspring-boot.run.profiles=gateway
```

验证泳道路由：

```bash
# 无 header → default 主干实例（8101）
curl --noproxy '*' http://127.0.0.1:8080/api/hello
# {"service":"demo-service","traffic":"default","port":8101,...}

# gray 标签 → 灰度实例（8102）
curl --noproxy '*' -H "X-Traffic: gray" http://127.0.0.1:8080/api/hello
# {"service":"demo-service","traffic":"gray","port":8102,...}

# 不存在的标签 → 回落 default（8101）
curl --noproxy '*' -H "X-Traffic: beta" http://127.0.0.1:8080/api/hello
```

### 配置中心示例

```bash
# 需先启动 Nacos 或 Consul，并在其中创建 dataId = config-demo.properties、group = DEFAULT_GROUP
# 内容为 .properties，例如：demo.config.value=from-remote
# 注意用 config-nacos / config-consul，不要用 nacos / consul（后者会连带切换服务发现后端）
mvn spring-boot:run -Dspring-boot.run.main-class=xyz.wewin.autumn.cloud.example.config.ConfigApplication -Dspring-boot.run.profiles=config,config-nacos

curl --noproxy '*' http://127.0.0.1:8103/config
# 在 Nacos 控制台改配置后再次访问，@RefreshScope 会重新绑定 @Value
```

## 配置项

### 服务发现（前缀 `autumn.discovery`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `backend` | 可省略 | `consul` / `nacos` / `multicast`。**只引一个 discovery Starter 时可省略**（自动激活）；同时引入多个时必须显式指定 |
| `register` | `true` | 是否注册自身（网关侧设为 `false`） |
| `service` | `spring.application.name` | 注册的服务名 |
| `host` | 自动探测本机 IP | 注册地址 |
| `port` | `server.port` | 注册端口 |
| `traffic` | `default` | 本实例泳道标签，写入 metadata `traffic` |
| `metadata.xxx` | - | 附加元数据 |

后端专属前缀：`autumn.discovery.consul.*`、`autumn.discovery.nacos.*`、`autumn.discovery.multicast.*`（详见 [docs/usage.md](docs/usage.md)）。

### 配置中心（前缀 `autumn.config`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | `false` 时完全不装配配置中心（真实生效） |
| `backend` | 无 | `nacos` / `consul`。**必须显式设置才启用**；拼错或未引对应 Starter 会启动失败 |
| `data-id` | `${spring.application.name}.properties` | 远程配置 dataId，内容按 `.properties` 解析 |
| `group` | 空 | 本次拉取使用的分组，优先于 `default-group` |
| `default-group` | `DEFAULT_GROUP` | 通用默认分组（真实生效） |
| `default-namespace` | 后端默认值 | Nacos 命名空间 / Consul datacenter（真实生效） |
| `refresh-enabled` | `true` | 是否注册变更监听；`false` 时仍加载启动期配置，但不自动刷新 |

后端专属前缀：`autumn.config.nacos.*`（`server-addr` / `namespace` / `access-token` / `polling-timeout-ms` …）、`autumn.config.consul.*`（`host` / `port` / `scheme` / `token` / `datacenter` …）。

### 泳道路由（前缀 `autumn.traffic`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `header` | `X-Traffic` | 携带泳道标签的请求 header |
| `fallback-to-main` | `true` | 目标标签无实例时是否回落到 `default` 主干（键名保持兼容） |

## 启动期诊断

`BackendValidator` 在 Bean 实例化前校验后端选择，避免"配错却什么都不发生"：

| 场景 | 行为 |
|---|---|
| 只引一个 discovery Starter、未写 `backend` | 自动激活，无需配置 |
| 多个 discovery Starter 共存、未写 `backend` | 启动失败，提示必须显式指定 |
| `backend` 拼错（如 `nacoss`） | 启动失败，列出合法值 |
| `backend` 指定了但没引对应 Starter | 启动失败，提示缺少哪个依赖 |
| 未写 `autumn.config.backend` | 输出 WARN，配置中心不启用（不会静默） |

## 接入项目

按需引入 Starter（**只引需要的**，不要照抄示例模块的 pom，它为了演示切后端引了全部）：

```xml
<!-- 服务发现：三选一 -->
<dependency>
    <groupId>xyz.we-win.autumn</groupId>
    <artifactId>autumn-cloud-extension-discovery-nacos-starter</artifactId>
    <version>1.0.0-RELEASE</version>
</dependency>

<!-- 配置中心：可选，二选一 -->
<dependency>
    <groupId>xyz.we-win.autumn</groupId>
    <artifactId>autumn-cloud-extension-config-nacos-starter</artifactId>
    <version>1.0.0-RELEASE</version>
</dependency>

<!-- 泳道路由：可选，仅网关侧 -->
<dependency>
    <groupId>xyz.we-win.autumn</groupId>
    <artifactId>autumn-cloud-extension-traffic-starter</artifactId>
    <version>1.0.0-RELEASE</version>
</dependency>
```

```properties
# ---- 服务提供方 ----
spring.application.name=demo-service
server.port=8101
autumn.discovery.backend=nacos
autumn.discovery.nacos.server-addr=127.0.0.1:8848
autumn.discovery.traffic=default

# ---- 配置中心 ----
autumn.config.enabled=true
autumn.config.backend=nacos
autumn.config.data-id=demo-service.properties
autumn.config.default-group=DEFAULT_GROUP
autumn.config.refresh-enabled=true

# ---- 网关侧 ----
autumn.discovery.register=false
autumn.traffic.header=X-Traffic
autumn.traffic.fallback-to-main=true
spring.cloud.gateway.server.webflux.routes[0].uri=lb://demo-service
```

使用远程配置：

```java
@RestController
@RefreshScope  // 配置变更时自动重新绑定
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

`common` 模块可脱离 Spring 单独使用（以下示例可直接编译）：

```java
import xyz.wewin.autumn.cloud.extension.core.api.ConfigCenterClient;
import xyz.wewin.autumn.cloud.extension.core.model.InstanceRegistration;
import xyz.wewin.autumn.cloud.extension.core.model.InstanceSnapshot;
import xyz.wewin.autumn.cloud.extension.core.multicast.MulticastConfig;
import xyz.wewin.autumn.cloud.extension.core.multicast.MulticastDiscovery;
import xyz.wewin.autumn.cloud.extension.core.nacos.NacosConfigCenterClient;
import xyz.wewin.autumn.cloud.extension.core.nacos.NacosConfigSettings;
import xyz.wewin.autumn.cloud.extension.core.traffic.TrafficSelector;

import java.util.Map;

// 配置中心：监听回调参数是新配置内容（String），不是事件对象
ConfigCenterClient configClient = new NacosConfigCenterClient(NacosConfigSettings.defaults());
String content = configClient.getConfig("demo-service.properties", "DEFAULT_GROUP", 5000);
configClient.addListener("demo-service.properties", "DEFAULT_GROUP",
        newContent -> System.out.println("Config changed: " + newContent));
configClient.close();

// 组播发现：零依赖
MulticastDiscovery discovery = new MulticastDiscovery(
        new MulticastConfig("239.192.71.88", 47891, 1, 3000, 15000, null));
discovery.start();
discovery.publish(new InstanceRegistration(
        "demo-service", "demo-1", "10.0.0.1", 8080, Map.of("traffic", "gray")));
var grayOnly = TrafficSelector.select(
        discovery.getInstances("demo-service"), "gray", InstanceSnapshot::traffic, true);
discovery.close();
```

> `ServiceDiscoveryClient` / `ServiceRegistryClient` 是抽象接口，Spring 侧实现由各 `discovery-*` Starter 提供；
> 纯 Java 场景直接使用 `ConsulClient` / `NacosClient` / `MulticastDiscovery`。

## 已知限制

- 远程配置只支持 `.properties` 格式，不支持 YAML / JSON
- Nacos 走 OpenAPI v1 + accessToken 直传，未内置用户名密码自动登录换 token
- `setStatus`（主动标记下线）未实现，摘除依赖 TTL / 心跳超时
- 组播后端适用于开发/局域网，跨网段需调大 TTL 且依赖网络设备放行组播路由
- 目前单元测试以纯 Java 逻辑为主，尚无完整的 Spring 上下文集成测试与端到端自动化冒烟

## License

Apache License 2.0
