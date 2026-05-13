package com.mmyddd.mcmod.changelog.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmyddd.mcmod.changelog.CTNHChangelog;
import com.mmyddd.mcmod.changelog.Config;
import lombok.Getter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OnlyIn(Dist.CLIENT)
public class VersionCheckService {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    // volatile 保证多线程间的可见性：EXECUTOR 线程写入，UI 线程读取
    private static volatile boolean hasUpdate = false;
    @Getter
    private static volatile boolean checkDone = false;
    @Getter
    private static volatile String latestChangelogVersion = "";

    public static void checkForUpdate() {
        if (!Config.isEnableVersionCheck()) {
            CTNHChangelog.LOGGER.info("Version check is disabled in config");
            checkDone = true;
            return;
        }

        if (Config.getModpackVersion().isEmpty() || Config.getChangelogUrl().isEmpty()) {
            CTNHChangelog.LOGGER.info("ModpackVersion or changelogUrl not configured, skipping version check");
            checkDone = true;
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                String changelogVersion = fetchChangelogVersion();
                latestChangelogVersion = changelogVersion != null ? changelogVersion : "";

                String currentVersion = Config.getModpackVersion();
                return changelogVersion != null && !changelogVersion.equals(currentVersion);
            } catch (Exception e) {
                CTNHChangelog.LOGGER.error("Failed to check for update", e);
                return false;
            }
        }, EXECUTOR).thenAccept(result -> {
            hasUpdate = result;
            checkDone = true;
            CTNHChangelog.LOGGER.info("Update check completed: hasUpdate = {}, currentVersion = {}, latestVersion = {}",
                    result, Config.getModpackVersion(), latestChangelogVersion);
        });
    }

    private static String fetchChangelogVersion() throws Exception {
        String urlStr = Config.getChangelogUrl();
        if (urlStr.isEmpty()) {
            return null;
        }

        // 使用 URI.create().toURL() 替代已弃用的 new URL() 构造函数
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "CTNH-Changelog/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                CTNHChangelog.LOGGER.warn("Failed to fetch changelog.json, response code: {}", responseCode);
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("entries") && !root.getAsJsonArray("entries").isEmpty()) {
                    return root.getAsJsonArray("entries").get(0).getAsJsonObject().get("version").getAsString();
                }
            }

            CTNHChangelog.LOGGER.warn("No entries found in changelog.json");
            return null;
        } finally {
            // 确保 HttpURLConnection 在所有路径上都被关闭，防止连接泄漏
            conn.disconnect();
        }
    }

    public static boolean hasUpdate() {
        if (!Config.isEnableVersionCheck()) {
            return false;
        }
        return hasUpdate;
    }

    // 关闭线程池，防止应用退出时线程泄漏
    public static void shutdown() {
        EXECUTOR.shutdown();
    }

    public static void reset() {
        hasUpdate = false;
        checkDone = false;
        latestChangelogVersion = "";
    }
}