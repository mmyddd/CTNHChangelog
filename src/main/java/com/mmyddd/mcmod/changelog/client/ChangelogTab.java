package com.mmyddd.mcmod.changelog.client;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ChangelogTab implements Tab {
    private static final Component TITLE = Component.translatable("ctnhchangelog.tab.title");
    public static boolean shouldOpenChangelogTab = false;
    private final CreateWorldScreen screen;
    @Getter
    private ChangelogList changelogList;
    private ScreenRectangle tabArea;

    public ChangelogTab(CreateWorldScreen screen) {
        this.screen = screen;
    }

    @Override
    public Component getTabTitle() {
        return TITLE;
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {
    }

    @Override
    public void doLayout(ScreenRectangle rectangle) {
        this.tabArea = rectangle;
        Minecraft minecraft = Minecraft.getInstance();
        int listY0 = rectangle.top();
        int listY1 = rectangle.top() + rectangle.height() - 25;
        this.changelogList = new ChangelogList(minecraft, rectangle.width(), rectangle.height() - 25, listY0, listY1, 52);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.changelogList != null) {
            this.changelogList.render(graphics, mouseX, mouseY, partialTick);
        }

        if (this.tabArea != null) {
            Font font = Minecraft.getInstance().font;
            String footerText = "* 感谢您游玩 Create: New Horizon 整合包！查看最新更新内容。";
            int footerY = this.tabArea.top() + this.tabArea.height() - 21;
            int textWidth = font.width(footerText);
            int startX = this.tabArea.left() + (this.tabArea.width() - textWidth) / 2;
            int charX = startX;
            int textLength = footerText.length();

            for (int i = 0; i < textLength; ++i) {
                String ch = String.valueOf(footerText.charAt(i));
                float progress = (float) i / (float) (textLength - 1);
                int color = getGradientColor(progress);
                graphics.drawString(font, ch, charX, footerY, color);
                charX += font.width(ch);
            }
        }
    }

    private int getGradientColor(float progress) {
        int startR = 85;
        int startG = 255;
        int startB = 85;
        int midR = 0;
        int midG = 255;
        int midB = 136;
        int endR = 0;
        int endG = 170;
        int endB = 204;
        int r;
        int g;
        int b;
        if (progress < 0.5F) {
            float t = progress * 2.0F;
            r = (int) ((float) startR + (float) (midR - startR) * t);
            g = (int) ((float) startG + (float) (midG - startG) * t);
            b = (int) ((float) startB + (float) (midB - startB) * t);
        } else {
            float t = (progress - 0.5F) * 2.0F;
            r = (int) ((float) midR + (float) (endR - midR) * t);
            g = (int) ((float) midG + (float) (endG - midG) * t);
            b = (int) ((float) midB + (float) (endB - midB) * t);
        }

        return 0xFF000000 | r << 16 | g << 8 | b;
    }

}