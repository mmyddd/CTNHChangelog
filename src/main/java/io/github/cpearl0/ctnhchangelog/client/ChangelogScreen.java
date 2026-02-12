package io.github.cpearl0.ctnhchangelog.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

public class ChangelogScreen extends Screen {
    private final ChangelogEntry entry;
    private final CreateWorldScreen parentScreen;  // 保存父屏幕

    // ✅ 原有的构造函数（保留向后兼容）
    public ChangelogScreen(ChangelogEntry entry) {
        this(entry, null);
    }

    // ✅ 新增的构造函数，接收父屏幕
    public ChangelogScreen(ChangelogEntry entry, CreateWorldScreen parentScreen) {
        super(Component.literal(entry.getVersion() + " - " + entry.getTitle()));
        this.entry = entry;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

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

        // 渲染版本和标题
        graphics.drawString(this.font,
                entry.getVersion() + " - " + entry.getTitle(),
                this.width / 2 - this.font.width(entry.getVersion() + " - " + entry.getTitle()) / 2,
                20,
                entry.getColor() | 0xFF000000);

        // 渲染日期
        if (!entry.getDate().isEmpty()) {
            graphics.drawString(this.font,
                    "日期: " + entry.getDate(),
                    this.width / 2 - this.font.width("日期: " + entry.getDate()) / 2,
                    40,
                    0xFFAAAAAA);
        }

        // 渲染所有更改
        int y = 70;
        for (String change : entry.getChanges()) {
            graphics.drawString(this.font, "• " + change, 30, y, 0xFFDDDDDD);
            y += 12;
        }
    }

    @Override
    public void onClose() {
        // ✅ 优先返回到保存的父屏幕
        if (this.minecraft != null) {
            if (this.parentScreen != null) {
                this.minecraft.setScreen(this.parentScreen);
            } else {
                // 如果没有父屏幕，尝试返回上一个屏幕
                this.minecraft.setScreen(this.minecraft.screen);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}