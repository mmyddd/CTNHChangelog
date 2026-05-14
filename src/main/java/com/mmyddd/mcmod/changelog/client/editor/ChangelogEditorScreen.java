package com.mmyddd.mcmod.changelog.client.editor;

import com.google.gson.*;
import com.mmyddd.mcmod.changelog.CTNHChangelog;
import com.mmyddd.mcmod.changelog.client.ChangelogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ChangelogEditorScreen extends Screen {
    private static final int TAB_BAR_Y = 10;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_WIDTH = 100;
    private static final int TAB_GAP = 4;
    private static final int MARGIN = 10;
    private static final int CONTENT_Y = 35;

    private final Screen parentScreen;
    private int currentTab;

    private List<EditableEntry> entries;
    private final LinkedHashMap<String, Integer> tagColors = new LinkedHashMap<>();
    private String footerText = "";

    private EditorEntriesTab entriesTab;
    private EditorTagsTab tagsTab;
    private EditorFooterTab footerTab;

    private String toastMessage;
    private long toastExpiry;

    public ChangelogEditorScreen(Screen parent) {
        super(Component.translatable("ctnhchangelog.editor.title"));
        this.parentScreen = parent;
    }

    // region 数据访问器

    public List<EditableEntry> getEntries() {
        return entries;
    }

    public Map<String, Integer> getTagColors() {
        return tagColors;
    }

    public String getFooterText() {
        return footerText;
    }

    public void setFooterText(String text) {
        this.footerText = text;
    }

    public void setTagColor(String tag, int color) {
        tagColors.put(tag, color);
    }

    public void removeTagColor(String tag) {
        tagColors.remove(tag);
    }

    public void addTagColor(String tag, int color) {
        tagColors.put(tag, color);
    }

    // 公共包装方法（供标签页类调用，因为 Screen 的 addRenderableWidget/removeWidget 是 protected）
    public int getScreenWidth() { return this.width; }
    public int getScreenHeight() { return this.height; }
    public net.minecraft.client.gui.Font getScreenFont() { return this.font; }
    public <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T addWidgetToScreen(T widget) {
        return this.addRenderableWidget(widget);
    }
    public void removeWidgetFromScreen(net.minecraft.client.gui.components.events.GuiEventListener widget) {
        this.removeWidget(widget);
    }

    // endregion

    @Override
    protected void init() {
        super.init();
        loadFromCurrentData();

        // 标签页按钮
        String[] tabKeys = {
                "ctnhchangelog.editor.tab.entries",
                "ctnhchangelog.editor.tab.tags",
                "ctnhchangelog.editor.tab.footer"
        };
        for (int i = 0; i < 3; i++) {
            final int tabIndex = i;
            int tx = MARGIN + i * (TAB_WIDTH + TAB_GAP);
            this.addRenderableWidget(
                    Button.builder(Component.translatable(tabKeys[i]), b -> switchTab(tabIndex))
                            .bounds(tx, TAB_BAR_Y, TAB_WIDTH, TAB_HEIGHT).build()
            );
        }

        // 操作按钮（右侧）
        int btnW = 55;
        int actionX = this.width - MARGIN - btnW;
        this.addRenderableWidget(
                Button.builder(Component.translatable("ctnhchangelog.editor.close"),
                        b -> this.onClose())
                        .bounds(actionX, TAB_BAR_Y, btnW, TAB_HEIGHT).build()
        );
        actionX -= btnW + 4;
        this.addRenderableWidget(
                Button.builder(Component.translatable("ctnhchangelog.editor.export"),
                        b -> exportJson())
                        .bounds(actionX, TAB_BAR_Y, btnW, TAB_HEIGHT).build()
        );
        actionX -= btnW + 4;
        this.addRenderableWidget(
                Button.builder(Component.translatable("ctnhchangelog.editor.import"),
                        b -> importJson())
                        .bounds(actionX, TAB_BAR_Y, btnW, TAB_HEIGHT).build()
        );

        // 创建标签页组件
        entriesTab = new EditorEntriesTab(this);
        tagsTab = new EditorTagsTab(this);
        footerTab = new EditorFooterTab(this);

        switchTab(0);
    }

    private void switchTab(int tabIndex) {
        // 移除当前标签页的 widgets
        switch (currentTab) {
            case 0 -> entriesTab.removeWidgets(this);
            case 1 -> tagsTab.removeWidgets(this);
            case 2 -> footerTab.removeWidgets(this);
        }

        currentTab = tabIndex;

        // 添加新标签页的 widgets
        switch (currentTab) {
            case 0 -> entriesTab.addWidgets(this);
            case 1 -> tagsTab.addWidgets(this);
            case 2 -> footerTab.addWidgets(this);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 标签页背景
        int tabAreaRight = MARGIN + 3 * (TAB_WIDTH + TAB_GAP);
        graphics.fill(MARGIN - 2, TAB_BAR_Y - 2, tabAreaRight, TAB_BAR_Y + TAB_HEIGHT + 2, 0xFF232838);

        // 当前标签页高亮
        int highlightX = MARGIN + currentTab * (TAB_WIDTH + TAB_GAP);
        graphics.fill(highlightX, TAB_BAR_Y + TAB_HEIGHT, highlightX + TAB_WIDTH, TAB_BAR_Y + TAB_HEIGHT + 2, 0xFFFFAA00);

        // 渲染当前标签页内容
        switch (currentTab) {
            case 0 -> entriesTab.render(graphics, this.font, mouseX, mouseY, partialTick);
            case 1 -> tagsTab.render(graphics, this.font, mouseX, mouseY, partialTick);
            case 2 -> footerTab.render(graphics, this.font, mouseX, mouseY, partialTick);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // 渲染 Toast
        renderToast(graphics);
    }

    // region 输入事件转发

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 颜色选择器优先处理（通过 mouseClicked 内部检测）

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        return switch (currentTab) {
            case 0 -> entriesTab.mouseClicked(mouseX, mouseY, button);
            case 1 -> tagsTab.mouseClicked(mouseX, mouseY, button);
            case 2 -> footerTab.mouseClicked(mouseX, mouseY, button);
            default -> false;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return switch (currentTab) {
            case 0 -> entriesTab.mouseScrolled(mouseX, mouseY, delta);
            case 1 -> tagsTab.mouseScrolled(mouseX, mouseY, delta);
            default -> super.mouseScrolled(mouseX, mouseY, delta);
        };
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return switch (currentTab) {
            case 0 -> entriesTab.mouseDragged(mouseX, mouseY, button);
            case 1 -> tagsTab.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            default -> super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        };
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        switch (currentTab) {
            case 0 -> entriesTab.mouseReleased(mouseX, mouseY, button);
            case 1 -> tagsTab.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentTab == 0 && entriesTab.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (currentTab == 1 && tagsTab.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (currentTab == 2 && footerTab.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (currentTab == 0 && entriesTab.charTyped(chr, modifiers)) return true;
        if (currentTab == 1 && tagsTab.charTyped(chr, modifiers)) return true;
        if (currentTab == 2 && footerTab.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    // endregion

    // region 数据加载

    private void loadFromCurrentData() {
        entries = new ArrayList<>();
        for (ChangelogEntry entry : ChangelogEntry.getAllEntries()) {
            entries.add(EditableEntry.fromChangelogEntry(entry));
        }

        tagColors.clear();
        tagColors.putAll(ChangelogEntry.getTagColorsMap());

        footerText = ChangelogEntry.getFooterText();
    }

    // endregion

    // region 导入/导出

    private void exportJson() {
        try {
            Path outputDir = Minecraft.getInstance().gameDirectory.toPath().resolve("changelog_opt");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            Path output = outputDir.resolve("changelog.json");
            Files.writeString(output, buildJsonString(), StandardCharsets.UTF_8);
            showToast(Component.translatable("ctnhchangelog.editor.exported").getString()
                    + ": changelog_opt/changelog.json");
        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to export changelog", e);
            showToast("Export failed: " + e.getMessage());
        }
    }

    private void importJson() {
        try {
            Path cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve(".cache");
            Path input = cacheDir.resolve("changelog_cache.json");
            if (Files.exists(input)) {
                String json = Files.readString(input, StandardCharsets.UTF_8);
                parseAndLoadJson(json);
                showToast(Component.translatable("ctnhchangelog.editor.imported").getString());
            } else {
                showToast(Component.translatable("ctnhchangelog.editor.import_not_found").getString());
            }
        } catch (Exception e) {
            CTNHChangelog.LOGGER.error("Failed to import changelog", e);
            showToast(Component.translatable("ctnhchangelog.editor.import_error").getString());
        }
    }

    private void parseAndLoadJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        // 解析 entries
        entries.clear();
        if (root.has("entries")) {
            JsonArray entriesArray = root.getAsJsonArray("entries");
            for (JsonElement el : entriesArray) {
                JsonObject obj = el.getAsJsonObject();
                EditableEntry e = new EditableEntry();
                e.version = obj.has("version") ? obj.get("version").getAsString() : "1.0.0";
                e.date = obj.has("date") ? obj.get("date").getAsString() : "";
                e.title = obj.has("title") ? obj.get("title").getAsString() : "";
                e.color = obj.has("color") ? parseColor(obj.get("color").getAsString()) : 0xFFFFFF00;

                // type
                e.types = new ArrayList<>();
                if (obj.has("type")) {
                    JsonElement typeEl = obj.get("type");
                    if (typeEl.isJsonArray()) {
                        for (JsonElement t : typeEl.getAsJsonArray()) e.types.add(t.getAsString());
                    } else {
                        e.types.add(typeEl.getAsString());
                    }
                } else {
                    e.types.add("patch");
                }

                // tags
                e.tags = new ArrayList<>();
                if (obj.has("tags")) {
                    JsonElement tagsEl = obj.get("tags");
                    if (tagsEl.isJsonArray()) {
                        for (JsonElement t : tagsEl.getAsJsonArray()) e.tags.add(t.getAsString());
                    } else {
                        e.tags.add(tagsEl.getAsString());
                    }
                } else if (obj.has("tag")) {
                    e.tags.add(obj.get("tag").getAsString());
                }

                // changes
                e.changes = new ArrayList<>();
                if (obj.has("changes")) {
                    for (JsonElement c : obj.getAsJsonArray("changes")) e.changes.add(c.getAsString());
                }

                entries.add(e);
            }
        }

        // 解析 tagColors
        tagColors.clear();
        if (root.has("tagColors")) {
            for (Map.Entry<String, JsonElement> tc : root.getAsJsonObject("tagColors").entrySet()) {
                tagColors.put(tc.getKey(), parseColor(tc.getValue().getAsString()));
            }
        }

        // 解析 footer
        footerText = root.has("footer") ? root.get("footer").getAsString() : "";

        // 刷新当前标签页
        switchTab(currentTab);
    }

    private String buildJsonString() {
        JsonObject root = new JsonObject();
        root.addProperty("footer", footerText);

        JsonArray entriesArray = new JsonArray();
        for (EditableEntry e : entries) {
            entriesArray.add(e.toJson());
        }
        root.add("entries", entriesArray);

        JsonObject tagColorsObj = new JsonObject();
        for (Map.Entry<String, Integer> tc : tagColors.entrySet()) {
            tagColorsObj.addProperty(tc.getKey(),
                    String.format("0xFF%06X", tc.getValue() & 0x00FFFFFF));
        }
        root.add("tagColors", tagColorsObj);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(root);
    }

    private static int parseColor(String colorStr) {
        try {
            if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
                String hex = colorStr.substring(2);
                if (hex.length() == 6) return (int) Long.parseLong("FF" + hex, 16);
                if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
            } else if (colorStr.startsWith("#")) {
                String hex = colorStr.substring(1);
                if (hex.length() == 6) return (int) Long.parseLong("FF" + hex, 16);
                if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
            }
        } catch (Exception ignored) {
        }
        return 0xFFFFFF00;
    }

    // endregion

    // region Toast

    public void showToast(String message) {
        this.toastMessage = message;
        this.toastExpiry = System.currentTimeMillis() + 3000;
    }

    private void renderToast(GuiGraphics graphics) {
        if (toastMessage == null || System.currentTimeMillis() > toastExpiry) {
            toastMessage = null;
            return;
        }
        int w = this.font.width(toastMessage) + 24;
        int x = this.width - w - 10;
        int y = this.height - 30;
        graphics.fill(x, y, x + w, y + 20, 0xFF2D3349);
        graphics.fill(x, y, x + 3, y + 20, 0xFFFFAA00);
        graphics.drawString(this.font, toastMessage, x + 8, y + 5, 0xFFFFFF);
    }

    // endregion

    @Override
    public void onClose() {
        // 同步数据并关闭
        switch (currentTab) {
            case 0 -> entriesTab.removeWidgets(this);
            case 1 -> tagsTab.removeWidgets(this);
            case 2 -> footerTab.removeWidgets(this);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        this.init(minecraft, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
