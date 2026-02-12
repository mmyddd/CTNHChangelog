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

        public Entry(ChangelogEntry entry) {
            this.changelogEntry = entry;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = ChangelogList.this.minecraft.font;

            // 选中/悬停效果
            if (ChangelogList.this.getSelected() == this) {
                graphics.fill(left - 2, top - 2, left + width - 2, top + height + 2, 0x80FFFFFF);
            } else if (hovering) {
                graphics.fill(left - 2, top - 2, left + width - 2, top + height + 2, 0x80000000);
            }

            // 左侧边框
            int borderColor = changelogEntry.getColor();
            graphics.fill(left, top, left + 4, top + height, borderColor | 0xFF000000);

            int textLeft = left + 12;
            int line1Y = top + 4;
            int line2Y = top + 18;
            int line3Y = top + 32;

            // 版本号 + 图标
            List<String> types = changelogEntry.getTypes();
            String primaryType = types.isEmpty() ? "patch" : types.get(0);
            String typeIcon = getTypeIcon(primaryType);
            graphics.drawString(font, typeIcon + " " + changelogEntry.getVersion(), textLeft, line1Y, changelogEntry.getColor() | 0xFF000000);

            // 标签
            List<String> allTags = new ArrayList<>();
            for (String type : types) {
                String translatedTag = getTranslatedTypeTag(type);
                if (translatedTag != null) {
                    allTags.add(translatedTag);
                }
            }
            allTags.addAll(changelogEntry.getTags());

            int tagStartX = textLeft + font.width(typeIcon + " " + changelogEntry.getVersion()) + 6;
            if (!allTags.isEmpty()) {
                int currentX = tagStartX;
                for (String tag : allTags) {
                    int tagWidth = font.width(tag) + 6;
                    int tagHeight = 10;
                    int tagBgColor = ChangelogEntry.getTagColor(tag);
                    graphics.fill(currentX, line1Y - 1, currentX + tagWidth, line1Y + tagHeight, tagBgColor);
                    graphics.drawString(font, tag, currentX + 3, line1Y, 0xFFFFFFFF);
                    currentX += tagWidth + 4;
                }
            }

            // 日期
            if (!changelogEntry.getDate().isEmpty()) {
                String dateText = Component.translatable("ctnhchangelog.date").getString() + ": " + changelogEntry.getDate();
                int dateWidth = font.width(dateText);
                graphics.drawString(font, dateText, left + width - dateWidth - 10, line1Y, 0xFFAAAAAA);
            }

            // 标题
            if (!changelogEntry.getTitle().isEmpty()) {
                graphics.drawString(font, changelogEntry.getTitle(), textLeft, line2Y, 0xFFDDDDDD);
            }

            // 预览内容
            if (!changelogEntry.getChanges().isEmpty()) {
                String preview = "• " + changelogEntry.getChanges().get(0);
                if (preview.length() > 60) {
                    preview = preview.substring(0, 57) + "...";
                }
                graphics.drawString(font, preview, textLeft, line3Y, 0xFFAAAAAA);

                // 更多数量提示
                if (changelogEntry.getChanges().size() > 1) {
                    String moreText = Component.translatable("ctnhchangelog.more_changes", changelogEntry.getChanges().size() - 1).getString();
                    graphics.drawString(font, moreText, textLeft + 250, line3Y, 0xFF888888);
                }
            }

            // ========== 双击查看详情提示 ==========
            String hintText = "(" + Component.translatable("ctnhchangelog.button.view_changelog").getString() + ")";
            graphics.drawString(font, hintText, left + width - font.width(hintText) - 10, line3Y, 0xFF888888);
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
    }
}