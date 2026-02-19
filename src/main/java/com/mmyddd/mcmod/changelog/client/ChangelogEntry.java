package com.mmyddd.mcmod.changelog.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmyddd.mcmod.changelog.CTNHChangelog;
import com.mmyddd.mcmod.changelog.Config;
import lombok.Getter;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Getter
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
    private static String footerText = "Hello World!";

    private static final Map<String, Integer> TAG_COLORS = new HashMap<>();

    private static List<ChangelogEntry> ALL_ENTRIES = new ArrayList<>();
    @Getter
    private static boolean isLoaded = false;
    @Getter
    private static boolean isLoadingComplete = false;

    private static boolean configLoaded = false;
    private static String pendingRemoteUrl = null;
    private static CompletableFuture<Void> loadFuture = null;

    private static final String CACHE_DIR_NAME = ".cache";
    private static final String CACHE_FILE_NAME = "changelog_cache.json";

    private static Path cacheDirectory = null;

    public ChangelogEntry(String version, String date, String title, List<String> changes, List<String> types, int color, List<String> tags) {
        this.version = version;
        this.date = date;
        this.title = title;
        this.changes = changes;
        this.types = types != null ? types : new ArrayList<>();
        this.color = color;
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public static String getPendingRemoteUrl() {
        return pendingRemoteUrl;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public static int getTagColor(String tag) {
        return TAG_COLORS.getOrDefault(tag, 0xFF888888);
    }

    public static List<ChangelogEntry> getAllEntries() {
        return ALL_ENTRIES;
    }

    public static Path getCacheDirectory() {
        if (cacheDirectory == null) {
            cacheDirectory = initializeCacheDirectory();
        }
        return cacheDirectory;
    }

    private static Path initializeCacheDirectory() {
        File gameDir = Minecraft.getInstance().gameDirectory;

        Path cacheDir = new File(gameDir, CACHE_DIR_NAME).toPath();

        CTNHChangelog.LOGGER.info("Cache directory: {}", cacheDir.toAbsolutePath());

        try {
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                CTNHChangelog.LOGGER.info("Created cache directory: {}", cacheDir.toAbsolutePath());
            } else {
                CTNHChangelog.LOGGER.info("Cache directory already exists: {}", cacheDir.toAbsolutePath());
            }
            return cacheDir;
        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to create cache directory: {}", e.getMessage());
        }

        CTNHChangelog.LOGGER.warn("Using temp directory for cache");
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), CACHE_DIR_NAME);
        try {
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }
        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to create temp directory: {}", e.getMessage());
        }
        return tempDir;
    }

    public static void initLoader() {
        CTNHChangelog.LOGGER.info("Initializing changelog loader, waiting for config...");
        loadFuture = CompletableFuture.completedFuture(null);
    }

    public static void loadAfterConfig() {
        configLoaded = true;
        String remoteUrl = Config.getChangelogUrl();

        CTNHChangelog.LOGGER.info("Config loaded, remote URL: {}", remoteUrl);

        if (remoteUrl != null && !remoteUrl.isEmpty()) {
            if (loadFuture == null || loadFuture.isDone()) {
                loadFuture = CompletableFuture.runAsync(() -> {
                    boolean success = loadData(remoteUrl);
                    if (success) {
                        isLoaded = true;
                        CTNHChangelog.LOGGER.info("Successfully loaded changelog from remote");
                    } else {
                        CTNHChangelog.LOGGER.warn("Failed to load from remote, falling back to local resources");
                        loadFromResources();
                        isLoaded = true;
                    }
                    isLoadingComplete = true;
                });
            }
        } else {
            CTNHChangelog.LOGGER.info("No remote URL configured, using local resources");
            if (loadFuture == null || loadFuture.isDone()) {
                loadFuture = CompletableFuture.runAsync(() -> {
                    loadFromResources();
                    isLoaded = true;
                    isLoadingComplete = true;
                });
            }
        }
    }

    public static void resetLoaded() {
        isLoaded = false;
        isLoadingComplete = false;
        configLoaded = false;
        pendingRemoteUrl = null;
    }

    private static boolean loadData(String remoteUrl) {
        try {
            String remoteETag = fetchRemoteETag(remoteUrl);

            if (remoteETag == null) {
                CTNHChangelog.LOGGER.warn("Failed to fetch remote ETag, checking cache...");

                Path cacheFile = getCacheDirectory().resolve(CACHE_FILE_NAME);
                if (Files.exists(cacheFile)) {
                    CTNHChangelog.LOGGER.info("Using cached data due to remote unavailable");
                    byte[] cachedData = Files.readAllBytes(cacheFile);
                    return loadFromStream(new ByteArrayInputStream(cachedData));
                } else {
                    CTNHChangelog.LOGGER.warn("No cache available, falling back to local resources");
                    return false;
                }
            }

            CTNHChangelog.LOGGER.info("Remote ETag: {}", remoteETag);

            Path cacheFile = getCacheDirectory().resolve(CACHE_FILE_NAME);
            Path etagFile = getCacheDirectory().resolve(CACHE_FILE_NAME + ".etag");

            if (Files.exists(cacheFile) && Files.exists(etagFile)) {
                byte[] cachedData = Files.readAllBytes(cacheFile);
                String cachedETag = Files.readString(etagFile).trim();

                CTNHChangelog.LOGGER.info("Cached ETag: {}", cachedETag);

                if (remoteETag.equals(cachedETag)) {
                    CTNHChangelog.LOGGER.info("Cache is valid, using cached data");
                    return loadFromStream(new ByteArrayInputStream(cachedData));
                } else {
                    CTNHChangelog.LOGGER.info("Cache ETag mismatch, need to refresh");
                }
            } else {
                CTNHChangelog.LOGGER.info("Cache not found");
            }

            CTNHChangelog.LOGGER.info("Downloading from remote");
            return downloadFromRemote(remoteUrl, remoteETag);

        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to load data: {}", e.getMessage());

            try {
                Path cacheFile = getCacheDirectory().resolve(CACHE_FILE_NAME);
                if (Files.exists(cacheFile)) {
                    CTNHChangelog.LOGGER.info("Using cached data due to error");
                    byte[] cachedData = Files.readAllBytes(cacheFile);
                    return loadFromStream(new ByteArrayInputStream(cachedData));
                }
            } catch (Exception ex) {
                CTNHChangelog.LOGGER.error("Failed to load cache on error recovery: {}", ex.getMessage());
            }

            return false;
        }
    }

    private static boolean downloadFromRemote(String urlStr, String remoteETag) {
        CTNHChangelog.LOGGER.info("Downloading from remote: {}", urlStr);

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
                return false;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }

            byte[] data = baos.toByteArray();
            CTNHChangelog.LOGGER.info("Downloaded {} bytes", data.length);

            boolean success = loadFromStream(new ByteArrayInputStream(data));

            if (success) {
                Path cacheFile = getCacheDirectory().resolve(CACHE_FILE_NAME);
                Path etagFile = getCacheDirectory().resolve(CACHE_FILE_NAME + ".etag");

                Files.write(cacheFile, data);
                Files.writeString(etagFile, remoteETag);

                CTNHChangelog.LOGGER.info("Successfully downloaded and cached changelog with ETag: {}", remoteETag);
            }

            return success;

        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to download from remote: {}", e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String fetchRemoteETag(String urlStr) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "CTNH-Changelog/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            String etag = connection.getHeaderField("ETag");
            if (etag != null) {
                return etag.replace("\"", "").replace("W/", "").trim();
            }
            return null;
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
            } else {
                CTNHChangelog.LOGGER.info("No tagColors defined in JSON");
                TAG_COLORS.clear();
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
            return true;
        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to parse changelog JSON", e);
            return false;
        }
    }

    private static int parseColor(String colorStr) {
        try {
            if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
                String hex = colorStr.substring(2);
                if (hex.length() == 6) {
                    return (int) Long.parseLong("FF" + hex, 16);
                } else if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
            } else if (colorStr.startsWith("#")) {
                return (int) Long.parseLong("FF" + colorStr.substring(1), 16);
            } else {
                return Integer.parseInt(colorStr);
            }
        } catch (Exception e) {
            CTNHChangelog.LOGGER.warn("Failed to parse color: {}, using default white", colorStr);
            return 0xFFFFFFFF;
        }
        return 0;
    }

    private static void loadDefaultEntries() {
        ALL_ENTRIES = new ArrayList<>();
        TAG_COLORS.clear();
        footerText = "Hello World!";

        List<String> changes1 = new ArrayList<>();
        changes1.add("这是一个示例");

        List<String> types1 = new ArrayList<>();
        types1.add("major");

        List<String> tags1 = new ArrayList<>();
        tags1.add("首次发布");
        tags1.add("重大更新");

        ALL_ENTRIES.add(new ChangelogEntry("1.0.0", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "首次发布", changes1, types1, 0xFF55FF55, tags1));

        CTNHChangelog.LOGGER.info("Loaded default changelog entries");
    }
}