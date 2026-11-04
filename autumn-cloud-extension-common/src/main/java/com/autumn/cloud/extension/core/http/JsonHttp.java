package com.autumn.cloud.extension.core.http;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 基于 apache-httpclient5 的极简阻塞 HTTP 封装，满足注册中心 HTTP API 调用。
 *
 * <p>面向 GraalVM Native Image：不使用反射，客户端实例可复用，用完 {@link #close()}。</p>
 */
public final class JsonHttp implements AutoCloseable {

    private final CloseableHttpClient client;

    public JsonHttp() {
        this(3000, 5000);
    }

    public JsonHttp(int connectTimeoutMs, int responseTimeoutMs) {
        this.client = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                        .setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMs))
                        .build())
                .build();
    }

    public String get(String uri, Map<String, String> headers) throws IOException {
        return exchange("GET", uri, null, headers);
    }

    public String post(String uri, Map<String, String> headers) throws IOException {
        return exchange("POST", uri, null, headers);
    }

    /** JSON body 的 PUT；body 为 null 表示空 body。 */
    public String put(String uri, String jsonBody, Map<String, String> headers) throws IOException {
        return exchange("PUT", uri, jsonBody, headers);
    }

    public String delete(String uri, Map<String, String> headers) throws IOException {
        return exchange("DELETE", uri, null, headers);
    }

    private String exchange(String method, String uri, String jsonBody, Map<String, String> headers)
            throws IOException {
        ClassicHttpRequest request = ClassicRequestBuilder.create(method)
                .setUri(uri)
                .build();
        if (jsonBody != null) {
            request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        }
        if (headers != null) {
            headers.forEach(request::addHeader);
        }
        try (CloseableHttpResponse response = client.execute(request)) {
            int code = response.getCode();
            try {
                String payload = response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (code >= 200 && code < 300) {
                    return payload;
                }
                throw new HttpCallException(method + " " + uri + " -> " + code + ": " + truncate(payload));
            } catch (IOException | org.apache.hc.core5.http.ParseException e) {
                throw new HttpCallException(method + " " + uri + " failed: " + e.getMessage(), e);
            }
        }
    }

    private static String truncate(String value) {
        return value != null && value.length() > 500 ? value.substring(0, 500) : value;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
