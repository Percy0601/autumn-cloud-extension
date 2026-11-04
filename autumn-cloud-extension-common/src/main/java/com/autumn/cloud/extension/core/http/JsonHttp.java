package com.autumn.cloud.extension.core.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 基于 JDK 原生 HttpClient 的极简阻塞 HTTP 封装，满足注册中心/配置中心 HTTP API 调用。
 *
 * <p>JDK 11+ 内置，零外部依赖，面向 GraalVM Native Image 友好。</p>
 */
public final class JsonHttp implements AutoCloseable {

    private final HttpClient client;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public JsonHttp() {
        this(3000, 5000);
    }

    public JsonHttp(int connectTimeoutMs, int requestTimeoutMs) {
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    public String get(String uri, Map<String, String> headers) throws IOException {
        return exchange("GET", uri, null, headers);
    }

    public String post(String uri, Map<String, String> headers) throws IOException {
        return exchange("POST", uri, null, headers);
    }

    /** 带 body 的 POST */
    public String post(String uri, Map<String, String> params, Map<String, String> headers) throws IOException {
        String body = params != null ? buildFormBody(params) : null;
        return exchange("POST", uri, body, headers);
    }

    /** JSON body 的 PUT；body 为 null 表示空 body。 */
    public String put(String uri, String jsonBody, Map<String, String> headers) throws IOException {
        return exchange("PUT", uri, jsonBody, headers);
    }

    public String delete(String uri, Map<String, String> headers) throws IOException {
        return exchange("DELETE", uri, null, headers);
    }

    private String exchange(String method, String uri, String body, Map<String, String> headers)
            throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(requestTimeout);

        // 设置 headers
        if (headers != null) {
            headers.forEach(builder::header);
        }

        // 设置 method + body
        HttpRequest.BodyPublisher publisher = body != null
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();

        builder.method(method, publisher);

        // 如果有 body 且没设置 Content-Type，默认 JSON
        if (body != null && (headers == null || !containsIgnoreCase(headers, "Content-Type"))) {
            builder.header("Content-Type", "application/json");
        }

        try {
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            int code = response.statusCode();
            String payload = response.body();

            if (code >= 200 && code < 300) {
                return payload;
            }
            throw new HttpCallException(method + " " + uri + " -> " + code + ": " + truncate(payload));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(method + " " + uri + " interrupted", e);
        } catch (IOException e) {
            throw new HttpCallException(method + " " + uri + " failed: " + e.getMessage(), e);
        }
    }

    private static String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('&');
            sb.append(java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8))
              .append('=')
              .append(java.net.URLEncoder.encode(v != null ? v : "", java.nio.charset.StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private static boolean containsIgnoreCase(Map<String, String> map, String key) {
        return map.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(key));
    }

    private static String truncate(String value) {
        return value != null && value.length() > 500 ? value.substring(0, 500) : value;
    }

    @Override
    public void close() {
        // JDK HttpClient 不需要显式关闭，内部连接池会自动回收
    }
}
