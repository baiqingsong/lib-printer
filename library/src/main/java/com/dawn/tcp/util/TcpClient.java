package com.dawn.tcp.util;

import com.dawn.tcp.TcpConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/** TCP client with auto-reconnect (internal). */
public final class TcpClient {

    private final InetSocketAddress remote;
    private final TcpConfig config;
    private final TcpListener listener;

    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final Object connLock = new Object();

    private ExecutorService connectExecutor;
    private volatile TcpConnection connection;

    public TcpClient(InetSocketAddress remote, TcpConfig config, TcpListener listener) {
        this.remote = Objects.requireNonNull(remote, "remote");
        this.config = config == null ? new TcpConfig() : config.copy();
        this.listener = listener;
    }

    public void start() {
        if (connectExecutor != null) return;
        stopped.set(false);
        connectExecutor = Executors.newSingleThreadExecutor(namedFactory("tcp-client-connector"));
        connectExecutor.execute(this::connectLoop);
    }

    public void stop() {
        stopped.set(true);
        if (connectExecutor != null) {
            connectExecutor.shutdownNow();
            connectExecutor = null;
        }
        TcpConnection c;
        synchronized (connLock) {
            c = connection;
            connection = null;
        }
        if (c != null) c.close();
    }

    public boolean isConnected() {
        TcpConnection c = connection;
        return c != null && !c.isClosed();
    }

    public void send(byte[] payload) throws IOException {
        TcpConnection c = connection;
        if (c == null) throw new IOException("Not connected");
        c.send(payload);
    }

    private void connectLoop() {
        long delay = config.reconnectBaseDelayMs;
        while (!stopped.get()) {
            try {
                if (listener != null) listener.onConnecting(remote);

                Socket socket = new Socket();
                socket.connect(remote, config.connectTimeoutMs);

                TcpConnection conn = new TcpConnection(socket, config, new Forwarding());
                synchronized (connLock) {
                    connection = conn;
                }

                delay = config.reconnectBaseDelayMs;

                conn.start();

                while (!stopped.get() && !conn.isClosed()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            } catch (Exception e) {
                if (listener != null) listener.onDisconnected(remote, e);
            } finally {
                synchronized (connLock) {
                    if (connection != null && connection.isClosed()) {
                        connection = null;
                    }
                }
            }

            if (stopped.get()) break;

            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
                break;
            }
            delay = Math.min(config.reconnectMaxDelayMs, Math.max(config.reconnectBaseDelayMs, delay * 2));
        }
    }

    private final class Forwarding implements TcpListener {
        @Override
        public void onConnected(InetSocketAddress remote) {
            if (listener != null) listener.onConnected(remote);
        }

        @Override
        public void onDisconnected(InetSocketAddress remote, Exception cause) {
            synchronized (connLock) {
                TcpConnection c = connection;
                if (c != null && c.remote().equals(remote)) {
                    connection = null;
                }
            }
            if (listener != null) listener.onDisconnected(remote, cause);
        }

        @Override
        public void onMessage(InetSocketAddress remote, byte[] payload) {
            if (listener != null) listener.onMessage(remote, payload);
        }

        @Override
        public void onError(InetSocketAddress remote, Exception error) {
            if (listener != null) listener.onError(remote, error);
        }
    }

    private static ThreadFactory namedFactory(String name) {
        return r -> {
            Thread t = new Thread(r);
            t.setName(name);
            t.setDaemon(true);
            return t;
        };
    }
}
