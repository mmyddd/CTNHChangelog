package com.mmyddd.mcmod.changelog.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

public class ChangelogScreen extends Screen {
    private final ChangelogEntry entry;
    private final CreateWorldScreen parentScreen;

    // 滚动相关
    private double scrollAmount;
    private int contentHeight;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private boolean isScrolling;

    public ChangelogScreen(ChangelogEntry entry) {
        this(entry, null);
    }

    public ChangelogScreen(ChangelogEntry entry, CreateWorldScreen parentScreen) {
        super(Component.literal(entry.getVersion() + " - " + entry.getTitle()));
        this.entry = entry;
        this.parentScreen = parentScreen;
        this.scrollAmount = 0;
    }

    @Override
    protected void init() {
        super.init();

        // 定义内容区域
        this.listLeft = 30;
        this.listRight = this.width - 30;
        this.listTop = 70;
        this.listBottom = this.height - 50;

        // 计算内容总高度
        this.contentHeight = 0;
        for (String change : entry.getChanges()) {
            // 使用 font.width 来判断是否需要换行
            if (this.font.width(change) > this.listRight - this.listLeft - 20) {
                // 估算换行后的行数
                int charsPerLine = (this.listRight - this.listLeft - 20) / this.font.width("一");
                int lines = (int) Math.ceil((double) change.length() / charsPerLine);
                this.contentHeight += lines * 12;
            } else {
                this.contentHeight += 12;
            }
        }

        // 返回按钮
        this.addRenderableWidget(
                Button.builder(Component.literal("返回"),
                                button -> this.onClose())
                        .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        // 渲染版本和标题（固定不滚动）
        String titleText = entry.getVersion() + " - " + entry.getTitle();
        graphics.drawString(this.font,
                titleText,
                this.width / 2 - this.font.width(titleText) / 2,
                20,
                entry.getColor() | 0xFF000000);

        // 渲染日期（固定不滚动）
        if (!entry.getDate().isEmpty()) {
            String dateText = "日期: " + entry.getDate();
            graphics.drawString(this.font,
                    dateText,
                    this.width / 2 - this.font.width(dateText) / 2,
                    40,
                    0xFFAAAAAA);
        }

        // 创建剪刀区域（启用滚动裁剪）
        graphics.enableScissor(listLeft - 5, listTop, listRight + 5, listBottom);

        // 渲染可滚动的内容
        int y = listTop - (int) this.scrollAmount;
        List<String> changes = entry.getChanges();

        for (String change : changes) {
            // 处理长文本换行
            List<FormattedCharSequence> lines = this.font.split(Component.literal(change), listRight - listLeft - 20);

            for (int i = 0; i < lines.size(); i++) {
                // 只渲染在可视区域内的内容
                if (y + 12 > listTop && y - 12 < listBottom) {
                    if (i == 0) {
                        // 第一行带圆点
                        graphics.drawString(this.font, Component.literal("• ").append(Component.literal(change)).getVisualOrderText(),
                                listLeft, y, 0xFFDDDDDD);
                    } else {
                        // 其余行缩进
                        graphics.drawString(this.font, lines.get(i), listLeft + 10, y, 0xFFDDDDDD);
                    }
                }
                y += 12;
            }
        }

        graphics.disableScissor();

        // 渲染滚动条（如果内容高度超过可视区域）
        int viewHeight = this.listBottom - this.listTop;
        if (this.contentHeight > viewHeight) {
            int scrollBarHeight = (int) ((float) viewHeight * viewHeight / this.contentHeight);
            int maxScroll = Math.max(0, this.contentHeight - viewHeight);
            int scrollBarY = (int) (this.scrollAmount * (viewHeight - scrollBarHeight) / maxScroll);
            scrollBarY = Mth.clamp(scrollBarY, 0, viewHeight - scrollBarHeight);

            // 滚动条背景
            graphics.fill(this.listRight + 2, this.listTop,
                    this.listRight + 6, this.listBottom,
                    0x33AAAAAA);
            // 滚动条滑块
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
        // 检查是否点击在滚动条区域
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
            if (this.parentScreen != null) {
                this.minecraft.setScreen(this.parentScreen);
            } else {
                if (this.minecraft.screen == this) {
                    this.minecraft.setScreen(null);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}