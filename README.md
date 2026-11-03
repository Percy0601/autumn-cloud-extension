# autumn-cloud-extension

轻量级 **Spring Cloud Gateway 服务发现扩展**：一个 starter 集成三种注册中心后端 + 多流量标签（traffic-label）负载均衡。

> 基线：Spring Boot 4.1.x / Spring Cloud 2025.1.x / Java 17+（编译目标 17，可运行于 17~25）

## 特性

- **三后端合一**（`autumn.discovery.backend` 三选一互斥装配，Spring 条件加载）：
  - `consul` —— 直连 Consul HTTP API（`/v1/agent/service/register`、`/v1/health/service?passing=true`），TTL 健康检查自动续约
  - `nacos` —— 直连 Nacos OpenAPI v1（注册/心跳/实例列表），临时实例客户端心跳
  - `multicast` —— 纯 JDK UDP 组播实现，**零外部依赖**，适合开发环境/小型局域网
- **不引入注册中心 SDK**：Consul / Nacos 全部走 HTTP API，仅依赖 `apache-httpclient5` + `jackson`
- **多流量标签（traffic-label）路由**：实例注册时打标（metadata `traffic-label`），网关按 HTTP header（默认 `X-Autumn-Traffic-Label`）把流量切到对应标签实例，支持 default 兜底回落
- **轻量 & Native Image 友好**：`core` 包零 Spring 依赖（可纯 Java API 使用）、构造器注入、无反射路由、注册表全内存

## 模块

| 模块 | 说明 |
|---|---|
| `autumn-cloud-extension-starter` | 核心 starter：`core`（纯 Java：模型/流量标签选择器/HTTP 封装/组播）+ consul/nacos/multicast 三后端 + LoadBalancer 流量标签过滤 |
| `autumn-cloud-extension-starter-using` | 使用示例：Provider（双流量标签实例）+ Gateway（lb:// 路由），含 consul/nacos/multicast profile |

## 快速开始（组播，零外部依赖）

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

验证流量标签路由：

```bash
# 无 header → default 实例（8101）
curl http://127.0.0.1:8080/api/hello
# {"service":"demo-service","trafficLabel":"default","port":8101,...}

# gray 标签 → 灰度实例（8102）
curl -H "X-Autumn-Traffic-Label: gray" http://127.0.0.1:8080/api/hello
# {"service":"demo-service","trafficLabel":"gray","port":8102,...}

# 不存在的标签 → 回落 default
curl -H "X-Autumn-Traffic-Label: beta" http://127.0.0.1:8080/api/hello
```

## 接入自己的项目

```xml
<dependency>
    <groupId>com.autumn.cloud</groupId>
    <artifactId>autumn-cloud-extension-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```properties
# 服务提供方
spring.application.name=demo-service
server.port=8101
autumn.discovery.backend=consul            # consul / nacos / multicast
autumn.discovery.traffic-label=gray        # 本实例流量标签（缺省 default）

# 网关方
autumn.discovery.register=false            # 网关自身不注册
spring.cloud.gateway.server.webflux.routes[0].uri=lb://demo-service
```

完整配置表与说明见 **[docs/usage.md](docs/usage.md)**。

## 已验证

- `mvn clean package`：全模块构建 + 12 个单元测试通过
- 组播后端端到端冒烟：双标签 provider + 网关，header 切换/回落全部符合预期
- macOS 多进程共享组播端口场景（bind 组播地址 + 独立发送通道）已修复验证

## GraalVM Native Image

代码层已按 Native 友好编写（无反射路由、构造器注入、`AutoConfiguration.imports` 注册、jackson 树模型解析）。
编译 native 镜像时请在应用侧配置 `spring-boot-maven-plugin` 的 `process-aot` / `native` goal，并注意：

- jackson 对 record 的序列化依赖其自带 native metadata（jackson 2.15+ 已内置）
- 组播后端纯 `java.nio.channels.DatagramChannel`，无反射

## License

Apache License 2.0
