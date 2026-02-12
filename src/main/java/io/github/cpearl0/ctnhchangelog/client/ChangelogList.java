package io.github.cpearl0.ctnhchangelog.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ChangelogList extends ObjectSelectionList<ChangelogList.Entry> {

    @Nullable
    private ChangelogScreen detailScreen;

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
        if (currentScreen instanceof CreateWorldScreen) {
            this.detailScreen = new ChangelogScreen(entry.getEntry(), (CreateWorldScreen) currentScreen);
            Minecraft.getInstance().setScreen(detailScreen);
        }
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

            // 渲染选中状态
            if (ChangelogList.this.getSelected() == this) {
                graphics.fill(left - 2, top - 2, left + width + 2, top + height + 2, 0x80FFFFFF);
            } else if (hovering) {
                graphics.fill(left - 2, top - 2, left + width + 2, top + height + 2, 0x80000000);
            }

            int borderColor = changelogEntry.getColor();
            graphics.fill(left, top, left + 4, top + height, borderColor | 0xFF000000);

            int textLeft = left + 12;
            int line1Y = top + 4;
            int line2Y = top + 18;
            int line3Y = top + 32;

            // Version and type
            String typeIcon = getTypeIcon(changelogEntry.getType());
            graphics.drawString(font, typeIcon + " " + changelogEntry.getVersion(), textLeft, line1Y, changelogEntry.getColor() | 0xFF000000);

            // 渲染多个标签
            int tagStartX = textLeft + font.width(typeIcon + " " + changelogEntry.getVersion()) + 6;
            List<String> tags = changelogEntry.getTags();

            if (!tags.isEmpty()) {
                int currentX = tagStartX;
                for (String tag : tags) {
                    int tagWidth = font.width(tag) + 6;
                    int tagHeight = 10;

                    // 从全局标签颜色映射获取颜色
                    int tagBgColor = ChangelogEntry.getTagColor(tag);

                    graphics.fill(currentX, line1Y - 1, currentX + tagWidth, line1Y + tagHeight, tagBgColor);
                    graphics.drawString(font, tag, currentX + 3, line1Y, 0xFFFFFFFF);

                    currentX += tagWidth + 4;  // 标签间距
                }
            }

            // Date
            if (!changelogEntry.getDate().isEmpty()) {
                String dateText = "日期: " + changelogEntry.getDate();
                int dateWidth = font.width(dateText);
                graphics.drawString(font, dateText, left + width - dateWidth - 10, line1Y, 0xFFAAAAAA);
            }

            // Title
            if (!changelogEntry.getTitle().isEmpty()) {
                graphics.drawString(font, changelogEntry.getTitle(), textLeft, line2Y, 0xFFDDDDDD);
            }

            // Changes preview (first line)
            if (!changelogEntry.getChanges().isEmpty()) {
                String preview = "• " + changelogEntry.getChanges().get(0);
                if (preview.length() > 60) {
                    preview = preview.substring(0, 57) + "...";
                }
                graphics.drawString(font, preview, textLeft, line3Y, 0xFFAAAAAA);

                if (changelogEntry.getChanges().size() > 1) {
                    String moreText = "还有 " + (changelogEntry.getChanges().size() - 1) + " 项更改...";
                    graphics.drawString(font, moreText, textLeft + 250, line3Y, 0xFF888888);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            ChangelogList.this.setSelected(this);

            if (button == 0) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < 250) {
                    ChangelogList.this.openDetailScreen(this);
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
                case "fix" -> "◆";
                default -> "•";
            };
        }

        private String getTypeTag(String type) {
            return switch (type) {
                case "major" -> "重大更新";
                case "minor" -> "功能更新";
                case "patch" -> "修复";
                case "fix" -> "热修复";
                default -> null;
            };
        }
    }
}