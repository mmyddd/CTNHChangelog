package com.mmyddd.mcmod.changelog;

import com.mmyddd.mcmod.changelog.client.ChangelogEntry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Locale;

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
    private static final ForgeConfigSpec.ConfigValue<String> CHANGELOG_URL_EN;
    private static final ForgeConfigSpec.ConfigValue<String> CHANGELOG_URL_RU;
    private static final ForgeConfigSpec.ConfigValue<String> MODPACK_VERSION;
    private static final ForgeConfigSpec.BooleanValue ENABLE_CHANGELOG_TAB;
    private static final ForgeConfigSpec.BooleanValue ENABLE_VERSION_CHECK;
    private static final ForgeConfigSpec.BooleanValue ENABLE_EDITOR;
    private static final ForgeConfigSpec.EnumValue<ButtonLocation> BUTTON_LOCATION;
    private static final ForgeConfigSpec.IntValue CACHE_TTL_MINUTES;

    static final ForgeConfigSpec SPEC;

    private static String changelogUrl = "";
    private static String changelogUrlEn = "";
    private static String changelogUrlRu = "";
    private static String modpackVersion = "";
    private static boolean enableChangelogTab = true;
    private static boolean enableVersionCheck = true; // 默认启用
    private static boolean enableEditor = false; // 默认关闭
    private static ButtonLocation buttonLocation = ButtonLocation.BOTH;
    private static int cacheTtlMinutes = 60;

    static {
        CHANGELOG_URL = BUILDER
                .comment("默认更新日志JSON文件的远程URL", "非英文/俄文语言优先使用此项", "例如: http://example.com/changelog.json")
                .define("changelogUrl", "");

        CHANGELOG_URL_EN = BUILDER
                .comment("英文更新日志JSON文件的远程URL", "英文语言使用此项，其他语言 URL 为空时也会回退到此项", "例如: http://example.com/changelog_en.json")
                .define("changelogUrlEn", "");

        CHANGELOG_URL_RU = BUILDER
                .comment("俄语更新日志JSON文件的远程URL", "俄语语言使用此项；为空时回退到 changelogUrlEn", "例如: http://example.com/changelog_ru.json")
                .define("changelogUrlRu", "");

        MODPACK_VERSION = BUILDER
                .comment("当前整合包版本号", "用于与更新日志最新版本对比", "例如: 1.0.0")
                .define("ModpackVersion", "1.0.0");

        ENABLE_CHANGELOG_TAB = BUILDER
                .comment("是否在创建世界界面显示更新日志标签页")
                .define("enableChangelogTab", true);

        ENABLE_VERSION_CHECK = BUILDER
                .comment("是否启用版本更新检查", "如果禁用，将不会对比ModpackVersion和远程最新版本", "也不会显示更新提示")
                .define("enableVersionCheck", true);

        ENABLE_EDITOR = BUILDER
                .comment("是否启用游戏内更新日志编辑器", "启用后在更新日志界面显示编辑按钮", "默认关闭")
                .define("enableEditor", false);

        BUTTON_LOCATION = BUILDER
                .comment("按钮显示位置", "BOTH - 在标题界面和选择世界界面都显示", "TITLE_SCREEN - 仅在标题界面显示", "SELECT_WORLD - 仅在选择世界界面显示")
                .defineEnum("buttonLocation", ButtonLocation.BOTH);

        CACHE_TTL_MINUTES = BUILDER
                .comment("远程更新日志缓存有效期（分钟）", "缓存未过期时直接使用本地缓存，不访问远端", "设为 0 可每次都检查远端")
                .defineInRange("cacheTtlMinutes", 60, 0, 10080);

        SPEC = BUILDER.build();
    }

    public static boolean isChangelogTabEnabled() {
        return enableChangelogTab;
    }

    public static String getChangelogUrl() {
        return changelogUrl;
    }

    public static String getChangelogUrlEn() {
        return changelogUrlEn;
    }

    public static String getChangelogUrlRu() {
        return changelogUrlRu;
    }

    public static String getModpackVersion() {
        return modpackVersion;
    }

    public static boolean isEnableVersionCheck() {
        return enableVersionCheck;
    }

    public static boolean isEnableEditor() {
        return enableEditor;
    }

    public static ButtonLocation getButtonLocation() {
        return buttonLocation;
    }

    public static int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public static boolean showButtonOnTitleScreen() {
        return buttonLocation == ButtonLocation.BOTH || buttonLocation == ButtonLocation.TITLE_SCREEN;
    }

    public static boolean showButtonOnSelectWorld() {
        return buttonLocation == ButtonLocation.BOTH || buttonLocation == ButtonLocation.SELECT_WORLD;
    }

    public static String getSelectedChangelogUrl() {
        String languageCode = getGameLanguageCode();
        if (languageCode.startsWith("ru")) {
            return firstNonEmpty(changelogUrlRu, changelogUrlEn, changelogUrl);
        }
        if (languageCode.startsWith("en")) {
            return firstNonEmpty(changelogUrlEn, changelogUrl);
        }
        return firstNonEmpty(changelogUrl, changelogUrlEn);
    }

    public static String getSelectedChangelogLanguage() {
        String languageCode = getGameLanguageCode();
        if (languageCode.startsWith("ru")) {
            if (!changelogUrlRu.isEmpty()) {
                return "ru";
            }
            if (!changelogUrlEn.isEmpty()) {
                return "en";
            }
            return "default";
        }
        if (languageCode.startsWith("en")) {
            return !changelogUrlEn.isEmpty() ? "en" : "default";
        }
        return !changelogUrl.isEmpty() || changelogUrlEn.isEmpty() ? "default" : "en";
    }

    public static String[] getLocalChangelogResourceCandidates() {
        String languageCode = getGameLanguageCode();
        if (languageCode.startsWith("ru")) {
            return new String[]{"/changelog_ru.json", "/changelog_en.json", "/changelog.json"};
        }
        if (languageCode.startsWith("en")) {
            return new String[]{"/changelog_en.json", "/changelog.json"};
        }
        return new String[]{"/changelog.json", "/changelog_en.json"};
    }

    private static String getGameLanguageCode() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.options.languageCode == null) {
            return "en_us";
        }
        return minecraft.options.languageCode.toLowerCase(Locale.ROOT);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            changelogUrl = CHANGELOG_URL.get();
            changelogUrlEn = CHANGELOG_URL_EN.get();
            changelogUrlRu = CHANGELOG_URL_RU.get();
            modpackVersion = MODPACK_VERSION.get();
            enableChangelogTab = ENABLE_CHANGELOG_TAB.get();
            enableVersionCheck = ENABLE_VERSION_CHECK.get();
            enableEditor = ENABLE_EDITOR.get();
            buttonLocation = BUTTON_LOCATION.get();
            cacheTtlMinutes = CACHE_TTL_MINUTES.get();

            CTNHChangelog.LOGGER.info("Config loaded - changelogUrlConfigured: {}, changelogUrlEnConfigured: {}, changelogUrlRuConfigured: {}, selectedChangelogLanguage: {}, selectedChangelogUrlConfigured: {}, modpackVersion: {}, enableChangelogTab: {}, enableVersionCheck: {}, enableEditor: {}, buttonLocation: {}, cacheTtlMinutes: {}",
                    !changelogUrl.isEmpty(), !changelogUrlEn.isEmpty(), !changelogUrlRu.isEmpty(), getSelectedChangelogLanguage(), !getSelectedChangelogUrl().isEmpty(), modpackVersion, enableChangelogTab, enableVersionCheck, enableEditor, buttonLocation, cacheTtlMinutes);

            ChangelogEntry.reloadAfterConfig();
        }
    }
}
