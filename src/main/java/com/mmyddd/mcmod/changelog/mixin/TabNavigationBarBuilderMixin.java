package com.mmyddd.mcmod.changelog.mixin;

import com.mmyddd.mcmod.changelog.Config;
import com.mmyddd.mcmod.changelog.client.ChangelogTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(TabNavigationBar.Builder.class)
public class TabNavigationBarBuilderMixin {
    @Shadow
    @Final
    private TabManager tabManager;
    @Shadow
    @Final
    private List<Tab> tabs;
    @Shadow
    private int width;

    @Inject(method = "build", at = @At("HEAD"))
    private void onBuild(CallbackInfoReturnable<TabNavigationBar> cir) {
        if (!Config.isChangelogTabEnabled()) return;
        
        Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof CreateWorldScreen screen) {
            for (Tab tab : this.tabs) {
                if (tab instanceof ChangelogTab) {
                    return;
                }
            }
            this.tabs.add(new ChangelogTab(screen));
        }
    }
}