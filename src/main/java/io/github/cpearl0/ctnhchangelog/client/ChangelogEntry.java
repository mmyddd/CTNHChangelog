package io.github.cpearl0.ctnhchangelog.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cpearl0.ctnhchangelog.CTNHChangelog;
import io.github.cpearl0.ctnhchangelog.Config;

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
    private final String type;
    private final int color;
    private final List<String> tags;

    // ✅ 全局标签颜色映射
    private static final Map<String, Integer> TAG_COLORS = new HashMap<>();

    private static List<ChangelogEntry> ALL_ENTRIES = new ArrayList<>();
    private static boolean isLoaded = false;

    public ChangelogEntry(String version, String date, String title, List<String> changes, String type, int color, List<String> tags) {
        this.version = version;
        this.date = date;
        this.title = title;
        this.changes = changes;
        this.type = type;
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

    public String getType() {
        return type;
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

    // ✅ 获取标签颜色（静态方法）
    public static int getTagColor(String tag) {
        return TAG_COLORS.getOrDefault(tag, 0xFF888888); // 默认灰色
    }

    public static List<ChangelogEntry> getAllEntries() {
        return ALL_ENTRIES;
    }

    public static boolean isLoaded() {
        return isLoaded;
    }

    public static void loadAsync() {
        CompletableFuture.runAsync(() -> {
            String remoteUrl = Config.getChangelogUrl();
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
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "CTNH-Changelog/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                CTNHChangelog.LOGGER.warn("Remote JSON returned status code: {}", responseCode);
                return false;
            }

            try (InputStream is = connection.getInputStream()) {
                return loadFromStream(is);
            }
        } catch (Exception e) {
            CTNHChangelog.LOGGER.warn("Failed to load changelog from remote: {}", e.getMessage());
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

            // ✅ 加载标签颜色配置
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

            // 加载更新日志条目
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

                String type = obj.has("type") ? obj.get("type").getAsString() : "update";
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

                entries.add(new ChangelogEntry(version, date, title, changes, type, color, tags));
            }

            ALL_ENTRIES = entries;

            // ✅ 如果没有定义任何标签颜色，使用默认配置
            if (TAG_COLORS.isEmpty()) {
                loadDefaultTagColors();
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

    // ✅ 默认标签颜色（仅当JSON中没有定义时使用）
    private static void loadDefaultTagColors() {
        TAG_COLORS.put("热销", 0xFF55FF55);
        TAG_COLORS.put("推荐", 0xFF55FF55);
        TAG_COLORS.put("重大更新", 0xFF5555FF);
        TAG_COLORS.put("新版", 0xFF5555FF);
        TAG_COLORS.put("修复", 0xFFFFFF55);
        TAG_COLORS.put("补丁", 0xFFFFFF55);
        TAG_COLORS.put("紧急修复", 0xFFFF5555);
        TAG_COLORS.put("热修复", 0xFFFF5555);
        TAG_COLORS.put("开发组招新", 0xFFFFAA00);
        TAG_COLORS.put("存档迁移", 0xFFFF55FF);
        CTNHChangelog.LOGGER.info("Loaded default tag colors");
    }

    private static void loadDefaultEntries() {
        ALL_ENTRIES = new ArrayList<>();
        loadDefaultTagColors();

        List<String> changes1 = new ArrayList<>();
        changes1.add("新增更新日志查看功能");
        changes1.add("优化界面显示效果");
        changes1.add("修复已知问题");

        List<String> tags1 = new ArrayList<>();
        tags1.add("首次发布");
        tags1.add("重大更新");

        ALL_ENTRIES.add(new ChangelogEntry("1.0.0", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "首次发布", changes1, "major", 0x55FF55, tags1));

        CTNHChangelog.LOGGER.info("Loaded default changelog entries");
    }
}