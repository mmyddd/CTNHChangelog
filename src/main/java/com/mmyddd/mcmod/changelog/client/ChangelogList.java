package com.mmyddd.mcmod.changelog.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ChangelogList extends ObjectSelectionList<ChangelogList.Entry> {

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
        if (selected != null && keyCode == 257) {
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

    public class Entry extends ObjectSelectionList.Entry<Entry> {
        private final ChangelogEntry changelogEntry;
        private long lastClickTime = 0;

        private final int typeColor;
        private final List<DisplayTag> displayTags = new ArrayList<>();

        public Entry(ChangelogEntry entry) {
            this.changelogEntry = entry;

            List<String> types = entry.getTypes();
            String primaryType = types.isEmpty() ? "patch" : types.get(0);
            this.typeColor = getTypeColor(primaryType);

            for (String type : types) {
                String translatedType = getTranslatedTypeTag(type);
                if (translatedType != null) {
                    int color = getTypeColor(type);
                    displayTags.add(new DisplayTag(translatedType, color));
                }
            }

            for (String tag : entry.getTags()) {
                int color = ChangelogEntry.getTagColor(tag);
                displayTags.add(new DisplayTag(tag, color));
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

            int borderColor = changelogEntry.getColor();
            graphics.fill(left, top, left + 4, top + height, borderColor | 0xFF000000);

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
                for (DisplayTag displayTag : displayTags) {
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
                if (preview.length() > 60) {
                    preview = preview.substring(0, 57) + "...";
                }
                graphics.drawString(font, preview, textLeft, line3Y, 0xFFAAAAAA);

                if (changelogEntry.getChanges().size() > 1) {
                    String moreText = Component.translatable("ctnhchangelog.more_changes", changelogEntry.getChanges().size() - 1).getString();

                    int moreTextX = left + width - hintTextWidth - 120; // 在查看提示左边120像素
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

        private int getTypeColor(String type) {
            return switch (type) {
                case "major" -> 0xFF5555FF;
                case "minor" -> 0xFF55FF55;
                case "patch" -> 0xFFFFFF55;
                case "hotfix" -> 0xFFFF5555;
                case "danger" -> 0xFFFF5555;
                default -> 0xFF888888;
            };
        }

        private String getTranslatedTypeTag(String type) {
            return switch (type) {
                case "major" -> Component.translatable("ctnhchangelog.type.major").getString();
                case "minor" -> Component.translatable("ctnhchangelog.type.minor").getString();
                case "patch" -> Component.translatable("ctnhchangelog.type.patch").getString();
                case "hotfix" -> Component.translatable("ctnhchangelog.type.hotfix").getString();
                case "danger" -> Component.translatable("ctnhchangelog.type.danger").getString();
                default -> null;
            };
        }

        private static class DisplayTag {
            final String text;
            final int color;

            DisplayTag(String text, int color) {
                this.text = text;
                this.color = color;
            }
        }
    }
}