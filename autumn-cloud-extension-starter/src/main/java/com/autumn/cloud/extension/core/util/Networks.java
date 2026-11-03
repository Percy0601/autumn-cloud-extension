package com.autumn.cloud.extension.core.util;

import java.net.InetAddress;

/**
 * 网络工具。
 */
public final class Networks {

    private Networks() {
    }

    /** 本机 IP，探测失败回退 127.0.0.1。 */
    public static String localHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
