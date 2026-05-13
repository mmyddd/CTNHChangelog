package com.mmyddd.mcmod.changelog.mixin;

import com.mmyddd.mcmod.changelog.Config;
import com.mmyddd.mcmod.changelog.client.ChangelogEntry;
import com.mmyddd.mcmod.changelog.client.ChangelogOverviewScreen;
import com.mmyddd.mcmod.changelog.client.MixinHelper;
import com.mmyddd.mcmod.changelog.client.VersionCheckService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

    @Shadow
    private EditBox searchBox;

    @Unique
    private Button ctnhChangelogButton;

    @Unique
    private boolean ctnhHasUpdate = false;

    protected SelectWorldScreenMixin(Component title) {
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
        if (!Config.isChangelogTabEnabled() || searchBox == null) return;
        if (!Config.showButtonOnSelectWorld()) return;

        ctnhChangelogButton = Button.builder(
                Component.translatable("ctnhchangelog.button.changelog"),
                button -> {
                    ChangelogEntry.resetLoaded();
                    ChangelogEntry.loadAfterConfig();
                    Minecraft.getInstance().setScreen(
                            new ChangelogOverviewScreen((SelectWorldScreen) (Object) this)
                    );
                }
        ).bounds(searchBox.getX() + searchBox.getWidth() + 3, searchBox.getY(), 65, searchBox.getHeight()).build();

        addRenderableWidget(ctnhChangelogButton);
    }

    @Override
    public void tick() {
        super.tick();
        ctnhHasUpdate = MixinHelper.checkUpdateStatus();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ctnhHasUpdate || ctnhChangelogButton == null) return;
        MixinHelper.renderUpdateIcon(graphics, ctnhChangelogButton);
    }
}