package com.dawn.tcp.util;

import com.dawn.tcp.TcpConfig;

/**
 * Index helper: references key types to help IDE/PSI rebuild after file corruption.
 * Not used at runtime.
 */
final class TcpUtilIndex {
    private TcpUtilIndex() {}

    static Class<?>[] refs() {
        return new Class<?>[] { TcpConfig.class, TcpServer.class, TcpClient.class, TcpText.class, TcpListener.class };
    }
}

