package xyz.wewin.autumn.cloud.extension.core.http;

/**
 * HTTP 调用异常（状态码非 2xx 或 IO 失败）。
 */
public class HttpCallException extends RuntimeException {

    public HttpCallException(String message) {
        super(message);
    }

    public HttpCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
