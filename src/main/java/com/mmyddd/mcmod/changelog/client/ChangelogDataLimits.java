package com.mmyddd.mcmod.changelog.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ChangelogDataLimits {
    public static final int MAX_DOCUMENT_BYTES = 1_048_576;
    public static final int MAX_JSON_NESTING = 64;
    public static final int MAX_CHANGE_TREE_DEPTH = 32;
    public static final int MAX_CHANGE_TREE_NODES = 4_096;

    private ChangelogDataLimits() {
    }

    public static void validateContentLength(long contentLength) {
        if (contentLength > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Changelog document exceeds the maximum size");
        }
    }

    public static byte[] readDocument(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");

        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(8_192, MAX_DOCUMENT_BYTES));
        byte[] buffer = new byte[8_192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            if (output.size() > MAX_DOCUMENT_BYTES - bytesRead) {
                throw new IllegalArgumentException("Changelog document exceeds the maximum size");
            }
            output.write(buffer, 0, bytesRead);
        }

        byte[] data = output.toByteArray();
        validateJsonPayload(data);
        return data;
    }

    public static void validateJsonPayload(String json) {
        Objects.requireNonNull(json, "json");
        validateJsonPayload(json.getBytes(StandardCharsets.UTF_8));
    }

    public static void validateJsonPayload(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Changelog document exceeds the maximum size");
        }

        boolean inString = false;
        boolean escaped = false;
        int nesting = 0;
        for (byte value : data) {
            int character = value & 0xFF;
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }

            if (character == '"') {
                inString = true;
            } else if (character == '{' || character == '[') {
                nesting++;
                if (nesting > MAX_JSON_NESTING) {
                    throw new IllegalArgumentException("Changelog JSON exceeds the maximum nesting depth");
                }
            } else if (character == '}' || character == ']') {
                nesting--;
                if (nesting < 0) {
                    throw new IllegalArgumentException("Changelog JSON has unbalanced containers");
                }
            }
        }

        if (inString || nesting != 0) {
            throw new IllegalArgumentException("Changelog JSON has unbalanced containers");
        }
    }
}