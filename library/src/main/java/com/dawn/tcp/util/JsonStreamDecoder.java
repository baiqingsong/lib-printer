package com.dawn.tcp.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Incremental decoder for a raw UTF-8 JSON text stream.
 * <p>
 * It extracts complete JSON values (object/array) from an incoming byte stream.
 * It supports:
 * - concatenated JSON values: {}{} or {}\n{} etc.
 * - arbitrary chunk boundaries
 * - strings/escapes inside JSON
 * <p>
 * Notes:
 * - This intentionally focuses on object/array values (most common for messaging).
 * - Leading whitespace/newlines are ignored.
 */
final class JsonStreamDecoder {

    interface Callback {
        void onJson(String jsonText);
    }

    private final Charset charset;
    private final int maxMessageBytes;

    private final StringBuilder buffer = new StringBuilder();

    JsonStreamDecoder(Charset charset, int maxMessageBytes) {
        this.charset = charset == null ? StandardCharsets.UTF_8 : charset;
        this.maxMessageBytes = maxMessageBytes <= 0 ? (4 * 1024 * 1024) : maxMessageBytes;
    }

    void append(byte[] bytes, int off, int len, Callback callback) {
        if (bytes == null || len <= 0) return;
        buffer.append(new String(bytes, off, len, charset));

        // Safety: prevent unbounded growth if peer sends garbage without JSON boundaries.
        if (buffer.length() > maxMessageBytes * 2) {
            // Keep last part only; drop old content.
            buffer.delete(0, buffer.length() - (maxMessageBytes));
        }

        List<String> out = extractAll();
        if (callback != null) {
            for (String s : out) {
                callback.onJson(s);
            }
        }
    }

    private List<String> extractAll() {
        List<String> result = new ArrayList<>();
        while (true) {
            String one = extractOne();
            if (one == null) break;
            result.add(one);
        }
        return result;
    }

    /**
     * Extract one complete JSON object/array from current buffer.
     * Returns null if not enough data.
     */
    private String extractOne() {
        int n = buffer.length();
        if (n == 0) return null;

        int i = 0;
        while (i < n && isWs(buffer.charAt(i))) i++;
        if (i >= n) {
            buffer.setLength(0);
            return null;
        }

        char start = buffer.charAt(i);
        if (start != '{' && start != '[') {
            // Not at a JSON container start. Drop until we find '{' or '['.
            int next = indexOfStart(i + 1);
            if (next < 0) {
                // Keep a small tail to avoid unlimited growth.
                if (buffer.length() > 256) buffer.delete(0, buffer.length() - 256);
            } else {
                buffer.delete(0, next);
            }
            return null;
        }

        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        for (int p = i; p < n; p++) {
            char c = buffer.charAt(p);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    int endExclusive = p + 1;
                    String json = buffer.substring(i, endExclusive);
                    if (json.getBytes(charset).length > maxMessageBytes) {
                        // Too large payload; drop it to avoid OOM.
                        buffer.delete(0, endExclusive);
                        return null;
                    }
                    buffer.delete(0, endExclusive);
                    return json;
                }
            }
        }

        return null;
    }

    private int indexOfStart(int from) {
        int n = buffer.length();
        for (int i = Math.max(0, from); i < n; i++) {
            char c = buffer.charAt(i);
            if (c == '{' || c == '[') return i;
        }
        return -1;
    }

    private static boolean isWs(char c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t';
    }
}

