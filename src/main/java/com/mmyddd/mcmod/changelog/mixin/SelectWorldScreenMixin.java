package com.mmyddd.mcmod.changelog.mixin;

import com.mmyddd.mcmod.changelog.Config;
import com.mmyddd.mcmod.changelog.client.ChangelogEntry;
import com.mmyddd.mcmod.changelog.client.ChangelogOverviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

    @Shadow
    private EditBox searchBox;

    protected SelectWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!Config.isChangelogTabEnabled() || searchBox == null) return;

        addRenderableWidget(Button.builder(
                Component.translatable("ctnhchangelog.button.changelog"),
                button -> {
                    ChangelogEntry.resetLoaded();
                    ChangelogEntry.loadAfterConfig();

                    Minecraft.getInstance().setScreen(
                            new ChangelogOverviewScreen((SelectWorldScreen) (Object) this)
                    );
                }
        ).bounds(
                searchBox.getX() + searchBox.getWidth() + 4,
                searchBox.getY(),
                60,
                searchBox.getHeight()
        ).build());
    }
}