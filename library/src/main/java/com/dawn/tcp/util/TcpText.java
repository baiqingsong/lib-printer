package com.dawn.tcp.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Small helpers for sending/receiving UTF-8 (or custom charset) text frames. */
public final class TcpText {

    private TcpText() {}

    public static byte[] encode(String text) {
        return encode(text, StandardCharsets.UTF_8);
    }

    public static byte[] encode(String text, Charset charset) {
        if (text == null) text = "";
        return text.getBytes(charset == null ? StandardCharsets.UTF_8 : charset);
    }

    public static String decode(byte[] payload) {
        return decode(payload, StandardCharsets.UTF_8);
    }

    public static String decode(byte[] payload, Charset charset) {
        if (payload == null) return "";
        return new String(payload, charset == null ? StandardCharsets.UTF_8 : charset);
    }
}
