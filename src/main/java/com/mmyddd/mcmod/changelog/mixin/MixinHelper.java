package com.mmyddd.mcmod.changelog.mixin;

import com.mmyddd.mcmod.changelog.Config;
import com.mmyddd.mcmod.changelog.client.ChangelogUtils;
import com.mmyddd.mcmod.changelog.client.VersionCheckService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;

/**
 * Mixin 共享逻辑工具类，消除 TitleScreenMixin 和 SelectWorldScreenMixin 中的重复代码。
 */
public final class MixinHelper {

    /** Forge 版本检查图标纹理 */
    public static final ResourceLocation VERSION_CHECK_ICONS =
            ResourceLocation.tryBuild("forge", "textures/gui/version_check_icons.png");

    private MixinHelper() {
        // 工具类，禁止实例化
    }

    /**
     * 检查是否有可用更新。
     * <p>
     * 仅在启用版本检查且检查已完成时才返回真实结果，否则返回 false。
     *
     * @return 是否有更新
     */
    public static boolean checkUpdateStatus() {
        if (Config.isEnableVersionCheck() && VersionCheckService.isCheckDone()) {
            return VersionCheckService.hasUpdate();
        }
        return false;
    }

    /**
     * 在按钮右上角渲染闪烁的更新提示图标。
     *
     * @param graphics GUI 图形上下文
     * @param button   目标按钮
     */
    public static void renderUpdateIcon(GuiGraphics graphics, Button button) {
        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();

        int iconX = x + w - (h / 2 + 4);
        int iconY = y + (h / 2 - 4);

        int sheetOffset = 3;
        int u = sheetOffset * 8;

        boolean blink = (System.currentTimeMillis() / ChangelogUtils.BLINK_INTERVAL & 1) == 1;
        int v = blink ? 8 : 0;

        graphics.blit(VERSION_CHECK_ICONS, iconX, iconY, u, v, 8, 8, 64, 16);
    }
}
