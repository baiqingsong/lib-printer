package com.dawn.tcp;

import java.nio.charset.StandardCharsets;

/**
 * TCP configuration for both client and server.
 */
public final class TcpConfig {

    /**
     * Wire protocol mode.
     * <p>
     * - LENGTH_PREFIXED: legacy framing: [int32 len][payload...]
     * - RAW_JSON_STREAM: peer sends plain UTF-8 JSON text stream (no length header). Receiver will extract
     *   complete JSON objects/arrays from the stream.
     */
    public enum ProtocolMode {
        LENGTH_PREFIXED,
        RAW_JSON_STREAM
    }

    /** Default protocol is legacy length-prefixed framing for backward compatibility. */
    public ProtocolMode protocolMode = ProtocolMode.LENGTH_PREFIXED;

    /** Max bytes allowed for a single decoded JSON message in RAW_JSON_STREAM mode (default 4MB). */
    public int maxJsonMessageBytes = 4 * 1024 * 1024;

    /** Connect timeout for client connect(). */
    public int connectTimeoutMs = 5_000;

    /** Socket read timeout. 0 means infinite block; >0 enables timeout to detect dead peers. */
    public int readTimeoutMs = 15_000;

    /** TCP keep-alive at OS level. */
    public boolean keepAlive = true;

    /** Disable Nagle for lower latency on small packets. */
    public boolean tcpNoDelay = true;

    /** Heartbeat interval; 0 disables heartbeat. */
    public long heartbeatIntervalMs = 5_000;

    /**
     * If no bytes are received for this duration, treat as disconnected.
     * Should be >= readTimeoutMs, only meaningful when readTimeoutMs > 0.
     */
    public long idleTimeoutMs = 30_000;

    /** Client reconnect base delay. */
    public long reconnectBaseDelayMs = 1_000;

    /** Client reconnect max delay. */
    public long reconnectMaxDelayMs = 15_000;

    /** Heartbeat text. For JSON protocol a fixed non-JSON string is recommended. */
    public String heartbeatText = "PING";

    /**
     * Internal heartbeat payload bytes.
     * If you set this directly, it takes precedence over heartbeatText.
     */
    public byte[] heartbeatPayload = null;

    public TcpConfig copy() {
        TcpConfig c = new TcpConfig();
        c.protocolMode = protocolMode;
        c.maxJsonMessageBytes = maxJsonMessageBytes;
        c.connectTimeoutMs = connectTimeoutMs;
        c.readTimeoutMs = readTimeoutMs;
        c.keepAlive = keepAlive;
        c.tcpNoDelay = tcpNoDelay;
        c.heartbeatIntervalMs = heartbeatIntervalMs;
        c.idleTimeoutMs = idleTimeoutMs;
        c.reconnectBaseDelayMs = reconnectBaseDelayMs;
        c.reconnectMaxDelayMs = reconnectMaxDelayMs;
        c.heartbeatText = heartbeatText;
        c.heartbeatPayload = heartbeatPayload;
        return c;
    }

    /** Resolve heartbeat bytes used on the wire. */
    public byte[] resolveHeartbeatBytes() {
        if (heartbeatPayload != null) return heartbeatPayload;
        if (heartbeatText == null) return "PING".getBytes(StandardCharsets.UTF_8);
        return heartbeatText.getBytes(StandardCharsets.UTF_8);
    }
}
