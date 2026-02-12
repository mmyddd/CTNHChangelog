package com.mmyddd.mcmod.changelog;

import com.mojang.logging.LogUtils;
import com.mmyddd.mcmod.changelog.client.ChangelogEntry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

@Mod(CTNHChangelog.MOD_ID)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CTNHChangelog {
    public static final String MOD_ID = "ctnhchangelog";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CTNHChangelog() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ChangelogEntry::loadAsync);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("CTNH Changelog initialized");
    }
}