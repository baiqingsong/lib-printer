package com.dawn.tcp.util;

import com.dawn.tcp.TcpConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** A single TCP connection (internal implementation). */
final class TcpConnection {

    private static final int DEFAULT_MAX_FRAME_SIZE = 4 * 1024 * 1024;

    private final Socket socket;
    private final InetSocketAddress remote;
    private final TcpConfig config;
    private final TcpListener listener;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object writeLock = new Object();

    private DataInputStream in;
    private DataOutputStream out;

    private volatile long lastReceiveAtMs;

    private ExecutorService ioExecutor;
    private ScheduledExecutorService scheduler;

    TcpConnection(Socket socket, TcpConfig config, TcpListener listener) throws IOException {
        this.socket = socket;
        this.remote = (InetSocketAddress) socket.getRemoteSocketAddress();
        this.config = config == null ? new TcpConfig() : config.copy();
        this.listener = listener;

        socket.setTcpNoDelay(this.config.tcpNoDelay);
        socket.setKeepAlive(this.config.keepAlive);
        socket.setSoTimeout(this.config.readTimeoutMs);

        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.lastReceiveAtMs = System.currentTimeMillis();
    }

    InetSocketAddress remote() {
        return remote;
    }

    void start() {
        ioExecutor = Executors.newSingleThreadExecutor(namedFactory("tcp-io-" + remote.getPort()));
        scheduler = Executors.newSingleThreadScheduledExecutor(namedFactory("tcp-hb-" + remote.getPort()));

        if (listener != null) listener.onConnected(remote);

        if (config.heartbeatIntervalMs > 0) {
            scheduler.scheduleAtFixedRate(() -> {
                if (closed.get()) return;
                try {
                    if (config.readTimeoutMs > 0 && config.idleTimeoutMs > 0) {
                        long idle = System.currentTimeMillis() - lastReceiveAtMs;
                        if (idle > config.idleTimeoutMs) {
                            close(new IOException("Idle timeout: " + idle + "ms"));
                            return;
                        }
                    }
                    // send heartbeat
                    send(config.resolveHeartbeatBytes());
                } catch (Exception e) {
                    close(e);
                }
            }, config.heartbeatIntervalMs, config.heartbeatIntervalMs, TimeUnit.MILLISECONDS);
        }

        ioExecutor.execute(() -> {
            Exception cause = null;
            try {
                if (config.protocolMode == TcpConfig.ProtocolMode.RAW_JSON_STREAM) {
                    readRawJsonStream();
                } else {
                    readLengthPrefixedFrames();
                }
            } catch (Exception e) {
                cause = e;
            } finally {
                // notify once
                if (closed.compareAndSet(false, true)) {
                    closeInternal();
                    if (listener != null) listener.onDisconnected(remote, cause);
                } else {
                    closeInternal();
                }
            }
        });
    }

    private void readLengthPrefixedFrames() throws IOException {
        while (!closed.get()) {
            byte[] frame = TcpFraming.readFrame(in, DEFAULT_MAX_FRAME_SIZE);
            if (frame == null) break;
            lastReceiveAtMs = System.currentTimeMillis();
            if (frame.length == 0) continue;
            if (listener != null) listener.onMessage(remote, frame);
        }
    }

    private void readRawJsonStream() throws IOException {
        // NOTE: DataInputStream wraps the same underlying InputStream.
        InputStream rawIn = in;
        JsonStreamDecoder decoder = new JsonStreamDecoder(StandardCharsets.UTF_8,
                config.maxJsonMessageBytes > 0 ? config.maxJsonMessageBytes : DEFAULT_MAX_FRAME_SIZE);

        byte[] buf = new byte[8 * 1024];
        int read;
        while (!closed.get() && (read = rawIn.read(buf)) != -1) {
            lastReceiveAtMs = System.currentTimeMillis();
            if (read <= 0) continue;

            decoder.append(buf, 0, read, json -> {
                if (json == null) return;
                byte[] payload = json.getBytes(StandardCharsets.UTF_8);
                if (payload.length == 0) return;
                if (listener != null) listener.onMessage(remote, payload);
            });
        }
    }

    void send(byte[] payload) throws IOException {
        if (closed.get()) throw new IOException("Connection closed");
        synchronized (writeLock) {
            if (closed.get()) throw new IOException("Connection closed");
            if (config.protocolMode == TcpConfig.ProtocolMode.RAW_JSON_STREAM) {
                // Raw stream: just write bytes through (no length header).
                if (payload != null && payload.length > 0) {
                    out.write(payload);
                }
                out.flush();
            } else {
                // Legacy framing.
                TcpFraming.writeFrame(out, payload);
            }
        }
    }

    void close(Exception cause) {
        if (!closed.compareAndSet(false, true)) return;
        closeInternal();
        if (listener != null) listener.onDisconnected(remote, cause);
    }

    void close() {
        close(null);
    }

    boolean isClosed() {
        return closed.get();
    }

    private void closeInternal() {
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
            ioExecutor = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        synchronized (writeLock) {
            if (out != null) {
                try { out.flush(); } catch (Exception ignore) {}
            }
        }

        if (in != null) {
            try { in.close(); } catch (Exception ignore) {}
            in = null;
        }
        if (out != null) {
            try { out.close(); } catch (Exception ignore) {}
            out = null;
        }
        try { socket.close(); } catch (Exception ignore) {}
    }

    private static ThreadFactory namedFactory(String prefix) {
        return r -> {
            Thread t = new Thread(r);
            t.setName(prefix);
            t.setDaemon(true);
            return t;
        };
    }
}
