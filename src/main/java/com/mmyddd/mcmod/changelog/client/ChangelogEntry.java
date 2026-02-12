package com.mmyddd.mcmod.changelog.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmyddd.mcmod.changelog.CTNHChangelog;
import com.mmyddd.mcmod.changelog.Config;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChangelogEntry {
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    private final String version;
    private final String date;
    private final String title;
    private final List<String> changes;
    private final List<String> types;
    private final int color;
    private final List<String> tags;

    @Getter
    private static String footerText = "Hello World!"; // 默认值

    private static final Map<String, Integer> TAG_COLORS = new HashMap<>();
    private static boolean defaultColorsLoaded = false;

    private static List<ChangelogEntry> ALL_ENTRIES = new ArrayList<>();
    @Getter
    private static boolean isLoaded = false;

    public ChangelogEntry(String version, String date, String title, List<String> changes, List<String> types, int color, List<String> tags) {
        this.version = version;
        this.date = date;
        this.title = title;
        this.changes = changes;
        this.types = types != null ? types : new ArrayList<>();
        this.color = color;
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public String getVersion() {
        return version;
    }

    public String getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getChanges() {
        return changes;
    }

    public List<String> getTypes() {
        return types;
    }

    public int getColor() {
        return color;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public static int getTagColor(String tag) {
        if (!defaultColorsLoaded && Minecraft.getInstance() != null) {
            loadDefaultTypeColors();
            defaultColorsLoaded = true;
        }
        return TAG_COLORS.getOrDefault(tag, 0xFF888888);
    }

    public static List<ChangelogEntry> getAllEntries() {
        return ALL_ENTRIES;
    }

    public static void loadAsync() {
        CompletableFuture.runAsync(() -> {
            int attempts = 0;
            while (Config.getChangelogUrl() == null || Config.getChangelogUrl().isEmpty()) {
                try {
                    if (attempts++ > 50) {
                        CTNHChangelog.LOGGER.warn("Timeout waiting for config, using local resources");
                        loadFromResources();
                        isLoaded = true;
                        return;
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            String remoteUrl = Config.getChangelogUrl();
            CTNHChangelog.LOGGER.info("Attempting to load changelog from remote URL: {}", remoteUrl);

            if (remoteUrl != null && !remoteUrl.isEmpty()) {
                if (loadFromRemote(remoteUrl)) {
                    CTNHChangelog.LOGGER.info("Loaded {} changelog entries from remote", ALL_ENTRIES.size());
                    CTNHChangelog.LOGGER.info("Loaded {} tag colors from remote", TAG_COLORS.size());
                    isLoaded = true;
                    return;
                }
                CTNHChangelog.LOGGER.warn("Failed to load from remote, falling back to local resources");
            }
            loadFromResources();
            isLoaded = true;
        });
    }

    private static boolean loadFromRemote(String urlStr) {
        CTNHChangelog.LOGGER.info("Attempting to load changelog from remote URL: {}", urlStr);

        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "CTNH-Changelog/1.0");

            int responseCode = connection.getResponseCode();
            CTNHChangelog.LOGGER.info("Remote server response code: {}", responseCode);

            if (responseCode != 200) {
                CTNHChangelog.LOGGER.warn("Remote JSON returned status code: {}", responseCode);
                return false;
            }

            try (InputStream is = connection.getInputStream()) {
                boolean success = loadFromStream(is);
                if (success) {
                    CTNHChangelog.LOGGER.info("Successfully loaded changelog from remote");
                } else {
                    CTNHChangelog.LOGGER.warn("Failed to parse remote changelog");
                }
                return success;
            }
        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to load changelog from remote: {}", e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static void loadFromResources() {
        try {
            InputStream is = ChangelogEntry.class.getResourceAsStream("/changelog.json");
            if (is != null) {
                if (loadFromStream(is)) {
                    CTNHChangelog.LOGGER.info("Loaded {} changelog entries from resources", ALL_ENTRIES.size());
                    CTNHChangelog.LOGGER.info("Loaded {} tag colors from resources", TAG_COLORS.size());
                } else {
                    loadDefaultEntries();
                }
            } else {
                CTNHChangelog.LOGGER.info("Could not find changelog.json in resources, using defaults");
                loadDefaultEntries();
            }
        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to load changelog from resources", e);
            loadDefaultEntries();
        }
    }

    private static boolean loadFromStream(InputStream is) {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("footer")) {
                footerText = root.get("footer").getAsString();
                CTNHChangelog.LOGGER.info("Loaded footer text: {}", footerText);
            }

            if (root.has("tagColors")) {
                JsonObject tagColorsObj = root.getAsJsonObject("tagColors");
                TAG_COLORS.clear();
                for (Map.Entry<String, JsonElement> entry : tagColorsObj.entrySet()) {
                    String tag = entry.getKey();
                    String colorStr = entry.getValue().getAsString();
                    int color = parseColor(colorStr);
                    TAG_COLORS.put(tag, color);
                    CTNHChangelog.LOGGER.debug("Loaded tag color: {} = {}", tag, colorStr);
                }
            }

            JsonArray entriesArray = root.getAsJsonArray("entries");
            List<ChangelogEntry> entries = new ArrayList<>();

            for (JsonElement element : entriesArray) {
                JsonObject obj = element.getAsJsonObject();
                String version = obj.get("version").getAsString();
                String date = obj.has("date") ? obj.get("date").getAsString() : "";
                String title = obj.has("title") ? obj.get("title").getAsString() : "";

                List<String> changes = new ArrayList<>();
                if (obj.has("changes")) {
                    JsonArray changesArray = obj.getAsJsonArray("changes");
                    for (JsonElement change : changesArray) {
                        changes.add(change.getAsString());
                    }
                }

                List<String> types = new ArrayList<>();
                if (obj.has("type")) {
                    JsonElement typeElement = obj.get("type");
                    if (typeElement.isJsonArray()) {
                        JsonArray typeArray = typeElement.getAsJsonArray();
                        for (JsonElement type : typeArray) {
                            types.add(type.getAsString());
                        }
                    } else if (typeElement.isJsonPrimitive()) {
                        types.add(typeElement.getAsString());
                    }
                } else {
                    types.add("patch");
                }

                String colorStr = obj.has("color") ? obj.get("color").getAsString() : "#FFFFFF";
                int color = parseColor(colorStr);

                List<String> tags = new ArrayList<>();
                if (obj.has("tags")) {
                    JsonElement tagsElement = obj.get("tags");
                    if (tagsElement.isJsonArray()) {
                        JsonArray tagsArray = tagsElement.getAsJsonArray();
                        for (JsonElement tag : tagsArray) {
                            tags.add(tag.getAsString());
                        }
                    } else if (tagsElement.isJsonPrimitive()) {
                        tags.add(tagsElement.getAsString());
                    }
                } else if (obj.has("tag")) {
                    tags.add(obj.get("tag").getAsString());
                }

                entries.add(new ChangelogEntry(version, date, title, changes, types, color, tags));
            }

            ALL_ENTRIES = entries;

            if (TAG_COLORS.isEmpty()) {
                loadDefaultTypeColors();
            }

            return true;
        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to parse changelog JSON", e);
            return false;
        }
    }

    private static int parseColor(String colorStr) {
        try {
            if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
                return (int) Long.parseLong(colorStr.substring(2), 16);
            } else if (colorStr.startsWith("#")) {
                return (int) Long.parseLong("FF" + colorStr.substring(1), 16);
            } else {
                return Integer.parseInt(colorStr);
            }
        } catch (Exception e) {
            return 0xFFFFFF;
        }
    }
    private static void loadDefaultTypeColors() {
        String major = Component.translatable("ctnhchangelog.type.major").getString();
        String minor = Component.translatable("ctnhchangelog.type.minor").getString();
        String patch = Component.translatable("ctnhchangelog.type.patch").getString();
        String hotfix = Component.translatable("ctnhchangelog.type.hotfix").getString();
        String danger = Component.translatable("ctnhchangelog.type.danger").getString();

        TAG_COLORS.putIfAbsent(major, 0xFF5555FF);
        TAG_COLORS.putIfAbsent(minor, 0xFF5555FF);
        TAG_COLORS.putIfAbsent(patch, 0xFFFFFF55);
        TAG_COLORS.putIfAbsent(hotfix, 0xFFFF5555);
        TAG_COLORS.putIfAbsent(danger, 0xFFFF5555);

        CTNHChangelog.LOGGER.info("Loaded default tag colors with translated keys");
    }

    private static void loadDefaultEntries() {
        ALL_ENTRIES = new ArrayList<>();
        loadDefaultTypeColors();
        footerText = "Hello World!";

        List<String> changes1 = new ArrayList<>();
        changes1.add("这是一个示例");

        List<String> types1 = new ArrayList<>();
        types1.add("major");

        List<String> tags1 = new ArrayList<>();
        tags1.add("首次发布");
        tags1.add("重大更新");

        ALL_ENTRIES.add(new ChangelogEntry("1.0.0", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "首次发布", changes1, types1, 0x55FF55, tags1));

        CTNHChangelog.LOGGER.info("Loaded default changelog entries");
    }
}