package com.mmyddd.mcmod.changelog.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A node in the ordered recursive changelog outline. */
public final class ChangeNode {
    private final String text;
    private final String title;
    private final List<ChangeNode> children;

    private ChangeNode(String text, String title, List<ChangeNode> children) {
        this.text = text;
        this.title = title;
        this.children = children == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(children));
    }

    public static ChangeNode bullet(String text) {
        return new ChangeNode(text == null ? "" : text, null, List.of());
    }

    public static ChangeNode heading(String title, List<ChangeNode> children) {
        return new ChangeNode(null, title == null ? "" : title, children);
    }

    public boolean isHeading() {
        return title != null;
    }

    public String getText() {
        return text == null ? "" : text;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public List<ChangeNode> getChildren() {
        return children;
    }

    public ChangeNode copy() {
        List<ChangeNode> copiedChildren = new ArrayList<>();
        for (ChangeNode child : children) {
            copiedChildren.add(child.copy());
        }
        return isHeading() ? heading(title, copiedChildren) : bullet(text);
    }

    public int countBullets() {
        if (!isHeading()) {
            return 1;
        }

        int count = 0;
        for (ChangeNode child : children) {
            count += child.countBullets();
        }
        return count;
    }

    public String firstText() {
        if (!isHeading()) {
            return getText();
        }
        for (ChangeNode child : children) {
            String text = child.firstText();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return getTitle();
    }

    public JsonElement toJson() {
        if (!isHeading()) {
            return new JsonPrimitive(getText());
        }

        JsonObject object = new JsonObject();
        object.addProperty("title", getTitle());
        JsonArray childArray = new JsonArray();
        for (ChangeNode child : children) {
            childArray.add(child.toJson());
        }
        object.add("children", childArray);
        return object;
    }

    public static ChangeNode fromJson(JsonElement element) {
        return fromJson(element, new ParseBudget(), 1);
    }

    private static ChangeNode fromJson(JsonElement element, ParseBudget budget, int depth) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            budget.registerNode(depth);
            return bullet(element.getAsString());
        }

        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("text") && object.get("text").isJsonPrimitive()) {
            budget.registerNode(depth);
            return bullet(object.get("text").getAsString());
        }

        if (!object.has("title") || !object.get("title").isJsonPrimitive()) {
            return null;
        }

        budget.registerNode(depth);
        List<ChangeNode> children = new ArrayList<>();
        if (object.has("children")) {
            children.addAll(fromContainer(object.get("children"), budget, depth));
        }
        return heading(object.get("title").getAsString(), children);
    }

    public static List<ChangeNode> fromContainer(JsonElement element) {
        return fromContainer(element, new ParseBudget(), 0);
    }

    private static List<ChangeNode> fromContainer(JsonElement element, ParseBudget budget, int parentDepth) {
        List<ChangeNode> nodes = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return nodes;
        }

        if (element.isJsonArray()) {
            for (JsonElement childElement : element.getAsJsonArray()) {
                ChangeNode child = fromJson(childElement, budget, parentDepth + 1);
                if (child != null) {
                    nodes.add(child);
                }
            }
            return nodes;
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("title") || object.has("text")) {
                ChangeNode node = fromJson(object, budget, parentDepth + 1);
                if (node != null) {
                    nodes.add(node);
                }
                return nodes;
            }

            for (var entry : object.entrySet()) {
                int depth = parentDepth + 1;
                budget.registerNode(depth);
                nodes.add(heading(entry.getKey(), fromContainer(entry.getValue(), budget, depth)));
            }
        }
        return nodes;
    }

    public static List<ChangeNode> fromJsonArray(JsonArray array) {
        return fromContainer(array);
    }

    private static final class ParseBudget {
        private int nodeCount;

        private void registerNode(int depth) {
            if (depth > ChangelogDataLimits.MAX_CHANGE_TREE_DEPTH) {
                throw new IllegalArgumentException("Changelog change tree exceeds the maximum depth");
            }
            nodeCount++;
            if (nodeCount > ChangelogDataLimits.MAX_CHANGE_TREE_NODES) {
                throw new IllegalArgumentException("Changelog change tree exceeds the maximum node count");
            }
        }
    }
}