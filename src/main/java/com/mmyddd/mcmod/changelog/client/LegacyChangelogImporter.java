package com.mmyddd.mcmod.changelog.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Imports the pre-v2 root and entry fields without coupling them to v2 writing. */
public final class LegacyChangelogImporter {
    private LegacyChangelogImporter() {
    }

    public static ChangelogDocument.Document importDocument(JsonObject root) {
        String footer = primitiveString(root, "footer", "");
        LinkedHashMap<String, Integer> tagColors = new LinkedHashMap<>();
        if (root.has("tagColors") && root.get("tagColors").isJsonObject()) {
            for (var entry : root.getAsJsonObject("tagColors").entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    tagColors.put(entry.getKey(), ChangelogDocument.parseColor(entry.getValue().getAsString()));
                }
            }
        }

        List<ChangelogDocument.EntryData> entries = new ArrayList<>();
        if (root.has("entries") && root.get("entries").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("entries")) {
                if (element.isJsonObject()) {
                    entries.add(importEntry(element.getAsJsonObject()));
                }
            }
        }
        return new ChangelogDocument.Document(footer, tagColors, entries);
    }

    private static ChangelogDocument.EntryData importEntry(JsonObject object) {
        String version = primitiveString(object, "version", "1.0.0");
        String date = primitiveString(object, "date", "");
        String title = primitiveString(object, "title", "");

        JsonElement typeElement = object.has("types") ? object.get("types") : object.get("type");
        List<String> types = stringList(typeElement);
        if (types.isEmpty()) {
            types.add("patch");
        }

        List<String> tags = stringList(object.has("tags") ? object.get("tags") : object.get("tag"));
        String color = primitiveString(object,
                object.has("accent") ? "accent" : "color",
                "#FFFFFF");
        List<ChangeNode> changes = object.has("changes")
                ? ChangeNode.fromContainer(object.get("changes"))
                : new ArrayList<>();

        return new ChangelogDocument.EntryData(
                version,
                date,
                title,
                types,
                tags,
                ChangelogDocument.parseColor(color),
                changes
        );
    }

    private static String primitiveString(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString()
                : fallback;
    }

    private static List<String> stringList(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    values.add(child.getAsString());
                }
            }
        } else if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
        }
        return values;
    }
}
