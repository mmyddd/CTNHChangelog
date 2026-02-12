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
    private Button originalCreateButton = null;

    @Unique
    private Button viewDetailsButton = null;

    @Unique
    private boolean isUpdatingButton = false;

    @Unique
    private int originalButtonX;
    @Unique
    private int originalButtonY;
    @Unique
    private int originalButtonWidth;
    @Unique
    private int originalButtonHeight;
    @Unique
    private Component originalButtonMessage;

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

        this.originalCreateButton = findCreateWorldButton();

        if (this.originalCreateButton != null) {
            this.originalButtonX = this.originalCreateButton.getX();
            this.originalButtonY = this.originalCreateButton.getY();
            this.originalButtonWidth = this.originalCreateButton.getWidth();
            this.originalButtonHeight = this.originalCreateButton.getHeight();
            this.originalButtonMessage = this.originalCreateButton.getMessage();
        }

        createViewDetailsButton();

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

    @Unique
    private void createViewDetailsButton() {
        this.viewDetailsButton = Button.builder(
                        VIEW_DETAILS_TEXT,
                        button -> {
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
                        })
                .bounds(originalButtonX, originalButtonY, originalButtonWidth, originalButtonHeight)
                .build();

        this.viewDetailsButton.visible = false;

        addRenderableWidget(this.viewDetailsButton);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void onRepositionElements(CallbackInfo ci) {
        if (!Config.isChangelogTabEnabled()) return;

        Button currentCreateButton = findCreateWorldButton();

        if (currentCreateButton != null) {
            this.originalCreateButton = currentCreateButton;

            this.originalButtonX = currentCreateButton.getX();
            this.originalButtonY = currentCreateButton.getY();
            this.originalButtonWidth = currentCreateButton.getWidth();
            this.originalButtonHeight = currentCreateButton.getHeight();
            this.originalButtonMessage = currentCreateButton.getMessage();

            if (this.viewDetailsButton != null) {
                this.viewDetailsButton.setX(this.originalButtonX);
                this.viewDetailsButton.setY(this.originalButtonY);
                this.viewDetailsButton.setWidth(this.originalButtonWidth);
                this.viewDetailsButton.setHeight(this.originalButtonHeight);
            }
        }

        updateButtonPosition();
    }

    @Unique
    private void updateButtonPosition() {
        if (isUpdatingButton) return;

        Tab currentTab = tabManager.getCurrentTab();
        if (currentTab instanceof ChangelogTab) {
            if (viewDetailsButton != null && originalCreateButton != null) {
                viewDetailsButton.setX(originalCreateButton.getX());
                viewDetailsButton.setY(originalCreateButton.getY());
                viewDetailsButton.setWidth(originalCreateButton.getWidth());
                viewDetailsButton.setHeight(originalCreateButton.getHeight());
            }
        }
    }

    @Unique
    private void updateButtonForCurrentTab() {
        if (isUpdatingButton) return;

        try {
            isUpdatingButton = true;

            Tab currentTab = tabManager.getCurrentTab();

            if (originalCreateButton == null) {
                originalCreateButton = findCreateWorldButton();
                if (originalCreateButton != null) {
                    originalButtonX = originalCreateButton.getX();
                    originalButtonY = originalCreateButton.getY();
                    originalButtonWidth = originalCreateButton.getWidth();
                    originalButtonHeight = originalCreateButton.getHeight();
                    originalButtonMessage = originalCreateButton.getMessage();
                }
            }

            if (viewDetailsButton == null) {
                createViewDetailsButton();
            }

            if (currentTab instanceof ChangelogTab) {
                switchToViewDetailsButton();
            } else {
                switchToCreateWorldButton();
            }
        } finally {
            isUpdatingButton = false;
        }
    }

    @Unique
    private void switchToViewDetailsButton() {
        if (originalCreateButton == null || viewDetailsButton == null) return;

        originalCreateButton.visible = false;
        viewDetailsButton.visible = true;
        viewDetailsButton.setX(originalButtonX);
        viewDetailsButton.setY(originalButtonY);
        viewDetailsButton.setWidth(originalButtonWidth);
        viewDetailsButton.setHeight(originalButtonHeight);
    }

    @Unique
    private void switchToCreateWorldButton() {
        if (originalCreateButton == null) return;

        originalCreateButton.visible = true;

        originalCreateButton.setX(originalButtonX);
        originalCreateButton.setY(originalButtonY);
        originalCreateButton.setWidth(originalButtonWidth);
        originalCreateButton.setHeight(originalButtonHeight);
        originalCreateButton.setMessage(originalButtonMessage);

        if (viewDetailsButton != null) {
            viewDetailsButton.visible = false;
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