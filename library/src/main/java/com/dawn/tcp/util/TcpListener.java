package com.dawn.tcp.util;

import java.net.InetSocketAddress;

/** Internal listener for TCP connection events. */
public interface TcpListener {
    default void onConnecting(InetSocketAddress remote) {}

    default void onConnected(InetSocketAddress remote) {}

    default void onDisconnected(InetSocketAddress remote, Exception cause) {}

    default void onMessage(InetSocketAddress remote, byte[] payload) {}

    default void onError(InetSocketAddress remote, Exception error) {}
}
