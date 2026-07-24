package com.mmyddd.mcmod.changelog.client.editor;

import com.mmyddd.mcmod.changelog.client.ChangeNode;

import java.util.ArrayList;
import java.util.List;

/** Mutable recursive change node used by the editor. */
public class EditableChangeNode {
    public String text;
    public String title;
    public final List<EditableChangeNode> children = new ArrayList<>();

    public static EditableChangeNode bullet(String text) {
        EditableChangeNode node = new EditableChangeNode();
        node.text = text == null ? "" : text;
        return node;
    }

    public static EditableChangeNode heading(String title) {
        EditableChangeNode node = new EditableChangeNode();
        node.text = null;
        node.title = title == null ? "" : title;
        return node;
    }

    public boolean isHeading() {
        return title != null;
    }

    public EditableChangeNode copy() {
        EditableChangeNode copy = isHeading() ? heading(title) : bullet(text);
        for (EditableChangeNode child : children) {
            copy.children.add(child.copy());
        }
        return copy;
    }

    public ChangeNode toChangeNode() {
        if (!isHeading()) {
            return ChangeNode.bullet(text);
        }

        List<ChangeNode> childNodes = new ArrayList<>();
        for (EditableChangeNode child : children) {
            childNodes.add(child.toChangeNode());
        }
        return ChangeNode.heading(title, childNodes);
    }

    public static EditableChangeNode fromChangeNode(ChangeNode node) {
        EditableChangeNode editable = node.isHeading()
                ? heading(node.getTitle())
                : bullet(node.getText());
        for (ChangeNode child : node.getChildren()) {
            editable.children.add(fromChangeNode(child));
        }
        return editable;
    }

    public int countBullets() {
        if (!isHeading()) {
            return 1;
        }
        int count = 0;
        for (EditableChangeNode child : children) {
            count += child.countBullets();
        }
        return count;
    }
}
