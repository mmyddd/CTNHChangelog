package io.github.cpearl0.ctnhchangelog.mixin;

import io.github.cpearl0.ctnhchangelog.Config;
import io.github.cpearl0.ctnhchangelog.client.ChangelogTab;
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
    private Button ctnhchangelog$changelogButton;

    protected SelectWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!Config.isChangelogTabEnabled()) return;

        // ✅ 延迟添加按钮，确保布局已完成
        this.ctnhchangelog$addChangelogButton();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        // ✅ 在tick中检查并修复按钮位置
        if (this.ctnhchangelog$changelogButton != null && this.searchBox != null) {
            int expectedX = this.searchBox.getX() + this.searchBox.getWidth() + 4;
            int expectedY = this.searchBox.getY();

            // 如果位置不正确，重新放置
            if (this.ctnhchangelog$changelogButton.getX() != expectedX ||
                    this.ctnhchangelog$changelogButton.getY() != expectedY) {
                this.ctnhchangelog$changelogButton.setX(expectedX);
                this.ctnhchangelog$changelogButton.setY(expectedY);
            }
        }
    }

    @Unique
    private void ctnhchangelog$addChangelogButton() {
        if (this.searchBox == null) return;

        int buttonWidth = 60;
        int buttonX = this.searchBox.getX() + this.searchBox.getWidth() + 4;
        int buttonY = this.searchBox.getY();
        int buttonHeight = this.searchBox.getHeight();

        // ✅ 保存按钮引用以便后续调整
        this.ctnhchangelog$changelogButton = Button.builder(
                        Component.translatable("ctnhchangelog.button.changelog"),
                        (button) -> {
                            ChangelogTab.shouldOpenChangelogTab = true;
                            CreateWorldScreen.openFresh(this.minecraft, this);
                        })
                .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
                .build();

        this.addRenderableWidget(this.ctnhchangelog$changelogButton);
    }

    @Override
    public void repositionElements() {
        super.repositionElements();
        // ✅ 当界面元素重排时，也重新放置按钮
        if (this.ctnhchangelog$changelogButton != null && this.searchBox != null) {
            this.ctnhchangelog$changelogButton.setX(this.searchBox.getX() + this.searchBox.getWidth() + 4);
            this.ctnhchangelog$changelogButton.setY(this.searchBox.getY());
        }
    }
}