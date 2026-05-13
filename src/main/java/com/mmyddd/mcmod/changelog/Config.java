package com.mmyddd.mcmod.changelog;

import com.mmyddd.mcmod.changelog.client.ChangelogEntry;
import lombok.Getter;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = CTNHChangelog.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    /**
     * 按钮显示位置枚举
     */
    public enum ButtonLocation {
        BOTH,
        TITLE_SCREEN,
        SELECT_WORLD
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<String> CHANGELOG_URL;
    private static final ForgeConfigSpec.ConfigValue<String> MODPACK_VERSION;
    private static final ForgeConfigSpec.BooleanValue ENABLE_CHANGELOG_TAB;
    private static final ForgeConfigSpec.BooleanValue ENABLE_VERSION_CHECK;
    private static final ForgeConfigSpec.EnumValue<ButtonLocation> BUTTON_LOCATION;

    static final ForgeConfigSpec SPEC;

    @Getter
    private static String changelogUrl = "";
    @Getter
    private static String modpackVersion = "";
    private static boolean enableChangelogTab = true;
    @Getter
    private static boolean enableVersionCheck = true; // 默认启用
    @Getter
    private static ButtonLocation buttonLocation = ButtonLocation.BOTH;

    static {
        CHANGELOG_URL = BUILDER
                .comment("更新日志JSON文件的远程URL", "例如: http://example.com/changelog.json")
                .define("changelogUrl", "");

        MODPACK_VERSION = BUILDER
                .comment("当前整合包版本号", "用于与更新日志最新版本对比", "例如: 1.0.0")
                .define("ModpackVersion", "1.0.0");

        ENABLE_CHANGELOG_TAB = BUILDER
                .comment("是否在创建世界界面显示更新日志标签页")
                .define("enableChangelogTab", true);

        ENABLE_VERSION_CHECK = BUILDER
                .comment("是否启用版本更新检查", "如果禁用，将不会对比ModpackVersion和远程最新版本", "也不会显示更新提示")
                .define("enableVersionCheck", true);

        BUTTON_LOCATION = BUILDER
                .comment("按钮显示位置", "BOTH - 在标题界面和选择世界界面都显示", "TITLE_SCREEN - 仅在标题界面显示", "SELECT_WORLD - 仅在选择世界界面显示")
                .defineEnum("buttonLocation", ButtonLocation.BOTH);

        SPEC = BUILDER.build();
    }

    public static boolean isChangelogTabEnabled() {
        return enableChangelogTab;
    }

    public static boolean showButtonOnTitleScreen() {
        return buttonLocation == ButtonLocation.BOTH || buttonLocation == ButtonLocation.TITLE_SCREEN;
    }

    public static boolean showButtonOnSelectWorld() {
        return buttonLocation == ButtonLocation.BOTH || buttonLocation == ButtonLocation.SELECT_WORLD;
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            changelogUrl = CHANGELOG_URL.get();
            modpackVersion = MODPACK_VERSION.get();
            enableChangelogTab = ENABLE_CHANGELOG_TAB.get();
            enableVersionCheck = ENABLE_VERSION_CHECK.get();
            buttonLocation = BUTTON_LOCATION.get();

            CTNHChangelog.LOGGER.info("Config loaded - changelogUrl: {}, modpackVersion: {}, enableChangelogTab: {}, enableVersionCheck: {}, buttonLocation: {}",
                    changelogUrl, modpackVersion, enableChangelogTab, enableVersionCheck, buttonLocation);

            ChangelogEntry.loadAfterConfig();
        }
    }
}