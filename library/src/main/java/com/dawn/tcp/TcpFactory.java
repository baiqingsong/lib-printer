package com.dawn.tcp;

import com.dawn.tcp.util.TcpClient;
import com.dawn.tcp.util.TcpListener;
import com.dawn.tcp.util.TcpServer;
import com.dawn.tcp.util.TcpText;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * TCP facade (ONLY public entry of this module).
 *
 * Protocol:
 * - Length-prefixed frames.
 * - Application payload is UTF-8 JSON string.
 * - Heartbeat sends a fixed string (default: "PING").
 */
public final class TcpFactory {

    private TcpFactory() {}

    /** Opaque handle for either client or server connection. */
    public interface Handle {
        boolean isConnected();

        void sendBytes(byte[] payload) throws IOException;

        void sendString(String text) throws IOException;

        void sendString(String text, Charset charset) throws IOException;

        void stop();
    }

    /** JSON/text oriented callbacks (UTF-8). */
    public interface JsonCallbacks {
        default void onConnecting(InetSocketAddress remote) {}

        default void onConnected(InetSocketAddress remote) {}

        default void onDisconnected(InetSocketAddress remote, Exception cause) {}

        /** jsonText is a UTF-8 JSON string. */
        default void onJsonMessage(InetSocketAddress remote, String jsonText) {}

        default void onError(InetSocketAddress remote, Exception error) {}
    }

    // -------- Server --------

    public static Handle startJsonServer(int port, TcpConfig config, JsonCallbacks callbacks) throws IOException {
        Objects.requireNonNull(callbacks, "callbacks");
        TcpConfig cfg = config == null ? new TcpConfig() : config;
        TcpListener listener = adaptJson(cfg, callbacks);
        TcpServer server = new TcpServer(port, cfg, listener);
        server.start();
        return new ServerHandle(server);
    }

    public static Handle startJsonServer(int port, JsonCallbacks callbacks) throws IOException {
        return startJsonServer(port, new TcpConfig(), callbacks);
    }

    // -------- Client --------

    public static Handle startJsonClient(String host, int port, TcpConfig config, JsonCallbacks callbacks) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(callbacks, "callbacks");
        TcpConfig cfg = config == null ? new TcpConfig() : config;
        TcpListener listener = adaptJson(cfg, callbacks);
        TcpClient client = new TcpClient(new InetSocketAddress(host, port), cfg, listener);
        client.start();
        return new ClientHandle(client);
    }

    public static Handle startJsonClient(String host, int port, JsonCallbacks callbacks) {
        return startJsonClient(host, port, new TcpConfig(), callbacks);
    }

    // -------- Send helpers --------

    /**
     * Send a JSON string to the remote peer.
     * @param handle the connection handle (from startJsonServer or startJsonClient)
     * @param jsonText the JSON string to send
     * @throws IOException if send fails or handle is null
     */
    public static void sendJson(Handle handle, String jsonText) throws IOException {
        if (handle == null) throw new IOException("handle is null");
        if (jsonText == null) jsonText = "";
        try {
            handle.sendString(jsonText, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            // Keep original exception as cause but add actionable context.
            throw new IOException("sendJson failed (connected=" + handle.isConnected() + ", bytes=" + jsonText.getBytes(StandardCharsets.UTF_8).length + ")", ioe);
        }
    }

    // -------- Internal adapter --------

    private static TcpListener adaptJson(TcpConfig config, JsonCallbacks callbacks) {
        return new TcpListener() {
            @Override
            public void onConnecting(InetSocketAddress remote) {
                callbacks.onConnecting(remote);
            }

            @Override
            public void onConnected(InetSocketAddress remote) {
                callbacks.onConnected(remote);
            }

            @Override
            public void onDisconnected(InetSocketAddress remote, Exception cause) {
                callbacks.onDisconnected(remote, cause);
            }

            @Override
            public void onMessage(InetSocketAddress remote, byte[] payload) {
                String text = TcpText.decode(payload, StandardCharsets.UTF_8);
                // ignore heartbeat frames
                if (config != null && config.heartbeatText != null && config.heartbeatText.equals(text)) return;
                // also ignore empty strings after decode
                if (text == null || text.trim().isEmpty()) return;
                callbacks.onJsonMessage(remote, text);
            }

            @Override
            public void onError(InetSocketAddress remote, Exception error) {
                callbacks.onError(remote, error);
            }
        };
    }

    // -------- Handle impls --------

    private static final class ClientHandle implements Handle {
        private final TcpClient client;

        private ClientHandle(TcpClient client) {
            this.client = client;
        }

        @Override
        public boolean isConnected() {
            return client.isConnected();
        }

        @Override
        public void sendBytes(byte[] payload) throws IOException {
            client.send(payload);
        }

        @Override
        public void sendString(String text) throws IOException {
            sendString(text, StandardCharsets.UTF_8);
        }

        @Override
        public void sendString(String text, Charset charset) throws IOException {
            client.send(TcpText.encode(text, charset));
        }

        @Override
        public void stop() {
            client.stop();
        }
    }

    private static final class ServerHandle implements Handle {
        private final TcpServer server;

        private ServerHandle(TcpServer server) {
            this.server = server;
        }

        @Override
        public boolean isConnected() {
            return server.hasClient();
        }

        @Override
        public void sendBytes(byte[] payload) throws IOException {
            server.sendToClient(payload);
        }

        @Override
        public void sendString(String text) throws IOException {
            sendString(text, StandardCharsets.UTF_8);
        }

        @Override
        public void sendString(String text, Charset charset) throws IOException {
            server.sendToClient(TcpText.encode(text, charset));
        }

        @Override
        public void stop() {
            server.stop();
        }
    }
}
