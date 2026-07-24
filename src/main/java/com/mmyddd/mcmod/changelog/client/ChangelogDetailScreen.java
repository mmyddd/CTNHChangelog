package com.mmyddd.mcmod.changelog.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangelogDetailScreen extends Screen {
    private static final int LINE_HEIGHT = 12;
    private static final int HEADING_HEIGHT = 20;

    private final ChangelogEntry entry;
    private final Screen parentScreen;
    private final Set<String> collapsedPaths = new HashSet<>();
    private final List<RenderedRow> renderedRows = new ArrayList<>();

    private double scrollAmount;
    private int contentHeight;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private boolean isScrolling;

    public ChangelogDetailScreen(ChangelogEntry entry, Screen parentScreen) {
        super(Component.literal(entry.getVersion() + " - " + entry.getTitle()));
        this.entry = entry;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        this.listLeft = 30;
        this.listRight = this.width - 30;
        this.listTop = 100;
        this.listBottom = this.height - 50;

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), button -> this.onClose())
                        .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        String titleText = entry.getVersion() + " - " + entry.getTitle();
        graphics.drawString(this.font, titleText,
                this.width / 2 - this.font.width(titleText) / 2, 20,
                entry.getColor() | 0xFF000000);

        if (!entry.getDate().isEmpty()) {
            String dateText = Component.translatable("ctnhchangelog.date").getString() + ": " + entry.getDate();
            graphics.drawString(this.font, dateText,
                    this.width / 2 - this.font.width(dateText) / 2, 35, 0xFFAAAAAA);
        }

        renderTags(graphics, 45);

        renderedRows.clear();
        int viewHeight = this.listBottom - this.listTop;
        contentHeight = calculateContentHeight();
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        scrollAmount = Mth.clamp(scrollAmount, 0, maxScroll);

        graphics.enableScissor(listLeft - 8, listTop, listRight + 8, listBottom);
        renderTree(graphics, mouseX, listTop - (int) scrollAmount);
        graphics.disableScissor();

        if (contentHeight > viewHeight) {
            int scrollBarHeight = Math.max(12, (int) ((float) viewHeight * viewHeight / contentHeight));
            int scrollRange = Math.max(1, viewHeight - scrollBarHeight);
            int scrollBarY = (int) (scrollAmount * scrollRange / maxScroll);
            scrollBarY = Mth.clamp(scrollBarY, 0, scrollRange);

            graphics.fill(listRight + 2, listTop, listRight + 6, listBottom, 0x33AAAAAA);
            graphics.fill(listRight + 2, listTop + scrollBarY,
                    listRight + 6, listTop + scrollBarY + scrollBarHeight, 0xFFAAAAAA);
        }
    }

    private int calculateContentHeight() {
        int height = 0;
        for (int i = 0; i < entry.getChangeTree().size(); i++) {
            height += calculateNodeHeight(entry.getChangeTree().get(i), pathFor(i), 0);
        }
        return Math.max(height, LINE_HEIGHT);
    }

    private int calculateNodeHeight(ChangeNode node, String path, int depth) {
        if (node.isHeading()) {
            int height = HEADING_HEIGHT;
            if (!collapsedPaths.contains(path)) {
                for (int i = 0; i < node.getChildren().size(); i++) {
                    height += calculateNodeHeight(node.getChildren().get(i), path + "." + i, depth + 1);
                }
            }
            return height;
        }

        List<FormattedCharSequence> lines = this.font.split(
                Component.literal("• " + node.getText()),
                Math.max(30, listRight - listLeft - 20 - depth * 14)
        );
        return Math.max(1, lines.size()) * LINE_HEIGHT;
    }

    private int renderTree(GuiGraphics graphics, int mouseX, int y) {
        List<ChangeNode> roots = entry.getChangeTree();
        for (int i = 0; i < roots.size(); i++) {
            y = renderNode(graphics, mouseX, roots.get(i), pathFor(i), 0, y);
        }
        return y;
    }

    private int renderNode(GuiGraphics graphics, int mouseX, ChangeNode node,
                           String path, int depth, int y) {
        int contentX = listLeft + depth * 16;
        if (node.isHeading()) {
            boolean collapsed = collapsedPaths.contains(path);
            int color = depth == 0 ? 0xFFFFFFFF : depth == 1 ? 0xFFE5F2E5 : 0xFFCFDCCF;
            MutableComponent heading = Component.literal((collapsed ? "▸ " : "▾ ") + node.getTitle())
                    .withStyle(ChatFormatting.BOLD);
            graphics.drawString(this.font, heading, contentX, y + 3, color);
            renderedRows.add(new RenderedRow(path, y, HEADING_HEIGHT, true));
            y += HEADING_HEIGHT;

            if (!collapsed) {
                for (int i = 0; i < node.getChildren().size(); i++) {
                    y = renderNode(graphics, mouseX, node.getChildren().get(i),
                            path + "." + i, depth + 1, y);
                }
            }
            return y;
        }

        List<FormattedCharSequence> lines = this.font.split(
                Component.literal("• " + node.getText()),
                Math.max(30, listRight - contentX - 10)
        );
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, contentX, y, 0xFFDDDDDD);
            y += LINE_HEIGHT;
        }
        renderedRows.add(new RenderedRow(path, y - Math.max(1, lines.size()) * LINE_HEIGHT,
                Math.max(1, lines.size()) * LINE_HEIGHT, false));
        return y;
    }

    private String pathFor(int index) {
        return Integer.toString(index);
    }

    private void renderTags(GuiGraphics graphics, int y) {
        List<ChangelogUtils.DisplayTag> allTags = new ArrayList<>();
        for (String type : entry.getTypes()) {
            allTags.add(new ChangelogUtils.DisplayTag(
                    ChangelogUtils.getTranslatedTypeTag(type), ChangelogUtils.getTypeColor(type)));
        }
        for (String tag : entry.getTags()) {
            allTags.add(new ChangelogUtils.DisplayTag(tag, ChangelogEntry.getTagColor(tag)));
        }
        if (allTags.isEmpty()) {
            return;
        }

        int startX = (this.width - calculateTotalWidth(allTags)) / 2;
        int currentX = startX;
        for (ChangelogUtils.DisplayTag tag : allTags) {
            int tagWidth = this.font.width(tag.text) + 6;
            graphics.fill(currentX, y - 1, currentX + tagWidth, y + 10, tag.color);
            graphics.drawString(this.font, tag.text, currentX + 3, y, 0xFFFFFFFF);
            currentX += tagWidth + 4;
        }
    }

    private int calculateTotalWidth(List<ChangelogUtils.DisplayTag> tags) {
        int total = 0;
        for (int i = 0; i < tags.size(); i++) {
            total += this.font.width(tags.get(i).text) + 6;
            if (i < tags.size() - 1) {
                total += 4;
            }
        }
        return total;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < listLeft || mouseX > listRight
                || mouseY < listTop || mouseY > listBottom) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int viewHeight = this.listBottom - this.listTop;
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        if (maxScroll > 0) {
            scrollAmount = Mth.clamp(scrollAmount - delta * 12, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contentHeight > listBottom - listTop
                && mouseX >= listRight + 2 && mouseX <= listRight + 6
                && mouseY >= listTop && mouseY <= listBottom) {
            isScrolling = true;
            return true;
        }

        if (button == 0 && mouseX >= listLeft - 8 && mouseX <= listRight + 8
                && mouseY >= listTop && mouseY <= listBottom) {
            for (RenderedRow row : renderedRows) {
                if (mouseY >= row.y && mouseY < row.y + row.height) {
                    if (row.heading) {
                        if (!collapsedPaths.add(row.path)) {
                            collapsedPaths.remove(row.path);
                        }
                        scrollAmount = Math.min(scrollAmount, Math.max(0, contentHeight - (listBottom - listTop)));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isScrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isScrolling) {
            int viewHeight = listBottom - listTop;
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            int scrollBarHeight = Math.max(12, (int) ((float) viewHeight * viewHeight / contentHeight));
            int scrollRange = Math.max(1, viewHeight - scrollBarHeight);
            scrollAmount = Mth.clamp((mouseY - listTop) * maxScroll / (double) scrollRange, 0, maxScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RenderedRow(String path, int y, int height, boolean heading) {
    }
}
