package com.mmyddd.mcmod.changelog.mixin;

import com.mmyddd.mcmod.changelog.Config;
import com.mmyddd.mcmod.changelog.client.ChangelogTab;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
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
    private Button changelogButton;

    protected SelectWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!Config.isChangelogTabEnabled()) return;

        addChangelogButton();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (changelogButton != null && searchBox != null) {
            int expectedX = searchBox.getX() + searchBox.getWidth() + 4;
            int expectedY = searchBox.getY();

            if (changelogButton.getX() != expectedX || changelogButton.getY() != expectedY) {
                changelogButton.setX(expectedX);
                changelogButton.setY(expectedY);
            }
        }
    }

    @Unique
    private void addChangelogButton() {
        if (searchBox == null) return;

        int buttonWidth = 60;
        int buttonX = searchBox.getX() + searchBox.getWidth() + 4;
        int buttonY = searchBox.getY();
        int buttonHeight = searchBox.getHeight();

        changelogButton = Button.builder(
                        Component.translatable("changelog.button.changelog"),
                        button -> {
                            ChangelogTab.shouldOpenChangelogTab = true;
                            CreateWorldScreen.openFresh(minecraft, this);
                        })
                .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
                .build();

        addRenderableWidget(changelogButton);
    }

    @Override
    public void repositionElements() {
        super.repositionElements();

        if (changelogButton != null && searchBox != null) {
            changelogButton.setX(searchBox.getX() + searchBox.getWidth() + 4);
            changelogButton.setY(searchBox.getY());
        }
    }
}