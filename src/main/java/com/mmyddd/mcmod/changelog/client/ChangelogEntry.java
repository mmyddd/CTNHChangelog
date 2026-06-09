package com.mmyddd.mcmod.changelog.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmyddd.mcmod.changelog.CTNHChangelog;
import com.mmyddd.mcmod.changelog.Config;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChangelogEntry {
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;
    private static final String NO_REMOTE_ETAG = "";

    private enum LoadSource {
        REMOTE,
        CACHE,
        UNAVAILABLE
    }

    private final String version;
    private final String date;
    private final String title;
    private final List<String> changes;
    private final List<String> types;
    private final int color;
    private final List<String> tags;

    // volatile 保证跨线程可见性：后台线程写入，渲染线程读取
    private static volatile String footerText = "Hello World!";

    // volatile 引用保证原子替换：构建完整的新 Map 后一次性替换引用
    private static volatile Map<String, Integer> TAG_COLORS = new ConcurrentHashMap<>();

    // volatile 保证跨线程可见性：后台线程赋新列表，渲染线程遍历
    private static volatile List<ChangelogEntry> ALL_ENTRIES = new ArrayList<>();
    private static volatile boolean isLoaded = false;
    private static volatile boolean isLoadingComplete = false;
    private static volatile String loadedLanguage = "";

    private static volatile CompletableFuture<Void> loadFuture = null;

    private static final String CACHE_DIR_NAME = ".cache";
    private static final String CACHE_FILE_PREFIX = "changelog_cache";

    private static volatile Path cacheDirectory = null;

    public ChangelogEntry(String version, String date, String title, List<String> changes, List<String> types, int color, List<String> tags) {
        this.version = version;
        this.date = date;
        this.title = title;
        // 防御性拷贝，防止外部修改影响内部状态
        this.changes = new ArrayList<>(changes);
        this.types = types != null ? new ArrayList<>(types) : new ArrayList<>();
        this.color = color;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
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
        return new ArrayList<>(changes);
    }

    public List<String> getTypes() {
        return new ArrayList<>(types);
    }

    public int getColor() {
        return color;
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public static String getFooterText() {
        return footerText;
    }

    public static boolean isLoaded() {
        return isLoaded;
    }

    public static boolean isLoadingComplete() {
        return isLoadingComplete;
    }

    public static int getTagColor(String tag) {
        return TAG_COLORS.getOrDefault(tag, 0xFF888888);
    }

    public static Map<String, Integer> getTagColorsMap() {
        return new HashMap<>(TAG_COLORS);
    }

    public static String getCacheFileNameForCurrentLanguage() {
        return getCacheFileName();
    }

    public static List<ChangelogEntry> getAllEntries() {
        return ALL_ENTRIES;
    }

    public static synchronized CompletableFuture<Void> getLoadFuture() {
        if (loadFuture == null) {
            return CompletableFuture.completedFuture(null);
        }
        return loadFuture;
    }

    public static synchronized Path getCacheDirectory() {
        if (cacheDirectory == null) {
            cacheDirectory = initializeCacheDirectory();
        }
        return cacheDirectory;
    }

    private static Path initializeCacheDirectory() {
        Path cacheDir = FMLPaths.GAMEDIR.get().resolve(CACHE_DIR_NAME);

        CTNHChangelog.LOGGER.info("Cache directory: {}", cacheDir.toAbsolutePath());

        try {
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                CTNHChangelog.LOGGER.info("Created cache directory: {}", cacheDir.toAbsolutePath());
            } else {
                CTNHChangelog.LOGGER.debug("Cache directory already exists: {}", cacheDir.toAbsolutePath());
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

    public static synchronized void loadAfterConfig() {
        if (loadFuture != null && !loadFuture.isDone()) {
            CTNHChangelog.LOGGER.debug("Changelog load already running, reusing current load");
            return;
        }

        if (hasFreshLoadedData()) {
            CTNHChangelog.LOGGER.info("Changelog data already loaded and cache is fresh, reusing in-memory data");
            return;
        }

        startLoadAfterConfig();
    }

    public static synchronized void reloadAfterConfig() {
        if (loadFuture != null && !loadFuture.isDone()) {
            CTNHChangelog.LOGGER.debug("Changelog load already running, reusing current load");
            return;
        }

        resetLoaded();
        startLoadAfterConfig();
    }

    private static void startLoadAfterConfig() {
        String remoteUrl = Config.getSelectedChangelogUrl();
        String selectedLanguage = Config.getSelectedChangelogLanguage();

        CTNHChangelog.LOGGER.info("Config loaded, selected changelog language: {}, remote URL configured: {}",
                selectedLanguage, !remoteUrl.isEmpty());

        if (remoteUrl != null && !remoteUrl.isEmpty()) {
            loadFuture = CompletableFuture.runAsync(() -> {
                LoadSource loadSource = loadData(remoteUrl, selectedLanguage);
                if (loadSource != LoadSource.UNAVAILABLE) {
                    isLoaded = true;
                    loadedLanguage = selectedLanguage;
                    CTNHChangelog.LOGGER.info("Successfully loaded changelog from {}", loadSource.name().toLowerCase(Locale.ROOT));
                } else {
                    CTNHChangelog.LOGGER.warn("Failed to load from remote, falling back to local resources");
                    loadFromResources(selectedLanguage);
                    isLoaded = true;
                    loadedLanguage = selectedLanguage;
                }
                isLoadingComplete = true;
            });
        } else {
            CTNHChangelog.LOGGER.info("No remote URL configured, using local resources");
            loadFuture = CompletableFuture.runAsync(() -> {
                loadFromResources(selectedLanguage);
                isLoaded = true;
                loadedLanguage = selectedLanguage;
                isLoadingComplete = true;
            });
        }
    }

    public static synchronized void ensureLoadedForCurrentConfig() {
        if (loadFuture != null && !loadFuture.isDone()) {
            CTNHChangelog.LOGGER.debug("Changelog load already running, reusing current load");
            return;
        }

        if (hasFreshLoadedData()) {
            CTNHChangelog.LOGGER.info("Changelog data already loaded and cache is fresh, reusing in-memory data");
            return;
        }

        resetLoaded();
        loadAfterConfig();
    }

    private static boolean hasFreshLoadedData() {
        return isLoaded && isLoadingComplete
                && loadedLanguage.equals(Config.getSelectedChangelogLanguage())
                && isCacheFresh();
    }

    public static void resetLoaded() {
        isLoaded = false;
        isLoadingComplete = false;
        loadedLanguage = "";
    }

    private static LoadSource loadData(String remoteUrl, String language) {
        try {
            if (loadFromCacheWhenFresh(language)) {
                return LoadSource.CACHE;
            }

            String remoteETag = fetchRemoteETag(remoteUrl);

            if (remoteETag == null) {
                CTNHChangelog.LOGGER.warn("Failed to fetch remote ETag, checking cache...");

                Path cacheFile = getCacheFile(language);
                if (Files.exists(cacheFile)) {
                    CTNHChangelog.LOGGER.info("Using cached data due to remote unavailable");
                    byte[] cachedData = Files.readAllBytes(cacheFile);
                    return loadFromStream(new ByteArrayInputStream(cachedData)) ? LoadSource.CACHE : LoadSource.UNAVAILABLE;
                } else {
                    CTNHChangelog.LOGGER.warn("No cache available, falling back to local resources");
                    return LoadSource.UNAVAILABLE;
                }
            }

            if (remoteETag.isEmpty()) {
                CTNHChangelog.LOGGER.info("Remote changelog has no ETag, refreshing cache with GET");
                return downloadFromRemote(remoteUrl, null, language) ? LoadSource.REMOTE : LoadSource.UNAVAILABLE;
            }

            CTNHChangelog.LOGGER.info("Remote ETag: {}", remoteETag);

            Path cacheFile = getCacheFile(language);
            Path etagFile = getCacheETagFile(language);

            if (Files.exists(cacheFile) && Files.exists(etagFile)) {
                byte[] cachedData = Files.readAllBytes(cacheFile);
                String cachedETag = Files.readString(etagFile).trim();

                CTNHChangelog.LOGGER.info("Cached ETag: {}", cachedETag);

                if (remoteETag.equals(cachedETag)) {
                    CTNHChangelog.LOGGER.info("Cache is valid, using cached data");
                    boolean success = loadFromStream(new ByteArrayInputStream(cachedData));
                    if (success) {
                        refreshCacheTimestamp(cacheFile);
                    }
                    return success ? LoadSource.CACHE : LoadSource.UNAVAILABLE;
                } else {
                    CTNHChangelog.LOGGER.info("Cache ETag mismatch, need to refresh");
                }
            } else {
                CTNHChangelog.LOGGER.info("Cache not found");
            }

            CTNHChangelog.LOGGER.info("Downloading from remote");
            return downloadFromRemote(remoteUrl, remoteETag, language) ? LoadSource.REMOTE : LoadSource.UNAVAILABLE;

        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to load data: {}", e.getMessage());

            try {
                Path cacheFile = getCacheFile(language);
                if (Files.exists(cacheFile)) {
                    CTNHChangelog.LOGGER.info("Using cached data due to error");
                    byte[] cachedData = Files.readAllBytes(cacheFile);
                    return loadFromStream(new ByteArrayInputStream(cachedData)) ? LoadSource.CACHE : LoadSource.UNAVAILABLE;
                }
            } catch (Exception ex) {
                CTNHChangelog.LOGGER.error("Failed to load cache on error recovery: {}", ex.getMessage());
            }

            return LoadSource.UNAVAILABLE;
        }
    }

    private static boolean downloadFromRemote(String urlStr, String remoteETag, String language) {
        CTNHChangelog.LOGGER.info("Downloading remote changelog for selected language: {}",
                language);

        HttpURLConnection connection = null;
        try {
            // 使用 URI.create 替代已弃用的 new URL 构造函数
            URL url = URI.create(urlStr).toURL();
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
                Path cacheFile = getCacheFile(language);
                Path etagFile = getCacheETagFile(language);

                Files.write(cacheFile, data);
                if (remoteETag != null && !remoteETag.isEmpty()) {
                    Files.writeString(etagFile, remoteETag);
                } else {
                    Files.deleteIfExists(etagFile);
                }

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
            // 使用 URI.create 替代已弃用的 new URL 构造函数
            URL url = URI.create(urlStr).toURL();
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
            return NO_REMOTE_ETAG;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static void loadFromResources() {
        loadFromResources(Config.getSelectedChangelogLanguage());
    }

    private static void loadFromResources(String language) {
        for (String resourcePath : getLocalChangelogResourceCandidates(language)) {
            try (InputStream is = ChangelogEntry.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    CTNHChangelog.LOGGER.info("Could not find {} in resources", resourcePath);
                    continue;
                }
                if (loadFromStream(is)) {
                    CTNHChangelog.LOGGER.info("Loaded {} changelog entries from resources: {}", ALL_ENTRIES.size(), resourcePath);
                    CTNHChangelog.LOGGER.info("Loaded {} tag colors from resources", TAG_COLORS.size());
                    return;
                }
                CTNHChangelog.LOGGER.warn("Failed to parse resource changelog: {}", resourcePath);
            } catch (Exception e) {
                CTNHChangelog.LOGGER.error("Failed to load changelog from resources: {}", resourcePath, e);
            }
        }
        CTNHChangelog.LOGGER.info("Could not load any resource changelog, using defaults");
        loadDefaultEntries();
    }

    private static Path getCacheFile(String language) {
        return getCacheDirectory().resolve(getCacheFileName(language));
    }

    private static Path getCacheETagFile(String language) {
        return getCacheDirectory().resolve(getCacheFileName(language) + ".etag");
    }

    private static boolean loadFromCacheWhenFresh(String language) {
        Path cacheFile = getCacheFile(language);
        if (isCacheFresh(language)) {
            try {
                CTNHChangelog.LOGGER.info("Using fresh changelog cache");
                byte[] cachedData = Files.readAllBytes(cacheFile);
                return loadFromStream(new ByteArrayInputStream(cachedData));
            } catch (Exception e) {
                CTNHChangelog.LOGGER.warn("Failed to load fresh cache, checking remote instead: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    private static void refreshCacheTimestamp(Path cacheFile) {
        try {
            Files.setLastModifiedTime(cacheFile, FileTime.from(Instant.now()));
        } catch (Exception e) {
            CTNHChangelog.LOGGER.warn("Failed to refresh changelog cache timestamp: {}", e.getMessage());
        }
    }

    private static boolean isCacheFresh() {
        return isCacheFresh(Config.getSelectedChangelogLanguage());
    }

    private static boolean isCacheFresh(String language) {
        int ttlMinutes = Config.getCacheTtlMinutes();
        if (ttlMinutes <= 0) {
            return false;
        }

        Path cacheFile = getCacheFile(language);
        if (!Files.exists(cacheFile)) {
            return false;
        }

        try {
            Instant lastModified = Files.getLastModifiedTime(cacheFile).toInstant();
            Duration cacheAge = Duration.between(lastModified, Instant.now());
            if (cacheAge.compareTo(Duration.ofMinutes(ttlMinutes)) > 0) {
                CTNHChangelog.LOGGER.info("Changelog cache expired after {} minutes", cacheAge.toMinutes());
                return false;
            }

            CTNHChangelog.LOGGER.info("Changelog cache is fresh, age: {} minutes", cacheAge.toMinutes());
            return true;
        } catch (Exception e) {
            CTNHChangelog.LOGGER.warn("Failed to check changelog cache freshness: {}", e.getMessage());
            return false;
        }
    }

    private static String getCacheFileName() {
        return getCacheFileName(Config.getSelectedChangelogLanguage());
    }

    private static String getCacheFileName(String language) {
        if (language.equals("ru") || language.equals("en")) {
            return CACHE_FILE_PREFIX + "_" + language + ".json";
        }
        return CACHE_FILE_PREFIX + ".json";
    }

    private static String[] getLocalChangelogResourceCandidates(String language) {
        if (language.equals("ru")) {
            return new String[]{"/changelog_ru.json", "/changelog_en.json", "/changelog.json"};
        }
        if (language.equals("en")) {
            return new String[]{"/changelog_en.json", "/changelog.json"};
        }
        return new String[]{"/changelog.json", "/changelog_en.json"};
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
                // 先构建完整的临时 Map，再一次性替换引用，避免渲染线程读到不完整状态
                Map<String, Integer> newTagColors = new ConcurrentHashMap<>();
                for (Map.Entry<String, JsonElement> entry : tagColorsObj.entrySet()) {
                    String tag = entry.getKey();
                    String colorStr = entry.getValue().getAsString();
                    int color = parseColor(colorStr);
                    newTagColors.put(tag, color);
                    CTNHChangelog.LOGGER.debug("Loaded tag color: {} = {}", tag, colorStr);
                }
                TAG_COLORS = newTagColors;
            } else {
                CTNHChangelog.LOGGER.info("No tagColors defined in JSON");
                TAG_COLORS = new ConcurrentHashMap<>();
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

            // 使用不可变列表包装，保证线程安全的同时防止外部修改
            ALL_ENTRIES = Collections.unmodifiableList(entries);
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
                // 0x 前缀但十六进制长度既非6也非8，视为无效格式
                CTNHChangelog.LOGGER.warn("Invalid hex color length: {}, using default white", colorStr);
                return 0xFFFFFFFF;
            } else if (colorStr.startsWith("#")) {
                String hex = colorStr.substring(1);
                if (hex.length() == 6) {
                    return (int) Long.parseLong("FF" + hex, 16);
                } else if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
                // # 前缀但十六进制长度既非6也非8，视为无效格式
                CTNHChangelog.LOGGER.warn("Invalid hex color length: {}, using default white", colorStr);
                return 0xFFFFFFFF;
            } else {
                return Integer.parseInt(colorStr);
            }
        } catch (Exception e) {
            CTNHChangelog.LOGGER.warn("Failed to parse color: {}, using default white", colorStr);
            return 0xFFFFFFFF;
        }
    }

    private static void loadDefaultEntries() {
        TAG_COLORS = new ConcurrentHashMap<>();
        footerText = "Hello World!";

        List<String> changes1 = new ArrayList<>();
        changes1.add("这是一个示例");

        List<String> types1 = new ArrayList<>();
        types1.add("major");

        List<String> tags1 = new ArrayList<>();
        tags1.add("首次发布");
        tags1.add("重大更新");

        // 先构建列表，再用不可变包装赋值，保证线程安全
        List<ChangelogEntry> defaultEntries = new ArrayList<>();
        defaultEntries.add(new ChangelogEntry("1.0.0", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "首次发布", changes1, types1, 0xFF55FF55, tags1));
        ALL_ENTRIES = Collections.unmodifiableList(defaultEntries);

        CTNHChangelog.LOGGER.info("Loaded default changelog entries");
    }
}
