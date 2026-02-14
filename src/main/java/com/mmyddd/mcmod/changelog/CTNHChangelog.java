package com.mmyddd.mcmod.changelog;

import com.mojang.logging.LogUtils;
import com.mmyddd.mcmod.changelog.client.ChangelogEntry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

@Mod(CTNHChangelog.MOD_ID)
public class CTNHChangelog {
    public static final String MOD_ID = "ctnhchangelog";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CTNHChangelog() {
        LOGGER.info("Registering config for CTNH Changelog");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        FMLJavaModLoadingContext.get().getModEventBus().register(this);
        FMLJavaModLoadingContext.get().getModEventBus().register(Config.class);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            LOGGER.info("Scheduling async changelog load");
            ChangelogEntry.loadAsync();
        });
    }

    @Mod.EventBusSubscriber(modid = CTNHChangelog.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("CTNH Changelog client setup completed");
        }
    }
}