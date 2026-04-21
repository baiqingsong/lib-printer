package com.dawn.tcp.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * Simple length-prefixed framing:
 * [int32 length][payload bytes...]
 * length == 0 is allowed.
 */
final class TcpFraming {
    private TcpFraming() {}

    static void writeFrame(DataOutputStream out, byte[] payload) throws IOException {
        int len = payload == null ? 0 : payload.length;
        out.writeInt(len);
        if (len > 0) {
            out.write(payload);
        }
        out.flush();
    }

    static byte[] readFrame(DataInputStream in, int maxFrameSize) throws IOException {
        int len;
        try {
            len = in.readInt();
        } catch (EOFException eof) {
            return null;
        }
        if (len < 0) throw new IOException("Negative frame length: " + len);
        if (maxFrameSize > 0 && len > maxFrameSize) {
            throw new IOException("Frame too large: " + len + ", max=" + maxFrameSize);
        }
        byte[] buf = new byte[len];
        in.readFully(buf);
        return buf;
    }
}

