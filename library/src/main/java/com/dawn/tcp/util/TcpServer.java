package com.dawn.tcp.util;

import com.dawn.tcp.TcpConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/** TCP server (internal). Single-client mode by default. */
public final class TcpServer {

    private final int port;
    private final TcpConfig config;
    private final TcpListener listener;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private ExecutorService acceptExecutor;
    private volatile ServerSocket serverSocket;

    private final Object connLock = new Object();
    private volatile TcpConnection currentConn;

    public TcpServer(int port, TcpConfig config, TcpListener listener) {
        this.port = port;
        this.config = config == null ? new TcpConfig() : config.copy();
        this.listener = listener;
    }

    public void start() throws IOException {
        if (acceptExecutor != null) return;
        stopped.set(false);

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));

        acceptExecutor = Executors.newSingleThreadExecutor(namedFactory("tcp-server-accept"));
        acceptExecutor.execute(this::acceptLoop);
    }

    public void stop() {
        stopped.set(true);

        if (acceptExecutor != null) {
            acceptExecutor.shutdownNow();
            acceptExecutor = null;
        }

        ServerSocket ss = serverSocket;
        serverSocket = null;
        if (ss != null) {
            try { ss.close(); } catch (Exception ignore) {}
        }

        TcpConnection c;
        synchronized (connLock) {
            c = currentConn;
            currentConn = null;
        }
        if (c != null) c.close();
    }

    public boolean hasClient() {
        TcpConnection c = currentConn;
        return c != null && !c.isClosed();
    }

    public void sendToClient(byte[] payload) throws IOException {
        TcpConnection c = currentConn;
        if (c == null) throw new IOException("No client connected");
        c.send(payload);
    }

    private void acceptLoop() {
        while (!stopped.get()) {
            try {
                ServerSocket ss = Objects.requireNonNull(serverSocket, "serverSocket");
                Socket client = ss.accept();

                TcpConnection old;
                synchronized (connLock) {
                    old = currentConn;
                }
                if (old != null) old.close(new IOException("Replaced by new client"));

                TcpConnection conn = new TcpConnection(client, config, new Forwarding());
                synchronized (connLock) {
                    currentConn = conn;
                }
                conn.start();
            } catch (Exception e) {
                if (stopped.get()) break;
                if (listener != null) listener.onError(null, e);
                try { Thread.sleep(300); } catch (InterruptedException ie) { break; }
            }
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
                TcpConnection c = currentConn;
                if (c != null && c.remote().equals(remote)) {
                    currentConn = null;
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
