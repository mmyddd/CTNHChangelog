package com.mmyddd.mcmod.changelog.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mmyddd.mcmod.changelog.client.ChangeNode;
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
    public List<EditableChangeNode> changeTree;
    /** Kept as a compatibility view for the old tab class while it is being replaced. */
    public List<String> changes;
    public boolean allowEmptyTypes;

    public EditableEntry() {
        this.version = "1.0.0";
        this.date = "";
        this.title = "";
        this.types = new ArrayList<>(List.of("patch"));
        this.tags = new ArrayList<>();
        this.color = 0xFFFFFF00;
        this.changeTree = new ArrayList<>();
        this.changes = new ArrayList<>();
    }

    public EditableEntry(EditableEntry other) {
        this.version = other.version;
        this.date = other.date;
        this.title = other.title;
        this.types = new ArrayList<>(other.types);
        this.tags = new ArrayList<>(other.tags);
        this.color = other.color;
        this.changeTree = new ArrayList<>();
        for (EditableChangeNode node : other.changeTree) {
            this.changeTree.add(node.copy());
        }
        this.changes = new ArrayList<>(other.changes);
        this.allowEmptyTypes = other.allowEmptyTypes;
    }

    public static EditableEntry fromChangelogEntry(ChangelogEntry entry) {
        EditableEntry editable = new EditableEntry();
        editable.version = entry.getVersion();
        editable.date = entry.getDate();
        editable.title = entry.getTitle();
        editable.types = new ArrayList<>(entry.getTypes());
        editable.tags = new ArrayList<>(entry.getTags());
        editable.color = entry.getColor();
        for (ChangeNode node : entry.getChangeTree()) {
            editable.changeTree.add(EditableChangeNode.fromChangeNode(node));
        }
        editable.syncLegacyChanges();
        return editable;
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

    public void syncLegacyChanges() {
        changes.clear();
        for (EditableChangeNode node : changeTree) {
            flattenCompatibility(node, changes, 0);
        }
    }

    private static void flattenCompatibility(EditableChangeNode node, List<String> target, int depth) {
        if (!node.isHeading()) {
            target.add("  ".repeat(depth) + (node.text == null ? "" : node.text));
            return;
        }
        int headingLevel = depth + 1;
        target.add("#".repeat(headingLevel) + " " + (node.title == null ? "" : node.title));
        for (EditableChangeNode child : node.children) {
            flattenCompatibility(child, target, depth + 1);
        }
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("version", version);
        object.addProperty("date", date);
        object.addProperty("title", title);

        JsonArray typeArray = new JsonArray();
        for (String type : types) {
            typeArray.add(type);
        }
        object.add("types", typeArray);

        JsonArray tagsArray = new JsonArray();
        for (String tag : tags) {
            tagsArray.add(tag);
        }
        object.add("tags", tagsArray);
        object.addProperty("accent", String.format("#%06X", color & 0x00FFFFFF));

        JsonArray changesArray = new JsonArray();
        for (EditableChangeNode node : changeTree) {
            changesArray.add(node.toChangeNode().toJson());
        }
        object.add("changes", changesArray);
        return object;
    }
}
