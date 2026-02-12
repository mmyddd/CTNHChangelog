package com.mmyddd.mcmod.changelog;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = CTNHChangelog.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    
    private static final ForgeConfigSpec.ConfigValue<String> CHANGELOG_URL;
    private static final ForgeConfigSpec.BooleanValue ENABLE_CHANGELOG_TAB;
    private static final ForgeConfigSpec.BooleanValue SHOW_ON_JOIN;
    
    static final ForgeConfigSpec SPEC;
    
    private static String changelogUrl;
    private static boolean enableChangelogTab;
    private static boolean showOnJoin;

    public static String getChangelogUrl() {
        return changelogUrl;
    }

    public static boolean isChangelogTabEnabled() {
        return enableChangelogTab;
    }
    
    public static boolean shouldShowOnJoin() {
        return showOnJoin;
    }
    
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        changelogUrl = CHANGELOG_URL.get();
        enableChangelogTab = ENABLE_CHANGELOG_TAB.get();
        showOnJoin = SHOW_ON_JOIN.get();
    }
    
    static {
        CHANGELOG_URL = BUILDER.comment("更新日志JSON文件的远程URL")
                .define("changelogUrl", "");
        
        ENABLE_CHANGELOG_TAB = BUILDER.comment("是否在创建世界界面显示更新日志标签页")
                .define("enableChangelogTab", true);
        
        SHOW_ON_JOIN = BUILDER.comment("是否在加入服务器/世界时显示更新日志提示")
                .define("showOnJoin", true);
        
        SPEC = BUILDER.build();
    }
}