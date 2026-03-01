package com.mmyddd.mcmod.changelog.mixin;

import com.mmyddd.mcmod.changelog.Config;
import com.mmyddd.mcmod.changelog.client.ChangelogEntry;
import com.mmyddd.mcmod.changelog.client.ChangelogOverviewScreen;
import com.mmyddd.mcmod.changelog.client.VersionCheckService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique
    private Button ctnhChangelogButton;

    @Unique
    private boolean ctnhHasUpdate = false;

    @Unique
    private static final int BLINK_INTERVAL = 800; // 800ms 闪烁周期，与Forge一致

    @Unique
    private static final ResourceLocation VERSION_CHECK_ICONS =
            ResourceLocation.tryBuild("forge", "textures/gui/version_check_icons.png");

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInitHead(CallbackInfo ci) {
        if (Config.isChangelogTabEnabled() && !Config.getModpackVersion().isEmpty()) {
            VersionCheckService.reset();
            VersionCheckService.checkForUpdate();
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!Config.isChangelogTabEnabled() || Config.getModpackVersion().isEmpty()) return;
        if (!Config.showButtonOnTitleScreen()) return;

        int l = this.height / 4 + 48;
        int buttonY = l + 72 + 12 + 24; // options按钮下方24像素

        ctnhChangelogButton = Button.builder(
                Component.translatable("ctnhchangelog.button.changelog"),
                button -> {
                    ChangelogEntry.resetLoaded();
                    ChangelogEntry.loadAfterConfig();
                    Minecraft.getInstance().setScreen(
                            new ChangelogOverviewScreen((TitleScreen) (Object) this)
                    );
                }
        ).bounds(this.width / 2 - 100, buttonY, 200, 20).build();

        addRenderableWidget(ctnhChangelogButton);
    }

    @Override
    public void tick() {
        super.tick();

        if (Config.isEnableVersionCheck() && VersionCheckService.isCheckDone()) {
            ctnhHasUpdate = VersionCheckService.hasUpdate();
        } else {
            ctnhHasUpdate = false;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ctnhHasUpdate || ctnhChangelogButton == null) return;

        int x = ctnhChangelogButton.getX();
        int y = ctnhChangelogButton.getY();
        int w = ctnhChangelogButton.getWidth();
        int h = ctnhChangelogButton.getHeight();

        int iconX = x + w - (h / 2 + 4);
        int iconY = y + (h / 2 - 4);

        int sheetOffset = 3;
        int u = sheetOffset * 8;

        boolean blink = (System.currentTimeMillis() / BLINK_INTERVAL & 1) == 1;
        int v = blink ? 8 : 0;

        graphics.blit(VERSION_CHECK_ICONS, iconX, iconY, u, v, 8, 8, 64, 16);
    }
}