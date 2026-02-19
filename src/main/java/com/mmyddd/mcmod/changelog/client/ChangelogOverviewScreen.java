package com.mmyddd.mcmod.changelog.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChangelogOverviewScreen extends Screen {
    private final Screen parentScreen;
    private ChangelogList changelogList;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private boolean isLoading = false;

    public ChangelogOverviewScreen(Screen parent) {
        super(Component.translatable("ctnhchangelog.tab.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.listLeft = 20;
        this.listRight = this.width - 20;
        this.listTop = 40;
        this.listBottom = this.height - 60;

        refreshList();

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.back"),
                                button -> this.minecraft.setScreen(parentScreen)
                        )
                        .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                        .build()
        );
        this.addRenderableWidget(
                Button.builder(
                                Component.literal("↻"),
                                button -> {
                                    if (!isLoading) {
                                        isLoading = true;
                                        button.active = false;

                                        ChangelogEntry.resetLoaded();

                                        CompletableFuture.runAsync(() -> {
                                            ChangelogEntry.loadAfterConfig();
                                            while (!ChangelogEntry.isLoadingComplete()) {
                                                try {
                                                    Thread.sleep(50);
                                                } catch (InterruptedException e) {
                                                    Thread.currentThread().interrupt();
                                                    break;
                                                }
                                            }

                                            if (this.minecraft != null) {
                                                this.minecraft.execute(() -> {
                                                    refreshList();
                                                    isLoading = false;
                                                    button.active = true;
                                                });
                                            }
                                        });
                                    }
                                }
                        )
                        .bounds(this.width - 30, 10, 20, 20)
                        .build()
        );
    }

    private void refreshList() {
        if (changelogList != null) {
            this.removeWidget(changelogList);
        }
        this.changelogList = new ChangelogList(
                this.minecraft,
                this.width - 40,
                this.height - 100,
                this.listTop,
                this.listBottom,
                52
        );
        this.addRenderableWidget(changelogList);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        String title = this.title.getString();
        graphics.drawString(
                this.font,
                title,
                this.width / 2 - this.font.width(title) / 2,
                20,
                0xFFFFFF
        );

        List<ChangelogEntry> entries = ChangelogEntry.getAllEntries();
        String stats = Component.translatable(
                "ctnhchangelog.stats",
                entries.size()
        ).getString();

        if (isLoading) {
            stats = Component.translatable("ctnhchangelog.loading").getString() + " " + stats;
        }

        graphics.drawString(
                this.font,
                stats,
                20,
                25,
                0xAAAAAA
        );

        String footer = ChangelogEntry.getFooterText();
        if (footer != null && !footer.isEmpty()) {
            int footerY = this.height - 50;
            int textWidth = this.font.width(footer);
            int startX = (this.width - textWidth) / 2;
            int charX = startX;

            for (int i = 0; i < footer.length(); i++) {
                String ch = String.valueOf(footer.charAt(i));
                float progress = (float) i / (float) (footer.length() - 1);
                int color = getGradientColor(progress);
                graphics.drawString(this.font, ch, charX, footerY, color);
                charX += this.font.width(ch);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int getGradientColor(float progress) {
        int startR = 85, startG = 255, startB = 85;
        int midR = 0, midG = 255, midB = 136;
        int endR = 0, endG = 170, endB = 204;

        int r, g, b;
        if (progress < 0.5F) {
            float t = progress * 2.0F;
            r = (int) (startR + (midR - startR) * t);
            g = (int) (startG + (midG - startG) * t);
            b = (int) (startB + (midB - startB) * t);
        } else {
            float t = (progress - 0.5F) * 2.0F;
            r = (int) (midR + (endR - midR) * t);
            g = (int) (midG + (endG - midG) * t);
            b = (int) (midB + (endB - midB) * t);
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (changelogList != null && changelogList.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (changelogList != null && changelogList.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}