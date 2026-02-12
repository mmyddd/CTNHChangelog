package com.mmyddd.mcmod.changelog.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

public class ChangelogDetailScreen extends Screen {
    private final ChangelogEntry entry;
    private final Screen parentScreen;

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
        this.scrollAmount = 0;
    }

    @Override
    protected void init() {
        super.init();

        this.listLeft = 30;
        this.listRight = this.width - 30;
        this.listTop = 70;
        this.listBottom = this.height - 50;

        // 计算内容高度
        this.contentHeight = 0;
        for (String change : entry.getChanges()) {
            List<FormattedCharSequence> lines = this.font.split(
                    Component.literal(change),
                    listRight - listLeft - 20
            );
            this.contentHeight += lines.size() * 12;
        }

        // 返回按钮
        this.addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.back"),
                                button -> this.onClose()
                        )
                        .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        // 标题
        String titleText = entry.getVersion() + " - " + entry.getTitle();
        graphics.drawString(this.font,
                titleText,
                this.width / 2 - this.font.width(titleText) / 2,
                20,
                entry.getColor() | 0xFF000000
        );

        // 日期
        if (!entry.getDate().isEmpty()) {
            String dateText = Component.translatable("ctnhchangelog.date").getString() + ": " + entry.getDate();
            graphics.drawString(this.font,
                    dateText,
                    this.width / 2 - this.font.width(dateText) / 2,
                    40,
                    0xFFAAAAAA
            );
        }

        // 滚动区域
        graphics.enableScissor(listLeft - 5, listTop, listRight + 5, listBottom);

        int y = listTop - (int) this.scrollAmount;
        List<String> changes = entry.getChanges();

        for (String change : changes) {
            List<FormattedCharSequence> lines = this.font.split(
                    Component.literal("• " + change),
                    listRight - listLeft - 20
            );

            for (FormattedCharSequence line : lines) {
                if (y + 12 > listTop && y - 12 < listBottom) {
                    graphics.drawString(this.font, line, listLeft, y, 0xFFDDDDDD);
                }
                y += 12;
            }
        }

        graphics.disableScissor();

        // 滚动条
        int viewHeight = this.listBottom - this.listTop;
        if (this.contentHeight > viewHeight) {
            int scrollBarHeight = (int) ((float) viewHeight * viewHeight / this.contentHeight);
            int maxScroll = Math.max(0, this.contentHeight - viewHeight);
            int scrollBarY = (int) (this.scrollAmount * (viewHeight - scrollBarHeight) / maxScroll);
            scrollBarY = Mth.clamp(scrollBarY, 0, viewHeight - scrollBarHeight);

            graphics.fill(this.listRight + 2, this.listTop,
                    this.listRight + 6, this.listBottom,
                    0x33AAAAAA);
            graphics.fill(this.listRight + 2, this.listTop + scrollBarY,
                    this.listRight + 6, this.listTop + scrollBarY + scrollBarHeight,
                    0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int viewHeight = this.listBottom - this.listTop;
        if (this.contentHeight > viewHeight) {
            double maxScroll = Math.max(0, this.contentHeight - viewHeight);
            this.scrollAmount = Mth.clamp(this.scrollAmount - delta * 12, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int viewHeight = this.listBottom - this.listTop;
        if (this.contentHeight > viewHeight) {
            if (mouseX >= this.listRight + 2 && mouseX <= this.listRight + 6 &&
                    mouseY >= this.listTop && mouseY <= this.listBottom) {
                this.isScrolling = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isScrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isScrolling) {
            int viewHeight = this.listBottom - this.listTop;
            int maxScroll = Math.max(0, this.contentHeight - viewHeight);
            if (maxScroll > 0) {
                int scrollBarHeight = (int) ((float) viewHeight * viewHeight / this.contentHeight);
                double scrollRatio = (mouseY - this.listTop) / (double) (viewHeight - scrollBarHeight);
                this.scrollAmount = Mth.clamp(scrollRatio * maxScroll, 0, maxScroll);
            }
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
}