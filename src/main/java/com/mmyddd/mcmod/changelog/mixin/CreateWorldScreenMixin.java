// CreateWorldScreenMixin.java
package com.mmyddd.mcmod.changelog.mixin;

import com.mmyddd.mcmod.changelog.CTNHChangelog;
import com.mmyddd.mcmod.changelog.Config;
import com.mmyddd.mcmod.changelog.client.ChangelogList;
import com.mmyddd.mcmod.changelog.client.ChangelogScreen;
import com.mmyddd.mcmod.changelog.client.ChangelogTab;
import com.mmyddd.mcmod.changelog.mixin.accessor.TabNavigationBarAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Collections;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    @Shadow
    @Final
    private TabManager tabManager;

    @Unique
    private static final Component VIEW_DETAILS_TEXT = Component.translatable("ctnhchangelog.button.view_changelog");

    @Unique
    private static final Component CREATE_WORLD_TEXT = Component.translatable("selectWorld.create");

    @Unique
    private Component originalCreateButtonText = null;

    @Unique
    private Button.OnPress originalCreateButtonCallback = null;

    @Unique
    private Button currentActionButton = null;

    @Unique
    private boolean isUpdatingButton = false;

    protected CreateWorldScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private Button findCreateWorldButton() {
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button) {
                Component message = button.getMessage();
                String messageText = message.getString();
                if (messageText.contains("创建") ||
                        messageText.contains("Create") ||
                        message.equals(CREATE_WORLD_TEXT) ||
                        messageText.contains("查看详情") ||
                        messageText.contains("View Details") ||
                        message.equals(VIEW_DETAILS_TEXT)) {
                    return button;
                }
            }
        }
        return null;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!Config.isChangelogTabEnabled()) return;

        resetButtonState();
        updateButtonForCurrentTab();

        if (ChangelogTab.shouldOpenChangelogTab) {
            ChangelogTab.shouldOpenChangelogTab = false;
            for (Tab tab : getAllTabs()) {
                if (tab instanceof ChangelogTab) {
                    tabManager.setCurrentTab(tab, true);
                    break;
                }
            }
        }
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void onRepositionElements(CallbackInfo ci) {
        if (!Config.isChangelogTabEnabled()) return;

        updateButtonPosition();
    }

    @Unique
    private void resetButtonState() {
        originalCreateButtonText = null;
        originalCreateButtonCallback = null;
        currentActionButton = null;
    }

    @Unique
    private void updateButtonPosition() {
        if (isUpdatingButton) return;

        Button currentButton = findCreateWorldButton();
        if (currentButton != null && currentActionButton != null) {
            Tab currentTab = tabManager.getCurrentTab();
            if (currentTab instanceof ChangelogTab) {
                if (currentActionButton.getMessage().getString().equals(VIEW_DETAILS_TEXT.getString())) {
                    currentActionButton.setX(currentButton.getX());
                    currentActionButton.setY(currentButton.getY());
                }
            }
        }
    }

    @Unique
    private void updateButtonForCurrentTab() {
        if (isUpdatingButton) return;

        try {
            isUpdatingButton = true;

            Tab currentTab = tabManager.getCurrentTab();
            Button currentButton = findCreateWorldButton();

            if (currentButton != null) {
                if (currentTab instanceof ChangelogTab) {
                    switchToViewDetailsButton(currentButton);
                } else {
                    switchToCreateWorldButton(currentButton);
                }
            }
        } finally {
            isUpdatingButton = false;
        }
    }

    @Unique
    private void switchToViewDetailsButton(Button currentButton) {
        if (originalCreateButtonText == null) {
            originalCreateButtonText = currentButton.getMessage();
        }
        if (originalCreateButtonCallback == null) {
            try {
                Field pressField = Button.class.getDeclaredField("onPress");
                pressField.setAccessible(true);
                originalCreateButtonCallback = (Button.OnPress) pressField.get(currentButton);
            } catch (Exception e) {
                CTNHChangelog.LOGGER.error("Failed to access button onPress field", e);
            }
        }

        if (!currentButton.getMessage().getString().equals(VIEW_DETAILS_TEXT.getString())) {
            removeWidget(currentButton);

            Button viewButton = Button.builder(VIEW_DETAILS_TEXT, button -> {
                Tab currentTab = tabManager.getCurrentTab();
                if (currentTab instanceof ChangelogTab changelogTab) {
                    ChangelogList list = changelogTab.getChangelogList();
                    ChangelogList.Entry selected = list != null ? list.getSelected() : null;
                    if (selected != null) {
                        Minecraft.getInstance().setScreen(
                                new ChangelogScreen(
                                        selected.getEntry(),
                                        (CreateWorldScreen) (Object) CreateWorldScreenMixin.this
                                )
                        );
                    }
                }
            }).bounds(currentButton.getX(), currentButton.getY(),
                    currentButton.getWidth(), currentButton.getHeight()).build();

            addRenderableWidget(viewButton);
            currentActionButton = viewButton;
        }
    }

    @Unique
    private void switchToCreateWorldButton(Button currentButton) {
        if (originalCreateButtonText != null &&
                originalCreateButtonCallback != null &&
                currentActionButton == currentButton &&
                currentButton.getMessage().getString().equals(VIEW_DETAILS_TEXT.getString())) {

            removeWidget(currentButton);

            Button originalButton = Button.builder(
                            originalCreateButtonText,
                            originalCreateButtonCallback)
                    .bounds(currentButton.getX(), currentButton.getY(),
                            currentButton.getWidth(), currentButton.getHeight())
                    .build();

            addRenderableWidget(originalButton);
            currentActionButton = originalButton;
        }
    }

    @Unique
    private Iterable<Tab> getAllTabs() {
        for (GuiEventListener child : children()) {
            if (child instanceof TabNavigationBar navigationBar) {
                return ((TabNavigationBarAccessor) navigationBar).getTabs();
            }
        }
        return Collections.emptyList();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!Config.isChangelogTabEnabled()) return;

        updateButtonForCurrentTab();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!Config.isChangelogTabEnabled()) return;

        Tab currentTab = tabManager.getCurrentTab();
        if (currentTab instanceof ChangelogTab changelogTab) {
            changelogTab.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!Config.isChangelogTabEnabled()) return super.mouseClicked(mouseX, mouseY, button);

        Tab currentTab = tabManager.getCurrentTab();
        if (currentTab instanceof ChangelogTab changelogTab) {
            ChangelogList list = changelogTab.getChangelogList();
            if (list != null && list.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!Config.isChangelogTabEnabled()) return super.mouseScrolled(mouseX, mouseY, delta);

        Tab currentTab = tabManager.getCurrentTab();
        if (currentTab instanceof ChangelogTab changelogTab) {
            ChangelogList list = changelogTab.getChangelogList();
            if (list != null && list.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}