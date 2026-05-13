package com.mmyddd.mcmod.changelog.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 编辑器的脚注编辑标签页
 * 提供 Footer Text 的编辑和渐变色预览功能
 */
public class EditorFooterTab {
    private final ChangelogEditorScreen editor;
    private EditBox footerEditBox;
    private int areaTop, areaBottom;

    public EditorFooterTab(ChangelogEditorScreen editor) {
        this.editor = editor;
    }

    /**
     * 创建 EditBox 并注册到屏幕
     */
    public void addWidgets(ChangelogEditorScreen screen) {
        this.areaTop = 35;
        this.areaBottom = screen.getScreenHeight() - 10;

        // 创建 Footer Text 输入框
        this.footerEditBox = new EditBox(
                screen.getScreenFont(),
                20,
                areaTop + 26,
                screen.getScreenWidth() - 60,
                20,
                Component.literal("Footer Text")
        );

        // 设置初始值
        String footerText = editor.getFooterText();
        if (footerText != null) {
            this.footerEditBox.setValue(footerText);
        }

        // 注册到屏幕
        screen.addWidgetToScreen(this.footerEditBox);
    }

    /**
     * 从屏幕移除 EditBox 并同步值
     */
    public void removeWidgets(ChangelogEditorScreen screen) {
        if (this.footerEditBox != null) {
            // 同步值回编辑器
            editor.setFooterText(this.footerEditBox.getValue());
            screen.removeWidgetFromScreen(this.footerEditBox);
        }
    }

    /**
     * 渲染标签和渐变色预览
     */
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        // 渲染 "Footer Text:" 标签
        graphics.drawString(font, "Footer Text:", 20, areaTop + 10, 0xFFAAAAAA);

        // 渲染渐变色预览标签
        graphics.drawString(font, "Preview:", 20, areaTop + 70, 0xFFAAAAAA);

        // 渲染渐变色预览文本
        if (footerEditBox != null) {
            String text = footerEditBox.getValue();
            if (text != null && !text.isEmpty()) {
                renderGradientText(graphics, font, text, 20, areaTop + 84);
            }
        }
    }

    /**
     * 渲染三段渐变色文本
     * 绿(85,255,85) → 青(0,255,136) → 蓝(0,170,204)
     */
    private void renderGradientText(GuiGraphics graphics, Font font, String text, int x, int y) {
        int len = text.length();
        if (len == 0) return;

        // 如果只有一个字符，使用第一段颜色的中点
        if (len == 1) {
            int color = 0xFF000000 | (85 << 16) | (255 << 8) | 85;
            graphics.drawString(font, text, x, y, color);
            return;
        }

        int currentX = x;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            String s = String.valueOf(c);

            // 计算进度 (0.0 - 1.0)
            float progress = (float) i / (len - 1);

            // 三段渐变色插值
            int r, g, b;
            if (progress < 0.5f) {
                // 前半段: 绿(85,255,85) → 青(0,255,136)
                float t = progress * 2; // 重新映射到 0-1
                r = (int) (85 * (1 - t) + 0 * t);
                g = (int) (255 * (1 - t) + 255 * t);
                b = (int) (85 * (1 - t) + 136 * t);
            } else {
                // 后半段: 青(0,255,136) → 蓝(0,170,204)
                float t = (progress - 0.5f) * 2; // 重新映射到 0-1
                r = (int) (0 * (1 - t) + 0 * t);
                g = (int) (255 * (1 - t) + 170 * t);
                b = (int) (136 * (1 - t) + 204 * t);
            }

            // 组合成 ARGB 颜色
            int color = 0xFF000000 | (r << 16) | (g << 8) | b;

            graphics.drawString(font, s, currentX, y, color);
            currentX += font.width(s);
        }
    }

    /**
     * 鼠标点击事件委托
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (footerEditBox != null) {
            return footerEditBox.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    /**
     * 按键事件委托
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (footerEditBox != null) {
            return footerEditBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    /**
     * 字符输入事件委托
     */
    public boolean charTyped(char codePoint, int modifiers) {
        if (footerEditBox != null) {
            return footerEditBox.charTyped(codePoint, modifiers);
        }
        return false;
    }
}
