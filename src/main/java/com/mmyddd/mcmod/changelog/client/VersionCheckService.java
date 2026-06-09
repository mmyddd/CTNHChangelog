package com.mmyddd.mcmod.changelog.client;

import com.mmyddd.mcmod.changelog.CTNHChangelog;
import com.mmyddd.mcmod.changelog.Config;
import lombok.Getter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.concurrent.CompletableFuture;

@OnlyIn(Dist.CLIENT)
public class VersionCheckService {
    // volatile 保证多线程间的可见性：EXECUTOR 线程写入，UI 线程读取
    private static volatile boolean hasUpdate = false;
    @Getter
    private static volatile boolean checkDone = false;
    @Getter
    private static volatile String latestChangelogVersion = "";
    private static volatile CompletableFuture<Void> checkFuture = null;

    public static synchronized void checkForUpdate() {
        if (!Config.isEnableVersionCheck()) {
            CTNHChangelog.LOGGER.info("Version check is disabled in config");
            clearResult();
            return;
        }

        if (Config.getModpackVersion().isEmpty() || Config.getSelectedChangelogUrl().isEmpty()) {
            CTNHChangelog.LOGGER.info("ModpackVersion or selected changelog URL not configured, skipping version check");
            clearResult();
            return;
        }

        if (checkFuture != null && !checkFuture.isDone()) {
            CTNHChangelog.LOGGER.debug("Version check already running, reusing current check");
            return;
        }

        checkDone = false;
        checkFuture = ChangelogEntry.getLoadFuture().thenRun(() -> {
            try {
                String changelogVersion = getLoadedChangelogVersion();
                latestChangelogVersion = changelogVersion != null ? changelogVersion : "";

                String currentVersion = Config.getModpackVersion();
                hasUpdate = changelogVersion != null && !changelogVersion.equals(currentVersion);
            } catch (Exception e) {
                CTNHChangelog.LOGGER.error("Failed to check for update", e);
                hasUpdate = false;
                latestChangelogVersion = "";
            } finally {
                checkDone = true;
                CTNHChangelog.LOGGER.info("Update check completed: hasUpdate = {}, currentVersion = {}, latestVersion = {}",
                        hasUpdate, Config.getModpackVersion(), latestChangelogVersion);
            }
        });
    }

    private static String getLoadedChangelogVersion() {
        if (ChangelogEntry.getAllEntries().isEmpty()) {
            CTNHChangelog.LOGGER.warn("No loaded changelog entries available for version check");
            return null;
        }
        return ChangelogEntry.getAllEntries().get(0).getVersion();
    }

    public static boolean hasUpdate() {
        if (!Config.isEnableVersionCheck()) {
            return false;
        }
        return hasUpdate;
    }

    // 关闭线程池，防止应用退出时线程泄漏
    public static void shutdown() {
    }

    public static synchronized void reset() {
        clearResult();
        checkFuture = null;
    }

    private static void clearResult() {
        hasUpdate = false;
        checkDone = true;
        latestChangelogVersion = "";
    }
}
