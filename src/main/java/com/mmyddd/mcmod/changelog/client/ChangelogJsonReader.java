package com.mmyddd.mcmod.changelog.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Chooses the versioned reader or the isolated legacy importer. */
public final class ChangelogJsonReader {
    private ChangelogJsonReader() {
    }

    public static ChangelogDocument.Document read(String json) {
        ChangelogDataLimits.validateJsonPayload(json);
        return read(JsonParser.parseString(json).getAsJsonObject());
    }

    public static ChangelogDocument.Document read(InputStream input) throws IOException {
        byte[] data = ChangelogDataLimits.readDocument(input);
        return read(JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    public static ChangelogDocument.Document read(JsonObject root) {
        if (ChangelogDocument.isVersioned(root)) {
            if (!ChangelogDocument.isV2(root)) {
                String version = root.has("formatVersion") && root.get("formatVersion").isJsonPrimitive()
                        ? root.get("formatVersion").getAsString()
                        : "missing";
                throw new IllegalArgumentException("Unsupported CTNHChangelog formatVersion: " + version);
            }
            return ChangelogDocument.parseV2(root);
        }
        return LegacyChangelogImporter.importDocument(root);
    }
}