package com.mmyddd.mcmod.changelog;

import lombok.Getter;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = CTNHChangelog.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<String> CHANGELOG_URL;
    private static final ForgeConfigSpec.BooleanValue ENABLE_CHANGELOG_TAB;

    static final ForgeConfigSpec SPEC;

    @Getter
    private static String changelogUrl = "";
    private static boolean enableChangelogTab = true;

    static {
        CHANGELOG_URL = BUILDER
                .comment("更新日志JSON文件的远程URL", "例如: http://example.com/changelog.json")
                .define("changelogUrl", "");

        ENABLE_CHANGELOG_TAB = BUILDER
                .comment("是否在创建世界界面显示更新日志标签页")
                .define("enableChangelogTab", true);

        SPEC = BUILDER.build();
    }

    public static boolean isChangelogTabEnabled() {
        return enableChangelogTab;
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            changelogUrl = CHANGELOG_URL.get();
            enableChangelogTab = ENABLE_CHANGELOG_TAB.get();
            CTNHChangelog.LOGGER.info("Config loaded - changelogUrl: {}, enableChangelogTab: {}",
                    changelogUrl, enableChangelogTab);
        }
    }
}