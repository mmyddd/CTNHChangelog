package com.mmyddd.mcmod.changelog.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * An EditBox that commits its current value when Enter is pressed or focus is lost.
 */
public class CommitOnBlurEditBox extends EditBox {
    private Runnable commitAction = () -> {
    };
    private String lastCommittedValue;
    private boolean committing;

    public CommitOnBlurEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.lastCommittedValue = getValue();
    }

    public void setCommitAction(Runnable commitAction) {
        this.commitAction = commitAction == null ? () -> {
        } : commitAction;
    }

    public void markCommittedValue() {
        this.lastCommittedValue = getValue();
    }

    public void commit() {
        if (committing || getValue().equals(lastCommittedValue)) {
            return;
        }
        committing = true;
        try {
            commitAction.run();
        } finally {
            lastCommittedValue = getValue();
            committing = false;
        }
    }

    @Override
    public void setFocused(boolean focused) {
        boolean focusLost = isFocused() && !focused;
        super.setFocused(focused);
        if (focusLost) {
            commit();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            super.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
