package com.autumn.cloud.extension.core.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 查询串工具。
 */
public final class QueryStrings {

    private QueryStrings() {
    }

    /** 将参数编码为 {@code a=1&b=2} 形式的查询串。 */
    public static String encode(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
