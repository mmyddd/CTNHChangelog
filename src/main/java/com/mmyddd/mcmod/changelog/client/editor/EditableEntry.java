package com.mmyddd.mcmod.changelog.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mmyddd.mcmod.changelog.client.ChangelogEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EditableEntry {
    private static final String[] TYPE_ORDER = {"major", "minor", "patch", "hotfix", "danger"};

    public String version;
    public String date;
    public String title;
    public List<String> types;
    public List<String> tags;
    public int color;
    public List<String> changes;

    public EditableEntry() {
        this.version = "1.0.0";
        this.date = "";
        this.title = "";
        this.types = new ArrayList<>(List.of("patch"));
        this.tags = new ArrayList<>();
        this.color = 0xFFFFFF00;
        this.changes = new ArrayList<>();
    }

    public EditableEntry(EditableEntry other) {
        this.version = other.version;
        this.date = other.date;
        this.title = other.title;
        this.types = new ArrayList<>(other.types);
        this.tags = new ArrayList<>(other.tags);
        this.color = other.color;
        this.changes = new ArrayList<>(other.changes);
    }

    public static EditableEntry fromChangelogEntry(ChangelogEntry entry) {
        EditableEntry e = new EditableEntry();
        e.version = entry.getVersion();
        e.date = entry.getDate();
        e.title = entry.getTitle();
        e.types = new ArrayList<>(entry.getTypes());
        e.tags = new ArrayList<>(entry.getTags());
        e.color = entry.getColor();
        e.changes = new ArrayList<>(entry.getChanges());
        return e;
    }

    public void sortTypes() {
        List<String> order = Arrays.asList(TYPE_ORDER);
        types.sort((a, b) -> {
            int ia = order.indexOf(a);
            int ib = order.indexOf(b);
            if (ia < 0) ia = 999;
            if (ib < 0) ib = 999;
            return Integer.compare(ia, ib);
        });
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", version);
        obj.addProperty("date", date);
        obj.addProperty("title", title);

        JsonArray typeArray = new JsonArray();
        for (String t : types) {
            typeArray.add(t);
        }
        obj.add("type", typeArray);

        JsonArray tagsArray = new JsonArray();
        for (String t : tags) {
            tagsArray.add(t);
        }
        obj.add("tags", tagsArray);

        obj.addProperty("color", String.format("0xFF%06X", color & 0x00FFFFFF));

        JsonArray changesArray = new JsonArray();
        for (String c : changes) {
            changesArray.add(c);
        }
        obj.add("changes", changesArray);

        return obj;
    }
}
