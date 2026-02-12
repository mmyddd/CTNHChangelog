package com.mmyddd.mcmod.changelog.mixin;

import com.mmyddd.mcmod.changelog.client.ChangelogTab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TabButton.class)
public abstract class TabButtonMixin extends AbstractWidget {
    @Shadow
    @Final
    private TabManager tabManager;
    @Shadow
    @Final
    private Tab tab;

    protected TabButtonMixin() {
        super(0, 0, 0, 0, Component.empty());
    }

    @Inject(method = "isSelected", at = @At("HEAD"), cancellable = true)
    private void onIsSelected(CallbackInfoReturnable<Boolean> cir) {
        Tab currentTab = this.tabManager.getCurrentTab();
        if (this.tab instanceof ChangelogTab && currentTab instanceof ChangelogTab) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void onRenderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Tab currentTab = this.tabManager.getCurrentTab();
        if (this.tab instanceof ChangelogTab && currentTab instanceof ChangelogTab) {
            graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0xFF55FF55);
        }
    }
}