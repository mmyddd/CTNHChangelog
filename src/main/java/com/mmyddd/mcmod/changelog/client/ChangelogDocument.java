package com.mmyddd.mcmod.changelog.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writer and v2 reader for the versioned changelog document format. */
public final class ChangelogDocument {
    public static final int FORMAT_VERSION = 2;

    private ChangelogDocument() {
    }

    public static Document parse(String json) {
        return parseV2(JsonParser.parseString(json).getAsJsonObject());
    }

    public static Document parse(JsonObject root) {
        return parseV2(root);
    }

    public static boolean isV2(JsonObject root) {
        return root != null
                && root.has("format")
                && root.get("format").isJsonPrimitive()
                && "ctnhchangelog".equals(root.get("format").getAsString())
                && root.has("formatVersion")
                && root.get("formatVersion").isJsonPrimitive()
                && root.get("formatVersion").getAsInt() == FORMAT_VERSION;
    }

    public static boolean isVersioned(JsonObject root) {
        return root != null
                && root.has("format")
                && root.get("format").isJsonPrimitive()
                && "ctnhchangelog".equals(root.get("format").getAsString());
    }

    public static Document parseV2(JsonObject root) {
        if (!isV2(root)) {
            throw new IllegalArgumentException("Not a CTNHChangelog v2 document");
        }

        String footer = "";
        LinkedHashMap<String, Integer> tagColors = new LinkedHashMap<>();

        JsonObject meta = root.has("meta") && root.get("meta").isJsonObject()
                ? root.getAsJsonObject("meta")
                : null;
        JsonObject footerSource = meta != null ? meta : root;
        if (footerSource.has("footer") && footerSource.get("footer").isJsonPrimitive()) {
            footer = footerSource.get("footer").getAsString();
        }

        JsonElement tagColorsElement = footerSource.get("tagColors");
        if (tagColorsElement != null && tagColorsElement.isJsonObject()) {
            for (var entry : tagColorsElement.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    tagColors.put(entry.getKey(), parseColor(entry.getValue().getAsString()));
                }
            }
        }

        List<EntryData> entries = new ArrayList<>();
        if (root.has("entries") && root.get("entries").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("entries")) {
                if (element.isJsonObject()) {
                    entries.add(parseEntry(element.getAsJsonObject()));
                }
            }
        }
        return new Document(footer, tagColors, entries);
    }

    private static EntryData parseEntry(JsonObject object) {
        String version = stringValue(object, "version", "1.0.0");
        String date = stringValue(object, "date", "");
        String title = stringValue(object, "title", "");

        List<String> types = stringList(object.get("types"));
        if (types.isEmpty()) {
            types.add("patch");
        }

        List<String> tags = stringList(object.get("tags"));
        String accent = stringValue(object, "accent", "#FFFFFF");

        List<ChangeNode> changes = object.has("changes")
                ? ChangeNode.fromContainer(object.get("changes"))
                : new ArrayList<>();
        return new EntryData(version, date, title, types, tags, parseColor(accent), changes);
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
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

    public static JsonObject toJson(Document document) {
        JsonObject root = new JsonObject();
        root.addProperty("format", "ctnhchangelog");
        root.addProperty("formatVersion", FORMAT_VERSION);

        JsonObject meta = new JsonObject();
        meta.addProperty("footer", document.footer);
        JsonObject tagColors = new JsonObject();
        for (var entry : document.tagColors.entrySet()) {
            tagColors.addProperty(entry.getKey(), formatColor(entry.getValue()));
        }
        meta.add("tagColors", tagColors);
        root.add("meta", meta);

        JsonArray entries = new JsonArray();
        for (EntryData entry : document.entries) {
            entries.add(entry.toJson());
        }
        root.add("entries", entries);
        return root;
    }

    public static String toPrettyJson(Document document) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(toJson(document));
    }

    public static int parseColor(String color) {
        if (color == null) {
            return 0xFFFFFFFF;
        }
        try {
            String value = color.trim();
            if (value.startsWith("0x") || value.startsWith("0X")) {
                value = value.substring(2);
            } else if (value.startsWith("#")) {
                value = value.substring(1);
            }
            if (value.length() == 6) {
                return (int) Long.parseLong("FF" + value, 16);
            }
            if (value.length() == 8) {
                return (int) Long.parseLong(value, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFFFFFFFF;
    }

    public static String formatColor(int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }

    public static final class Document {
        public String footer;
        public final LinkedHashMap<String, Integer> tagColors;
        public final List<EntryData> entries;

        public Document(String footer, Map<String, Integer> tagColors, List<EntryData> entries) {
            this.footer = footer == null ? "" : footer;
            this.tagColors = new LinkedHashMap<>();
            if (tagColors != null) {
                this.tagColors.putAll(tagColors);
            }
            this.entries = new ArrayList<>(entries == null ? List.of() : entries);
        }
    }

    public static final class EntryData {
        public String version;
        public String date;
        public String title;
        public final List<String> types;
        public final List<String> tags;
        public int accent;
        public final List<ChangeNode> changes;

        public EntryData(String version, String date, String title, List<String> types,
                         List<String> tags, int accent, List<ChangeNode> changes) {
            this.version = version == null ? "1.0.0" : version;
            this.date = date == null ? "" : date;
            this.title = title == null ? "" : title;
            this.types = new ArrayList<>(types == null ? List.of() : types);
            this.tags = new ArrayList<>(tags == null ? List.of() : tags);
            this.accent = accent;
            this.changes = new ArrayList<>();
            if (changes != null) {
                for (ChangeNode change : changes) {
                    this.changes.add(change.copy());
                }
            }
        }

        public JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("version", version);
            object.addProperty("date", date);
            object.addProperty("title", title);

            JsonArray typesArray = new JsonArray();
            for (String type : types) {
                typesArray.add(type);
            }
            object.add("types", typesArray);

            JsonArray tagsArray = new JsonArray();
            for (String tag : tags) {
                tagsArray.add(tag);
            }
            object.add("tags", tagsArray);
            object.addProperty("accent", formatColor(accent));

            JsonArray changesArray = new JsonArray();
            for (ChangeNode change : changes) {
                changesArray.add(change.toJson());
            }
            object.add("changes", changesArray);
            return object;
        }
    }
}
