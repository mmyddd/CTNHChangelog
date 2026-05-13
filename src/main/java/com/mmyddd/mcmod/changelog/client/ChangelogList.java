package com.mmyddd.mcmod.changelog.client;

import com.mmyddd.mcmod.changelog.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ChangelogList extends ObjectSelectionList<ChangelogList.Entry> {

    private static final int MAX_PREVIEW_LENGTH = 60;
    private long lastBlinkTime = 0;
    private boolean blinkState = true;

    public ChangelogList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
        super(minecraft, width, height, y0, y1, itemHeight);

        for (ChangelogEntry entry : ChangelogEntry.getAllEntries()) {
            this.addEntry(new Entry(entry));
        }

        this.setRenderSelection(true);
        this.setRenderHeader(false, 0);
    }

    @Override
    public int getRowWidth() {
        return this.width - 40;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.width - 6;
    }

    @Override
    public void setSelected(@Nullable Entry entry) {
        super.setSelected(entry);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Entry selected = this.getSelected();
        if (selected != null && keyCode == GLFW.GLFW_KEY_ENTER) {
            this.openDetailScreen(selected);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openDetailScreen(Entry entry) {
        Screen currentScreen = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(
                new ChangelogDetailScreen(entry.getEntry(), currentScreen)
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlinkTime > ChangelogUtils.BLINK_INTERVAL) {
            lastBlinkTime = currentTime;
            blinkState = !blinkState;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {
        private final ChangelogEntry changelogEntry;
        private long lastClickTime = 0;

        private final int typeColor;
        private final List<ChangelogUtils.DisplayTag> displayTags = new ArrayList<>();

        public Entry(ChangelogEntry entry) {
            this.changelogEntry = entry;

            List<String> types = entry.getTypes();
            String primaryType = types.isEmpty() ? "patch" : types.get(0);
            this.typeColor = ChangelogUtils.getTypeColor(primaryType);

            for (String type : types) {
                String translatedType = ChangelogUtils.getTranslatedTypeTag(type);
                int color = ChangelogUtils.getTypeColor(type);
                displayTags.add(new ChangelogUtils.DisplayTag(translatedType, color));
            }

            for (String tag : entry.getTags()) {
                int color = ChangelogEntry.getTagColor(tag);
                displayTags.add(new ChangelogUtils.DisplayTag(tag, color));
            }
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = ChangelogList.this.minecraft.font;

            if (ChangelogList.this.getSelected() == this) {
                graphics.fill(left - 2, top - 2, left + width - 2, top + height + 2, 0x80FFFFFF);
            } else if (hovering) {
                graphics.fill(left - 2, top - 2, left + width - 2, top + height + 2, 0x80000000);
            }

            int borderColor = changelogEntry.getColor() | 0xFF000000;

            graphics.fill(left, top, left + 4, top + height, borderColor);

            boolean isLatest = (index == 0);

            boolean isCurrentVersion = changelogEntry.getVersion().equals(Config.getModpackVersion());

            if (Config.isEnableVersionCheck() && isLatest && VersionCheckService.hasUpdate() && blinkState) {
                graphics.fill(left - 8, top, left - 4, top + height, 0xFFFFFF00);
            }

            if (isCurrentVersion) {
                graphics.fill(left - 8, top, left - 4, top + height, 0xFF00FF00);
            }

            int textLeft = left + 12;
            int line1Y = top + 4;
            int line2Y = top + 18;
            int line3Y = top + 32;

            List<String> types = changelogEntry.getTypes();
            String primaryType = types.isEmpty() ? "patch" : types.get(0);
            String typeIcon = getTypeIcon(primaryType);
            String versionDisplay = typeIcon + " " + changelogEntry.getVersion();
            graphics.drawString(font, versionDisplay, textLeft, line1Y, typeColor);

            int tagStartX = textLeft + font.width(versionDisplay) + 6;
            if (!displayTags.isEmpty()) {
                int currentX = tagStartX;
                for (ChangelogUtils.DisplayTag displayTag : displayTags) {
                    int tagWidth = font.width(displayTag.text) + 6;
                    int tagHeight = 10;

                    graphics.fill(currentX, line1Y - 1, currentX + tagWidth, line1Y + tagHeight, displayTag.color);
                    graphics.drawString(font, displayTag.text, currentX + 3, line1Y, 0xFFFFFFFF);

                    currentX += tagWidth + 4;
                }
            }

            if (!changelogEntry.getDate().isEmpty()) {
                String dateText = Component.translatable("ctnhchangelog.date").getString() + ": " + changelogEntry.getDate();
                int dateWidth = font.width(dateText);
                graphics.drawString(font, dateText, left + width - dateWidth - 10, line1Y, 0xFFAAAAAA);
            }

            if (!changelogEntry.getTitle().isEmpty()) {
                graphics.drawString(font, changelogEntry.getTitle(), textLeft, line2Y, 0xFFDDDDDD);
            }

            String hintText = "(" + Component.translatable("ctnhchangelog.button.view_changelog").getString() + ")";
            int hintTextWidth = font.width(hintText);

            if (!changelogEntry.getChanges().isEmpty()) {
                String preview = "• " + changelogEntry.getChanges().get(0);
                if (preview.length() > MAX_PREVIEW_LENGTH) {
                    preview = preview.substring(0, MAX_PREVIEW_LENGTH - 3) + "...";
                }
                graphics.drawString(font, preview, textLeft, line3Y, 0xFFAAAAAA);

                if (changelogEntry.getChanges().size() > 1) {
                    String moreText = Component.translatable("ctnhchangelog.more_changes", changelogEntry.getChanges().size() - 1).getString();
                    int moreTextX = left + width - hintTextWidth - 120;
                    graphics.drawString(font, moreText, moreTextX, line3Y, 0xFF888888);
                }
            }

            graphics.drawString(font, hintText, left + width - hintTextWidth - 10, line3Y, 0xFF888888);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            ChangelogList.this.setSelected(this);

            if (button == 0) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < 250) {
                    Screen currentScreen = Minecraft.getInstance().screen;
                    Minecraft.getInstance().setScreen(
                            new ChangelogDetailScreen(changelogEntry, currentScreen)
                    );
                }
                lastClickTime = currentTime;
            }
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(changelogEntry.getVersion() + " - " + changelogEntry.getTitle());
        }

        public ChangelogEntry getEntry() {
            return changelogEntry;
        }

        private String getTypeIcon(String type) {
            return switch (type) {
                case "major" -> "★";
                case "minor" -> "●";
                case "patch" -> "○";
                case "hotfix" -> "◆";
                default -> "•";
            };
        }
    }
}