package com.mmyddd.mcmod.changelog.client.editor;

import com.mmyddd.mcmod.changelog.CTNHChangelog;
import com.mmyddd.mcmod.changelog.client.ChangeNode;
import com.mmyddd.mcmod.changelog.client.ChangelogDocument;
import com.mmyddd.mcmod.changelog.client.ChangelogEntry;
import com.mmyddd.mcmod.changelog.client.ChangelogJsonReader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A single-screen command deck for editing the recursive changelog outline. */
public class CommandDeckEditorScreen extends Screen {
    private static final int PANEL = 0xE8171B24;
    private static final int PANEL_ALT = 0xE8212631;
    private static final int BORDER = 0xFF374454;
    private static final int TEXT = 0xFFE9EEF4;
    private static final int MUTED = 0xFF9BA8B7;
    private static final int ACCENT = 0xFFD7F26B;
    private static final int CYAN = 0xFF86D7BC;
    private static final int DANGER = 0xFFFF7A7A;
    private static final int PANEL_TOP = 48;
    private static final int PANEL_HEADER_Y = 52;
    private static final int ENTRY_LIST_TOP = 104;
    private static final int ENTRY_FIELD_TOP = 62;
    private static final int ENTRY_FIELD_GAP = 24;
    private static final int TYPE_CHECKBOX_TOP = ENTRY_FIELD_TOP + 18;
    private static final int TYPE_CHECKBOX_ROW_HEIGHT = 20;
    private static final int TREE_TOOLBAR_Y = 146;
    private static final int TREE_TOP = 174;
    private static final String[] TYPE_OPTIONS = {"major", "minor", "patch", "hotfix", "danger"};
    private static final String[] CTNH_TEMPLATE_HEADINGS = {
            "占位符", "模组改动", "整合包改动", "GTM", "CTNH-Core",
            "CTNH-Mana", "CTNH-Bio", "CTNH-Energy", "CTPP"
    };
    private static final int ENTRY_TAG_ROW_HEIGHT = 20;
    private static final int ENTRY_TAG_HEIGHT = 18;
    private static final int TAG_DROPDOWN_GAP = 4;
    private static final int TAG_ROW_HEIGHT = 24;
    private static final long DOUBLE_CLICK_MS = 300L;
    private static final long DRAG_HOLD_MS = 300L;

    private final Screen parentScreen;
    private final List<EditableEntry> entries = new ArrayList<>();
    private final LinkedHashMap<String, Integer> tagColors = new LinkedHashMap<>();
    private String footerText = "";

    private int selectedEntryIndex = -1;
    private final List<Integer> selectedPath = new ArrayList<>();
    private final Set<String> collapsedPaths = new HashSet<>();

    private EditBox searchBox;
    private EditBox versionBox;
    private EditBox dateBox;
    private EditBox titleBox;
    private EditBox tagsBox;
    private EditBox accentBox;
    private EditBox tagColorsBox;
    private EditBox footerBox;
    private EditBox inlineNodeBox;
    private final List<Checkbox> typeCheckboxes = new ArrayList<>();

    private final List<EditBox> tagNameBoxes = new ArrayList<>();
    private final List<EditBox> tagColorBoxes = new ArrayList<>();
    private final List<Button> tagDeleteButtons = new ArrayList<>();
    private EditBox newTagBox;
    private Button addTagButton;
    private double tagManagementScroll;
    private EditorColorPicker tagColorPicker;
    private int tagColorPickerTargetIndex = -1;
    private int tagColorPickerX;
    private int tagColorPickerY;
    private boolean tagDropdownOpen;
    private int tagDropdownX;
    private int tagDropdownY;

    private final List<Button> entryButtons = new ArrayList<>();
    private final List<Integer> entryButtonEntryIndices = new ArrayList<>();
    private String cachedSearch = "";
    private EditableEntry boundEntry;
    private boolean loadingSelectedFields;
    private EditableChangeNode editingNode;
    private final List<Integer> editingNodePath = new ArrayList<>();
    private boolean dataInitialized;
    private String cleanSnapshot = "";
    private boolean importConfirmationVisible;
    private ChangelogDocument.Document pendingImportDocument;
    private Path pendingImportPath;

    private int left;
    private int leftRight;
    private int centerLeft;
    private int centerRight;
    private int rightLeft;
    private int bottom;
    private int treeTop;
    private int treeBottom;
    private double treeScroll;
    private int treeContentHeight;
    private final List<TreeRow> treeRows = new ArrayList<>();
    private boolean entryDragging;
    private int entryDragSourceIndex = -1;
    private int entryDragTargetRow = -1;
    private int entryDragMouseY;
    private long entryPressTime;
    private boolean tagDragging;
    private int tagDragIndex = -1;
    private int tagDragMouseX;
    private int tagDragMouseY;
    private long tagPressTime;
    private long lastEntryClickTime;
    private int lastEntryClickIndex = -1;
    private long lastTreeClickTime;
    private String lastTreeClickPath = "";
    private int accentTop;
    private int tagColorsLabelY;
    private int tagManagementTop;
    private int tagManagementBottom;
    private int footerTop;
    private int typesBottom = TYPE_CHECKBOX_TOP + TYPE_CHECKBOX_ROW_HEIGHT;
    private int tagsTop = typesBottom + 8;

    private String toastMessage;
    private long toastExpiry;

    public CommandDeckEditorScreen(Screen parent) {
        super(Component.translatable("ctnhchangelog.editor.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        commitInlineNodeEdit();
        if (tagColorPicker != null) {
            closeTagColorPicker();
        }
        tagDropdownOpen = false;
        super.init();
        boolean firstInit = !dataInitialized;
        if (firstInit) {
            loadFromCurrentData();
            dataInitialized = true;
        }

        left = 12;
        int leftWidth = Math.min(236, Math.max(200, this.width / 4));
        int rightWidth = Math.min(252, Math.max(220, this.width / 4));
        leftRight = left + leftWidth;
        rightLeft = this.width - rightWidth - 12;
        centerLeft = leftRight + 8;
        centerRight = rightLeft - 8;
        bottom = this.height - 14;
        treeTop = TREE_TOP;
        treeBottom = bottom - 18;
        updateInspectorLayout();

        this.addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.import"), b -> importJson())
                .bounds(this.width - 194, 10, 58, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.export"), b -> exportJson())
                .bounds(this.width - 132, 10, 58, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width - 70, 10, 58, 20).build());

        searchBox = createBox(left + 8, 78, leftRight - left - 16,
                commandDeckText("search_entries").getString());
        searchBox.setMaxLength(120);
        searchBox.setResponder(value -> {
            if (!value.equals(cachedSearch)) {
                refreshEntryButtons();
            }
        });
        setCommitAction(searchBox, () -> {
        });
        markCommittedValue(searchBox);
        this.addRenderableWidget(searchBox);

        int fieldX = centerLeft + 74;
        int fieldWidth = Math.max(80, centerRight - fieldX - 10);
        versionBox = createBox(fieldX, ENTRY_FIELD_TOP, fieldWidth, "1.2.2");
        dateBox = createBox(fieldX, ENTRY_FIELD_TOP + ENTRY_FIELD_GAP, fieldWidth, "2026-06-09");
        titleBox = createBox(fieldX, ENTRY_FIELD_TOP + ENTRY_FIELD_GAP * 2, fieldWidth,
                commandDeckText("title_placeholder").getString());
        versionBox.setMaxLength(64);
        dateBox.setMaxLength(64);
        titleBox.setMaxLength(180);
        versionBox.setResponder(this::handleEntryFieldChanged);
        dateBox.setResponder(this::handleEntryFieldChanged);
        titleBox.setResponder(this::handleEntryFieldChanged);
        setCommitAction(versionBox, this::commitEntryFields);
        setCommitAction(dateBox, this::commitEntryFields);
        setCommitAction(titleBox, this::commitEntryFields);
        markCommittedValue(versionBox);
        markCommittedValue(dateBox);
        markCommittedValue(titleBox);
        this.addRenderableWidget(versionBox);
        this.addRenderableWidget(dateBox);
        this.addRenderableWidget(titleBox);

        int rightFieldX = rightLeft + 74;
        int rightFieldWidth = Math.max(100, this.width - rightFieldX - 20);
        buildTypeCheckboxes(rightFieldX, TYPE_CHECKBOX_TOP, rightFieldWidth);
        tagsBox = null;
        accentBox = createBox(rightFieldX, accentTop, rightFieldWidth, "#D7F26B");
        tagColorsBox = null;
        footerBox = createBox(rightLeft + 12, footerTop, rightFieldWidth + 62,
                commandDeckText("footer_placeholder").getString());
        accentBox.setMaxLength(16);
        footerBox.setMaxLength(500);
        setCommitAction(accentBox, this::commitInspectorFields);
        setCommitAction(footerBox, this::commitInspectorFields);
        markCommittedValue(accentBox);
        markCommittedValue(footerBox);
        this.addRenderableWidget(accentBox);
        this.addRenderableWidget(footerBox);

        addTreeControls();
        buildTagManagementWidgets();
        boundEntry = null;
        loadSelectedFields();
        refreshEntryButtons();
        if (firstInit) {
            markClean();
        }
    }

    private EditBox createBox(int x, int y, int width, String hint) {
        EditBox box = new CommitOnBlurEditBox(this.font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setBordered(true);
        return box;
    }

    private void setCommitAction(EditBox box, Runnable action) {
        if (box instanceof CommitOnBlurEditBox commitBox) {
            commitBox.setCommitAction(action);
        }
    }

    private void markCommittedValue(EditBox box) {
        if (box instanceof CommitOnBlurEditBox commitBox) {
            commitBox.markCommittedValue();
        }
    }

    private void commitEntryFields() {
        if (!loadingSelectedFields && selectedEntry() != null) {
            syncFields();
        }
    }

    private void commitInspectorFields() {
        if (loadingSelectedFields) {
            return;
        }
        syncFields();
        updateInspectorLayout();
    }

    private Component commandDeckText(String key, Object... arguments) {
        return Component.translatable("ctnhchangelog.editor.commanddeck." + key, arguments);
    }

    private void buildTypeCheckboxes(int x, int y, int width) {
        typeCheckboxes.clear();
        int currentX = x;
        int currentY = y;
        for (String type : TYPE_OPTIONS) {
            int checkboxWidth = this.font.width(type) + 24;
            if (currentX != x && currentX + checkboxWidth > x + width) {
                currentX = x;
                currentY += TYPE_CHECKBOX_ROW_HEIGHT;
            }
            Checkbox checkbox = new Checkbox(
                    currentX,
                    currentY,
                    checkboxWidth,
                    TYPE_CHECKBOX_ROW_HEIGHT,
                    Component.translatable("ctnhchangelog.type." + type),
                    false
            );
            typeCheckboxes.add(checkbox);
            this.addRenderableWidget(checkbox);
            currentX += checkboxWidth + 4;
        }
        typesBottom = currentY + TYPE_CHECKBOX_ROW_HEIGHT;
        tagsTop = typesBottom + 8;
    }

    private void updateInspectorLayout() {
        EditableEntry entry = selectedEntry();
        int tagRows = entryTagRowCount(entry);
        int dropdownHeight = 0;
        if (tagDropdownOpen && entry != null) {
            dropdownHeight = availableEntryTags(entry).size() * ENTRY_TAG_ROW_HEIGHT;
        }

        int tagsBottom = tagsTop + tagRows * ENTRY_TAG_ROW_HEIGHT;
        if (dropdownHeight > 0) {
            tagsBottom += TAG_DROPDOWN_GAP + dropdownHeight;
        }
        accentTop = tagsBottom + 8;
        tagColorsLabelY = accentTop + 34;
        tagManagementTop = tagColorsLabelY + 14;

        footerTop = bottom - 28;
        tagManagementBottom = Math.max(tagManagementTop + TAG_ROW_HEIGHT, footerTop - 14);

        if (accentBox != null) {
            accentBox.setY(accentTop);
        }
        if (footerBox != null) {
            footerBox.setY(footerTop);
        }
        updateTagManagementWidgets();
    }

    private int entryTagRowCount(EditableEntry entry) {
        if (entry == null) {
            return 1;
        }
        int leftEdge = rightLeft + 74;
        int rightEdge = this.width - 20;
        int dropWidth = this.font.width("▼ +") + 12;
        int currentX = leftEdge + dropWidth + 8;
        int rows = 1;
        for (String tag : entry.tags) {
            int pillWidth = this.font.width(tag) + 22;
            if (currentX + pillWidth > rightEdge) {
                currentX = leftEdge;
                rows++;
            }
            currentX += pillWidth + 6;
        }
        return rows;
    }

    private void buildTagManagementWidgets() {
        tagNameBoxes.clear();
        tagColorBoxes.clear();
        tagDeleteButtons.clear();
        newTagBox = null;
        addTagButton = null;

        int index = 0;
        for (Map.Entry<String, Integer> entry : tagColors.entrySet()) {
            int y = tagManagementRowY(index);
            int rowIndex = index;
            EditBox nameBox = createBox(rightLeft + 12, y, 78,
                    commandDeckText("tag_name").getString());
            nameBox.setValue(entry.getKey());
            nameBox.setMaxLength(64);
            setCommitAction(nameBox, this::commitInspectorFields);
            markCommittedValue(nameBox);
            EditBox colorBox = createBox(rightLeft + 118, y, 60, "#888888");
            colorBox.setValue(ChangelogDocument.formatColor(entry.getValue()));
            colorBox.setMaxLength(9);
            setCommitAction(colorBox, this::commitInspectorFields);
            markCommittedValue(colorBox);
            Button deleteButton = Button.builder(Component.literal("X"), button -> removeTagColor(rowIndex))
                    .bounds(rightLeft + 184, y, 26, 20).build();

            this.addRenderableWidget(nameBox);
            this.addRenderableWidget(colorBox);
            this.addRenderableWidget(deleteButton);
            tagNameBoxes.add(nameBox);
            tagColorBoxes.add(colorBox);
            tagDeleteButtons.add(deleteButton);
            index++;
        }

        int newY = tagManagementRowY(index);
        newTagBox = createBox(rightLeft + 12, newY, 132,
                commandDeckText("new_tag").getString());
        newTagBox.setHint(commandDeckText("new_tag_name"));
        newTagBox.setMaxLength(64);
        setCommitAction(newTagBox, this::addTagColor);
        markCommittedValue(newTagBox);
        addTagButton = Button.builder(commandDeckText("add"), button -> addTagColor())
                .bounds(rightLeft + 148, newY, 62, 20).build();
        this.addRenderableWidget(newTagBox);
        this.addRenderableWidget(addTagButton);
        updateTagManagementWidgets();
    }

    private void removeTagManagementWidgets() {
        for (EditBox box : tagNameBoxes) {
            removeWidget(box);
        }
        for (EditBox box : tagColorBoxes) {
            removeWidget(box);
        }
        for (Button button : tagDeleteButtons) {
            removeWidget(button);
        }
        if (newTagBox != null) {
            removeWidget(newTagBox);
        }
        if (addTagButton != null) {
            removeWidget(addTagButton);
        }
        tagNameBoxes.clear();
        tagColorBoxes.clear();
        tagDeleteButtons.clear();
        newTagBox = null;
        addTagButton = null;
    }

    private void rebuildTagManagementWidgets() {
        removeTagManagementWidgets();
        buildTagManagementWidgets();
    }

    private int tagManagementRowY(int index) {
        return tagManagementTop + index * TAG_ROW_HEIGHT - (int) tagManagementScroll;
    }

    private void updateTagManagementWidgets() {
        int viewTop = tagManagementTop;
        int viewBottom = tagManagementBottom;
        int contentHeight = (tagColors.size() + 1) * TAG_ROW_HEIGHT;
        int viewHeight = tagManagementBottom - tagManagementTop;
        tagManagementScroll = Mth.clamp(tagManagementScroll, 0, Math.max(0, contentHeight - viewHeight));
        for (int i = 0; i < tagNameBoxes.size(); i++) {
            int y = tagManagementRowY(i);
            EditBox nameBox = tagNameBoxes.get(i);
            EditBox colorBox = tagColorBoxes.get(i);
            Button deleteButton = tagDeleteButtons.get(i);
            nameBox.setX(rightLeft + 12);
            nameBox.setY(y);
            colorBox.setX(rightLeft + 118);
            colorBox.setY(y);
            deleteButton.setX(rightLeft + 184);
            deleteButton.setY(y);
            boolean visible = y + 20 >= viewTop && y < viewBottom;
            nameBox.visible = visible;
            nameBox.active = visible;
            colorBox.visible = visible;
            colorBox.active = visible;
            deleteButton.visible = visible;
            deleteButton.active = visible;
        }

        if (newTagBox != null && addTagButton != null) {
            int y = tagManagementRowY(tagNameBoxes.size());
            newTagBox.setX(rightLeft + 12);
            newTagBox.setY(y);
            addTagButton.setX(rightLeft + 148);
            addTagButton.setY(y);
            boolean visible = y + 20 >= viewTop && y < viewBottom;
            newTagBox.visible = visible;
            newTagBox.active = visible;
            addTagButton.visible = visible;
            addTagButton.active = visible;
        }
    }

    private void syncTagManagementRows() {
        if (tagNameBoxes.isEmpty() || tagColorBoxes.isEmpty()) {
            return;
        }
        List<String> oldNames = new ArrayList<>(tagColors.keySet());
        List<Integer> oldColors = new ArrayList<>(tagColors.values());
        LinkedHashMap<String, Integer> updated = new LinkedHashMap<>();
        for (int i = 0; i < tagNameBoxes.size(); i++) {
            String oldName = i < oldNames.size() ? oldNames.get(i) : "";
            String newName = tagNameBoxes.get(i).getValue().trim();
            if (newName.isEmpty()) {
                removeTagReferences(oldName);
                continue;
            }
            int fallback = i < oldColors.size() ? oldColors.get(i) : 0xFF888888;
            int color = parseManagedColor(tagColorBoxes.get(i).getValue(), fallback);
            updated.put(newName, color);
            if (!oldName.equals(newName)) {
                replaceTagReferences(oldName, newName);
            }
        }
        tagColors.clear();
        tagColors.putAll(updated);
    }

    private int parseManagedColor(String value, int fallback) {
        try {
            String normalized = value == null ? "" : value.trim();
            if (normalized.startsWith("#")) {
                normalized = normalized.substring(1);
            } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
                normalized = normalized.substring(2);
            }
            if (normalized.length() == 6) {
                return (int) Long.parseLong("FF" + normalized, 16);
            }
            if (normalized.length() == 8) {
                return (int) Long.parseLong(normalized, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    private void replaceTagReferences(String oldName, String newName) {
        if (oldName.isEmpty() || oldName.equals(newName)) {
            return;
        }
        for (EditableEntry entry : entries) {
            for (int i = 0; i < entry.tags.size(); i++) {
                if (oldName.equals(entry.tags.get(i))) {
                    entry.tags.set(i, newName);
                }
            }
        }
    }

    private void removeTagReferences(String tagName) {
        if (tagName.isEmpty()) {
            return;
        }
        for (EditableEntry entry : entries) {
            entry.tags.removeIf(tagName::equals);
        }
    }

    private void addTagColor() {
        if (newTagBox == null) {
            return;
        }
        syncTagManagementRows();
        String tagName = newTagBox.getValue().trim();
        if (tagName.isEmpty()) {
            showToast(commandDeckText("tag_name_required").getString());
            return;
        }
        if (tagColors.containsKey(tagName)) {
            showToast(commandDeckText("tag_exists", tagName).getString());
            return;
        }
        tagColors.put(tagName, 0xFF888888);
        newTagBox.setValue("");
        rebuildTagManagementWidgets();
        showToast(commandDeckText("tag_added").getString());
    }

    private void removeTagColor(int index) {
        syncTagManagementRows();
        List<String> names = new ArrayList<>(tagColors.keySet());
        if (index < 0 || index >= names.size()) {
            return;
        }
        String removed = names.get(index);
        tagColors.remove(removed);
        removeTagReferences(removed);
        rebuildTagManagementWidgets();
        updateInspectorLayout();
        showToast(Component.translatable("ctnhchangelog.editor.tag_removed", removed).getString());
    }

    private void openTagColorPicker(int index) {
        syncTagManagementRows();
        List<String> names = new ArrayList<>(tagColors.keySet());
        if (index < 0 || index >= names.size()) {
            return;
        }
        closeTagColorPicker();
        tagDropdownOpen = false;
        tagColorPickerTargetIndex = index;
        String tagName = names.get(index);
        int initialColor = tagColors.getOrDefault(tagName, 0xFF888888);
        tagColorPicker = new EditorColorPicker(
                initialColor,
                newColor -> {
                    tagColors.put(tagName, newColor);
                    if (index < tagColorBoxes.size()) {
                        tagColorBoxes.get(index).setValue(ChangelogDocument.formatColor(newColor));
                        markCommittedValue(tagColorBoxes.get(index));
                    }
                    closeTagColorPicker();
                },
                () -> closeTagColorPicker(true)
        );
        tagColorPickerX = (this.width - EditorColorPicker.PICKER_W) / 2;
        tagColorPickerY = Math.max(10, (this.height - EditorColorPicker.PICKER_H) / 2);
        tagColorPicker.init(this.font, tagColorPickerX, tagColorPickerY);
        if (tagColorPicker.getHexInput() != null) {
            this.addRenderableWidget(tagColorPicker.getHexInput());
        }
    }

    private void closeTagColorPicker() {
        closeTagColorPicker(false);
    }

    private void closeTagColorPicker(boolean discardHexInput) {
        if (tagColorPicker == null) {
            return;
        }
        EditBox hexInput = tagColorPicker.getHexInput();
        if (hexInput != null) {
            if (discardHexInput) {
                markCommittedValue(hexInput);
            }
            removeWidget(hexInput);
        }
        tagColorPicker.close();
        tagColorPicker = null;
        tagColorPickerTargetIndex = -1;
    }

    private void addTreeControls() {
        int y = TREE_TOOLBAR_Y;
        int x = centerLeft;
        int gap = 4;
        int buttonWidth = Math.max(42, (centerRight - centerLeft - gap * 6) / 7);
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.add_heading"), b -> addSibling(true))
                .bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.add_child"), b -> addChildHeading())
                .bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.add_bullet"), b -> addBullet())
                .bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.move_up"), b -> moveSelected(-1))
                .bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.move_down"), b -> moveSelected(1))
                .bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.indent"), b -> indentSelected())
                .bounds(x, y, buttonWidth, 20).build());
        x += buttonWidth + gap;
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.outdent"), b -> outdentSelected())
                .bounds(x, y, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.expand_all"), b -> collapsedPaths.clear())
                .bounds(centerLeft, treeBottom + 2, 82, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.collapse_all"), b -> collapseAll())
                .bounds(centerLeft + 86, treeBottom + 2, 88, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.command.delete_node"), b -> deleteSelectedNode())
                .bounds(centerRight - 84, treeBottom + 2, 84, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.new_entry"), b -> addEntry())
                .bounds(left + 8, bottom - 16, 68, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.ctnh_template"), b -> addCtnhTemplateEntry())
                .bounds(left + 80, bottom - 16, 76, 16).build());
        addRenderableWidget(Button.builder(Component.literal("↑"), b -> moveEntry(-1))
                .bounds(leftRight - 62, bottom - 36, 24, 16).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), b -> moveEntry(1))
                .bounds(leftRight - 34, bottom - 36, 24, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("ctnhchangelog.editor.delete"), b -> deleteEntry())
                .bounds(leftRight - 62, bottom - 16, 54, 16).build());
    }

    private void loadFromCurrentData() {
        entries.clear();
        for (ChangelogEntry entry : ChangelogEntry.getAllEntries()) {
            entries.add(EditableEntry.fromChangelogEntry(entry));
        }
        tagColors.clear();
        tagColors.putAll(ChangelogEntry.getTagColorsMap());
        footerText = ChangelogEntry.getFooterText();
        if (selectedEntryIndex >= entries.size()) {
            selectedEntryIndex = entries.isEmpty() ? -1 : 0;
        } else if (selectedEntryIndex < 0 && !entries.isEmpty()) {
            selectedEntryIndex = 0;
        }
    }

    private void refreshEntryButtons() {
        for (Button button : entryButtons) {
            removeWidget(button);
        }
        entryButtons.clear();
        entryButtonEntryIndices.clear();
        cachedSearch = searchBox == null ? "" : searchBox.getValue();
        String query = cachedSearch.trim().toLowerCase(Locale.ROOT);
        int row = 0;
        for (int index = 0; index < entries.size(); index++) {
            EditableEntry entry = entries.get(index);
            if (!matchesSearch(entry, query)) {
                continue;
            }
            if (ENTRY_LIST_TOP + row * 25 >= bottom - 42) {
                break;
            }
            final int entryIndex = index;
            String label = entry.version + (entry.title.isEmpty() ? "" : "  " + entry.title);
            if (label.length() > 32) {
                label = label.substring(0, 29) + "...";
            }
            Button button = Button.builder(Component.literal(label), b -> selectEntry(entryIndex))
                    .bounds(left + 8, ENTRY_LIST_TOP + row * 25, leftRight - left - 16, 21).build();
            entryButtons.add(button);
            entryButtonEntryIndices.add(entryIndex);
            addRenderableWidget(button);
            row++;
        }
    }

    private boolean matchesSearch(EditableEntry entry, String query) {
        if (query.isEmpty()) {
            return true;
        }

        String searchable = (entry.version + " " + entry.date + " " + entry.title + " "
                + String.join(" ", entry.types) + " " + String.join(" ", entry.tags))
                .toLowerCase(Locale.ROOT);
        if (searchable.contains(query)) {
            return true;
        }

        return containsSearchText(entry.changeTree, query);
    }

    private boolean containsSearchText(List<EditableChangeNode> nodes, String query) {
        for (EditableChangeNode node : nodes) {
            String nodeText = (node.isHeading() ? node.title : node.text);
            if (nodeText != null && nodeText.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
            if (containsSearchText(node.children, query)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleEntryRailClick(double mouseX, double mouseY, int button) {
        if (button != 0 || mouseX < left + 8 || mouseX > leftRight - 8
                || mouseY < ENTRY_LIST_TOP || mouseY >= bottom - 42) {
            return false;
        }
        int row = (int) ((mouseY - ENTRY_LIST_TOP) / 25);
        if (row < 0 || row >= entryButtonEntryIndices.size()) {
            return false;
        }
        int entryIndex = entryButtonEntryIndices.get(row);
        Button clickedButton = row < entryButtons.size() ? entryButtons.get(row) : null;
        long now = System.currentTimeMillis();
        boolean doubleClick = entryIndex == lastEntryClickIndex
                && now - lastEntryClickTime < DOUBLE_CLICK_MS;
        lastEntryClickIndex = entryIndex;
        lastEntryClickTime = now;
        if (clickedButton != null) {
            setFocused(clickedButton);
        }
        selectEntry(entryIndex);
        if (doubleClick) {
            focusTitleEditor();
            lastEntryClickTime = 0;
        }
        entryPressTime = now;
        entryDragSourceIndex = entryIndex;
        entryDragTargetRow = row;
        entryDragMouseY = (int) mouseY;
        entryDragging = false;
        return true;
    }

    private void focusTitleEditor() {
        if (titleBox == null) {
            return;
        }
        setFocused(titleBox);
        titleBox.setCursorPosition(titleBox.getValue().length());
        titleBox.setHighlightPos(titleBox.getValue().length());
    }

    private void updateEntryDragTarget(double mouseY) {
        if (entryButtonEntryIndices.isEmpty()) {
            return;
        }
        int row = (int) ((mouseY - ENTRY_LIST_TOP + 12) / 25);
        entryDragTargetRow = Mth.clamp(row, 0, entryButtonEntryIndices.size() - 1);
        entryDragMouseY = (int) mouseY;
    }

    private void finishEntryDrag() {
        int sourceIndex = entryDragSourceIndex;
        int targetRow = entryDragTargetRow;
        boolean shouldMove = entryDragging;
        entryDragging = false;
        entryDragSourceIndex = -1;
        entryDragTargetRow = -1;
        entryPressTime = 0;
        if (!shouldMove || sourceIndex < 0 || targetRow < 0
                || targetRow >= entryButtonEntryIndices.size()) {
            return;
        }

        int targetIndex = entryButtonEntryIndices.get(targetRow);
        if (sourceIndex < 0 || sourceIndex >= entries.size()
                || targetIndex < 0 || targetIndex >= entries.size()
                || sourceIndex == targetIndex) {
            return;
        }
        syncFields();
        EditableEntry moved = entries.remove(sourceIndex);
        int insertAt = Mth.clamp(targetIndex, 0, entries.size());
        entries.add(insertAt, moved);
        selectedEntryIndex = insertAt;
        boundEntry = null;
        loadSelectedFields();
        refreshEntryButtons();
    }

    private void selectEntry(int index) {
        syncFields();
        selectedEntryIndex = index >= 0 && index < entries.size() ? index : -1;
        selectedPath.clear();
        collapsedPaths.clear();
        tagDropdownOpen = false;
        treeScroll = 0;
        boundEntry = null;
        loadSelectedFields();
    }

    private EditableEntry selectedEntry() {
        return selectedEntryIndex >= 0 && selectedEntryIndex < entries.size()
                ? entries.get(selectedEntryIndex) : null;
    }

    private void loadSelectedFields() {
        loadingSelectedFields = true;
        EditableEntry entry = selectedEntry();
        updateEntryFieldHints(entry);
        if (entry == null) {
            setBoxValue(versionBox, "");
            setBoxValue(dateBox, "");
            setBoxValue(titleBox, "");
            setTypeCheckboxes(List.of());
            setBoxValue(tagsBox, "");
            setBoxValue(accentBox, "");
            boundEntry = null;
        } else if (entry != boundEntry) {
            setBoxValue(versionBox, entry.version);
            setBoxValue(dateBox, entry.date);
            setBoxValue(titleBox, entry.title);
            setTypeCheckboxes(entry.types);
            setBoxValue(tagsBox, String.join(", ", entry.tags));
            setBoxValue(accentBox, entry.allowEmptyTypes ? "" : ChangelogDocument.formatColor(entry.color));
            boundEntry = entry;
        }
        setBoxValue(footerBox, footerText);
        updateInspectorLayout();
        loadingSelectedFields = false;
    }

    private void updateEntryFieldHints(EditableEntry entry) {
        boolean hasSelection = entry != null;
        setBoxHint(versionBox, hasSelection ? "" : "1.2.2");
        setBoxHint(dateBox, hasSelection ? "" : "2026-06-09");
        setBoxHint(titleBox, hasSelection ? "" : commandDeckText("title_placeholder").getString());
        setBoxHint(accentBox, hasSelection && entry.allowEmptyTypes ? "" : "#D7F26B");
    }

    private void setBoxHint(EditBox box, String hint) {
        if (box != null) {
            box.setHint(Component.literal(hint == null ? "" : hint));
        }
    }

    private void handleEntryFieldChanged(String ignored) {
        if (loadingSelectedFields || selectedEntry() == null) {
            return;
        }
        syncFields();
        refreshEntryButtons();
    }

    private void setBoxValue(EditBox box, String value) {
        if (box != null && !box.getValue().equals(value == null ? "" : value)) {
            box.setValue(value == null ? "" : value);
        }
        markCommittedValue(box);
    }

    private void syncFields() {
        EditableEntry entry = selectedEntry();
        if (entry != null) {
            entry.version = versionBox.getValue();
            entry.date = dateBox.getValue();
            entry.title = titleBox.getValue();
            entry.types = getSelectedTypes();
            if (entry.types.isEmpty() && !entry.allowEmptyTypes) {
                entry.types.add("patch");
            } else if (!entry.types.isEmpty()) {
                entry.allowEmptyTypes = false;
            }
            setTypeCheckboxes(entry.types);
            entry.color = ChangelogDocument.parseColor(accentBox.getValue());
        }
        syncTagManagementRows();
        if (footerBox != null) {
            footerText = footerBox.getValue();
        }
        if (entry != null) {
            entry.syncLegacyChanges();
        }
    }

    private List<String> getSelectedTypes() {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < TYPE_OPTIONS.length; index++) {
            if (index < typeCheckboxes.size() && typeCheckboxes.get(index).selected()) {
                result.add(TYPE_OPTIONS[index]);
            }
        }
        return result;
    }

    private void setTypeCheckboxes(List<String> selectedTypes) {
        Set<String> selected = new HashSet<>(selectedTypes);
        for (int index = 0; index < typeCheckboxes.size(); index++) {
            boolean shouldBeSelected = selected.contains(TYPE_OPTIONS[index]);
            Checkbox checkbox = typeCheckboxes.get(index);
            if (checkbox.selected() != shouldBeSelected) {
                checkbox.onPress();
            }
        }
    }

    private String formatTagColors() {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tagColors.entrySet()) {
            values.add(entry.getKey() + "=" + ChangelogDocument.formatColor(entry.getValue()));
        }
        return String.join(", ", values);
    }

    private void parseTagColors(String value) {
        LinkedHashMap<String, Integer> parsed = new LinkedHashMap<>();
        for (String part : value.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2 || pair[0].trim().isEmpty()) {
                continue;
            }
            parsed.put(pair[0].trim(), ChangelogDocument.parseColor(pair[1].trim()));
        }
        tagColors.clear();
        tagColors.putAll(parsed);
    }

    private EditableChangeNode selectedNode() {
        EditableEntry entry = selectedEntry();
        if (entry == null) {
            return null;
        }
        List<EditableChangeNode> nodes = entry.changeTree;
        EditableChangeNode node = null;
        for (Integer index : selectedPath) {
            if (index == null || index < 0 || index >= nodes.size()) {
                return null;
            }
            node = nodes.get(index);
            nodes = node.children;
        }
        return node;
    }

    private List<EditableChangeNode> selectedSiblings() {
        EditableEntry entry = selectedEntry();
        if (entry == null || selectedPath.isEmpty()) {
            return entry == null ? new ArrayList<>() : entry.changeTree;
        }
        List<EditableChangeNode> nodes = entry.changeTree;
        for (int i = 0; i < selectedPath.size() - 1; i++) {
            int index = selectedPath.get(i);
            if (index < 0 || index >= nodes.size()) {
                return new ArrayList<>();
            }
            nodes = nodes.get(index).children;
        }
        return nodes;
    }

    private String pathKey(List<Integer> path) {
        StringBuilder result = new StringBuilder();
        for (Integer index : path) {
            if (result.length() > 0) {
                result.append('.');
            }
            result.append(index);
        }
        return result.toString();
    }

    private boolean pathEquals(List<Integer> leftPath, List<Integer> rightPath) {
        return leftPath.equals(rightPath);
    }

    private void resetTreeView() {
        collapsedPaths.clear();
        treeScroll = 0;
    }

    private void selectNode(List<Integer> path) {
        syncFields();
        selectedPath.clear();
        selectedPath.addAll(path);
    }

    private void addSibling(boolean heading) {
        syncFields();
        EditableEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        List<EditableChangeNode> siblings = selectedSiblings();
        int insertAt = selectedPath.isEmpty() ? siblings.size() : selectedPath.get(selectedPath.size() - 1) + 1;
        EditableChangeNode node = heading
                ? EditableChangeNode.heading("New section")
                : EditableChangeNode.bullet("New change");
        siblings.add(Math.min(insertAt, siblings.size()), node);
        if (selectedPath.isEmpty()) {
            selectedPath.add(siblings.size() - 1);
        } else {
            selectedPath.set(selectedPath.size() - 1, insertAt);
        }
        resetTreeView();
    }

    private void addChildHeading() {
        syncFields();
        EditableEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        EditableChangeNode node = selectedNode();
        if (node == null) {
            node = EditableChangeNode.heading("New section");
            entry.changeTree.add(node);
            selectedPath.clear();
            selectedPath.add(entry.changeTree.size() - 1);
        } else {
            if (!node.isHeading()) {
                return;
            }
            node.children.add(EditableChangeNode.heading("New subsection"));
            selectedPath.add(node.children.size() - 1);
        }
        resetTreeView();
    }

    private void addBullet() {
        addSibling(false);
    }

    private void deleteSelectedNode() {
        syncFields();
        if (selectedPath.isEmpty()) {
            return;
        }
        List<EditableChangeNode> siblings = selectedSiblings();
        int index = selectedPath.get(selectedPath.size() - 1);
        if (index < 0 || index >= siblings.size()) {
            return;
        }
        siblings.remove(index);
        List<Integer> parentPath = new ArrayList<>(selectedPath.subList(0, selectedPath.size() - 1));
        selectedPath.clear();
        selectedPath.addAll(parentPath);
        if (parentPath.isEmpty() && !siblings.isEmpty()) {
            selectedPath.add(Math.min(index, siblings.size() - 1));
        }
        resetTreeView();
    }

    private void moveSelected(int delta) {
        syncFields();
        if (selectedPath.isEmpty()) {
            return;
        }
        List<EditableChangeNode> siblings = selectedSiblings();
        int index = selectedPath.get(selectedPath.size() - 1);
        int target = index + delta;
        if (index < 0 || index >= siblings.size() || target < 0 || target >= siblings.size()) {
            return;
        }
        EditableChangeNode node = siblings.remove(index);
        siblings.add(target, node);
        selectedPath.set(selectedPath.size() - 1, target);
        resetTreeView();
    }

    private void indentSelected() {
        syncFields();
        if (selectedPath.isEmpty()) {
            return;
        }
        List<EditableChangeNode> siblings = selectedSiblings();
        int index = selectedPath.get(selectedPath.size() - 1);
        if (index <= 0 || index >= siblings.size()) {
            return;
        }
        EditableChangeNode previous = siblings.get(index - 1);
        if (!previous.isHeading()) {
            return;
        }
        EditableChangeNode node = siblings.remove(index);
        previous.children.add(node);
        selectedPath.set(selectedPath.size() - 1, previous.children.size() - 1);
        selectedPath.add(selectedPath.size() - 1, index - 1);
        resetTreeView();
    }

    private void outdentSelected() {
        syncFields();
        if (selectedPath.size() < 2) {
            return;
        }
        int parentIndex = selectedPath.get(selectedPath.size() - 2);
        List<EditableChangeNode> siblings = selectedSiblings();
        int index = selectedPath.get(selectedPath.size() - 1);
        if (index < 0 || index >= siblings.size()) {
            return;
        }
        EditableChangeNode node = siblings.remove(index);
        List<Integer> grandparentPath = new ArrayList<>(selectedPath.subList(0, selectedPath.size() - 2));
        EditableEntry entry = selectedEntry();
        List<EditableChangeNode> grandparentSiblings = nodesAtParent(entry, grandparentPath);
        int insertAt = Math.min(parentIndex + 1, grandparentSiblings.size());
        grandparentSiblings.add(insertAt, node);
        selectedPath.clear();
        selectedPath.addAll(grandparentPath);
        selectedPath.add(insertAt);
        resetTreeView();
    }

    private List<EditableChangeNode> nodesAtParent(EditableEntry entry, List<Integer> parentPath) {
        if (entry == null) {
            return new ArrayList<>();
        }
        List<EditableChangeNode> nodes = entry.changeTree;
        for (Integer index : parentPath) {
            if (index < 0 || index >= nodes.size()) {
                return new ArrayList<>();
            }
            nodes = nodes.get(index).children;
        }
        return nodes;
    }

    private EditableChangeNode getNode(List<Integer> path) {
        EditableEntry entry = selectedEntry();
        if (entry == null || path.isEmpty()) {
            return null;
        }
        List<EditableChangeNode> nodes = entry.changeTree;
        EditableChangeNode node = null;
        for (Integer index : path) {
            if (index < 0 || index >= nodes.size()) {
                return null;
            }
            node = nodes.get(index);
            nodes = node.children;
        }
        return node;
    }

    private void collapseAll() {
        collapsedPaths.clear();
        treeScroll = 0;
        EditableEntry entry = selectedEntry();
        if (entry != null) {
            collectHeadingPaths(entry.changeTree, new ArrayList<>());
        }
    }

    private void collectHeadingPaths(List<EditableChangeNode> nodes, List<Integer> prefix) {
        for (int i = 0; i < nodes.size(); i++) {
            EditableChangeNode node = nodes.get(i);
            List<Integer> path = new ArrayList<>(prefix);
            path.add(i);
            if (node.isHeading()) {
                collapsedPaths.add(pathKey(path));
                collectHeadingPaths(node.children, path);
            }
        }
    }

    private void startInlineNodeEdit(List<Integer> path) {
        commitInlineNodeEdit();
        EditableChangeNode node = getNode(path);
        if (node == null) {
            return;
        }
        editingNode = node;
        editingNodePath.clear();
        editingNodePath.addAll(path);
        TreeRow row = findTreeRow(path);
        int x = row == null ? centerLeft + 12 : row.left + (node.isHeading() ? 14 : 4);
        int y = row == null ? treeTop : row.y;
        int width = Math.max(80, centerRight - x - 12);
        inlineNodeBox = createBox(x, y, width, commandDeckText("edit_node").getString());
        inlineNodeBox.setMaxLength(500);
        inlineNodeBox.setValue(node.isHeading() ? node.title : node.text);
        setCommitAction(inlineNodeBox, this::commitInlineNodeEdit);
        markCommittedValue(inlineNodeBox);
        addRenderableWidget(inlineNodeBox);
        updateInlineNodeEditorPosition();
        setFocused(inlineNodeBox);
        inlineNodeBox.setCursorPosition(inlineNodeBox.getValue().length());
        inlineNodeBox.setHighlightPos(inlineNodeBox.getValue().length());
    }

    private void updateInlineNodeEditorPosition() {
        if (inlineNodeBox == null) {
            return;
        }
        TreeRow row = findTreeRow(editingNodePath);
        if (row == null) {
            inlineNodeBox.visible = false;
            inlineNodeBox.active = false;
            return;
        }
        int x = row.left + (editingNode != null && editingNode.isHeading() ? 14 : 4);
        inlineNodeBox.setX(x);
        inlineNodeBox.setY(row.y);
        inlineNodeBox.visible = row.y + row.height >= treeTop && row.y <= treeBottom;
        inlineNodeBox.active = inlineNodeBox.visible;
    }

    private TreeRow findTreeRow(List<Integer> path) {
        for (TreeRow row : treeRows) {
            if (pathEquals(row.path, path)) {
                return row;
            }
        }
        return null;
    }

    private void commitInlineNodeEdit() {
        if (inlineNodeBox == null) {
            return;
        }
        EditableChangeNode node = editingNode;
        String value = inlineNodeBox.getValue().trim();
        if (node != null && !value.isEmpty()) {
            if (node.isHeading()) {
                node.title = value;
            } else {
                node.text = value;
            }
        }
        closeInlineNodeEditor();
    }

    private void cancelInlineNodeEdit() {
        closeInlineNodeEditor();
    }

    private void closeInlineNodeEditor() {
        if (inlineNodeBox != null) {
            markCommittedValue(inlineNodeBox);
            removeWidget(inlineNodeBox);
            inlineNodeBox = null;
        }
        editingNode = null;
        editingNodePath.clear();
    }

    private boolean isEditingNode(List<Integer> path) {
        return inlineNodeBox != null && pathEquals(editingNodePath, path);
    }

    private void addEntry() {
        syncFields();
        EditableEntry entry = new EditableEntry();
        entry.version = "1.0.0";
        entry.title = "New changelog entry";
        entry.changeTree.add(EditableChangeNode.bullet("Describe a change"));
        insertEntryBeforeSelection(entry, "ctnhchangelog.editor.entry_added");
    }

    private void addCtnhTemplateEntry() {
        syncFields();
        EditableEntry entry = new EditableEntry();
        entry.version = "";
        entry.date = "";
        entry.title = "";
        entry.types.clear();
        entry.tags.clear();
        entry.color = 0xFFFFFFFF;
        entry.allowEmptyTypes = true;
        for (String heading : CTNH_TEMPLATE_HEADINGS) {
            entry.changeTree.add(EditableChangeNode.heading(heading));
        }
        entry.syncLegacyChanges();
        insertEntryBeforeSelection(entry, "ctnhchangelog.editor.ctnh_template_added");
    }

    private void insertEntryBeforeSelection(EditableEntry entry, String toastKey) {
        int insertAt = selectedEntryIndex < 0 ? 0 : selectedEntryIndex;
        entries.add(Math.min(insertAt, entries.size()), entry);
        selectedEntryIndex = Math.min(insertAt, entries.size() - 1);
        selectedPath.clear();
        selectedPath.add(0);
        resetTreeView();
        boundEntry = null;
        loadSelectedFields();
        refreshEntryButtons();
        showToast(Component.translatable(toastKey).getString());
    }

    private void deleteEntry() {
        syncFields();
        if (selectedEntryIndex < 0 || selectedEntryIndex >= entries.size()) {
            return;
        }
        entries.remove(selectedEntryIndex);
        if (entries.isEmpty()) {
            selectedEntryIndex = -1;
        } else {
            selectedEntryIndex = Math.min(selectedEntryIndex, entries.size() - 1);
        }
        selectedPath.clear();
        resetTreeView();
        boundEntry = null;
        loadSelectedFields();
        refreshEntryButtons();
        showToast(Component.translatable("ctnhchangelog.editor.entry_deleted").getString());
    }

    private void moveEntry(int delta) {
        syncFields();
        int target = selectedEntryIndex + delta;
        if (selectedEntryIndex < 0 || selectedEntryIndex >= entries.size()
                || target < 0 || target >= entries.size()) {
            return;
        }
        EditableEntry entry = entries.remove(selectedEntryIndex);
        entries.add(target, entry);
        selectedEntryIndex = target;
        resetTreeView();
        refreshEntryButtons();
    }

    private void renderPanel(GuiGraphics graphics, int x, int y, int right, int bottom, int color) {
        graphics.fill(x, y, right, bottom, color);
        graphics.fill(x, y, right, y + 1, BORDER);
        graphics.fill(x, bottom - 1, right, bottom, BORDER);
        graphics.fill(x, y, x + 1, bottom, BORDER);
        graphics.fill(right - 1, y, right, bottom, BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(0, 0, this.width, this.height, 0xFF10141B);
        graphics.drawString(this.font, this.title, 14, 15, TEXT);
        graphics.drawString(this.font, commandDeckText("command_deck"), 14, 26, MUTED);

        renderPanel(graphics, left, PANEL_TOP, leftRight, bottom, PANEL);
        renderPanel(graphics, centerLeft, PANEL_TOP, centerRight, bottom, PANEL_ALT);
        renderPanel(graphics, rightLeft, PANEL_TOP, this.width - 12, bottom, PANEL);

        graphics.drawString(this.font, commandDeckText("entries"), left + 8, PANEL_HEADER_Y, ACCENT);
        graphics.drawString(this.font, commandDeckText("editing_surface"),
                centerLeft + 8, PANEL_HEADER_Y, CYAN);
        graphics.drawString(this.font, commandDeckText("inspector"),
                rightLeft + 12, PANEL_HEADER_Y, ACCENT);
        graphics.drawString(this.font, commandDeckText("entries_count", entries.size()),
                leftRight - 70, PANEL_HEADER_Y, MUTED);

        renderEntryListDecoration(graphics);
        renderEntryFieldsDecoration(graphics);
        renderInspectorDecoration(graphics, mouseX, mouseY);
        renderTree(graphics, mouseX, mouseY);
        updateInlineNodeEditorPosition();

        super.render(graphics, mouseX, mouseY, partialTick);
        renderSelectedEntryHighlight(graphics);
        renderEntryDragOverlay(graphics);
        renderTagManagementDecoration(graphics, mouseX, mouseY);
        if (tagDropdownOpen) {
            renderTagDropdown(graphics, mouseX, mouseY);
        }
        if (tagColorPicker != null) {
            tagColorPicker.render(graphics, this.font, tagColorPickerX, tagColorPickerY, mouseX, mouseY);
        }
        renderToast(graphics);
        if (importConfirmationVisible) {
            renderImportConfirmation(graphics);
        }
    }

    private void renderEntryListDecoration(GuiGraphics graphics) {
        graphics.drawString(this.font, commandDeckText("filter"), left + 8, 68, MUTED);
        graphics.drawString(this.font, commandDeckText("order_preserved"), left + 8, bottom - 27, MUTED);
        if (entries.isEmpty()) {
            graphics.drawString(this.font, commandDeckText("no_entries"),
                    left + 16, ENTRY_LIST_TOP + 4, MUTED);
        }
    }

    private void renderSelectedEntryHighlight(GuiGraphics graphics) {
        int row = entryButtonEntryIndices.indexOf(selectedEntryIndex);
        if (row < 0 || row >= entryButtons.size()) {
            return;
        }
        Button button = entryButtons.get(row);
        int x = button.getX();
        int y = button.getY();
        int right = x + button.getWidth();
        int bottom = y + button.getHeight();
        graphics.fill(x, y, right, y + 2, ACCENT);
        graphics.fill(x, bottom - 2, right, bottom, ACCENT);
        graphics.fill(x, y, x + 2, bottom, ACCENT);
        graphics.fill(right - 2, y, right, bottom, ACCENT);
    }

    private void renderEntryFieldsDecoration(GuiGraphics graphics) {
        graphics.drawString(this.font, Component.translatable("ctnhchangelog.editor.version"),
                centerLeft + 8, ENTRY_FIELD_TOP + 6, MUTED);
        graphics.drawString(this.font, Component.translatable("ctnhchangelog.editor.date"),
                centerLeft + 8, ENTRY_FIELD_TOP + ENTRY_FIELD_GAP + 6, MUTED);
        graphics.drawString(this.font, Component.translatable("ctnhchangelog.editor.title_field"),
                centerLeft + 8, ENTRY_FIELD_TOP + ENTRY_FIELD_GAP * 2 + 6, MUTED);
        graphics.drawString(this.font, Component.translatable("ctnhchangelog.changes"),
                centerLeft + 8, TREE_TOOLBAR_Y - 14, TEXT);
        if (selectedEntry() == null) {
            graphics.drawString(this.font, Component.translatable("ctnhchangelog.editor.select_entry"),
                    centerLeft + 12, TREE_TOP + 8, MUTED);
        }
    }

    private void renderInspectorDecoration(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.translatable("ctnhchangelog.editor.types"),
                rightLeft + 12, ENTRY_FIELD_TOP + 6, MUTED);
        graphics.drawString(this.font, Component.translatable("ctnhchangelog.editor.tags"),
                rightLeft + 12, tagsTop + 6, MUTED);
        graphics.drawString(this.font, Component.translatable("ctnhchangelog.editor.color"),
                rightLeft + 12, accentTop + 6, MUTED);
        renderEntryTags(graphics, mouseX, mouseY);
        graphics.drawString(this.font, commandDeckText("tag_colors"),
                rightLeft + 12, tagColorsLabelY, MUTED);
        graphics.drawString(this.font, Component.translatable("ctnhchangelog.editor.tab.footer"),
                rightLeft + 12, footerTop - 14, MUTED);
    }

    private List<TagPillLayout> entryTagPillLayouts(EditableEntry entry) {
        List<TagPillLayout> layouts = new ArrayList<>();
        if (entry == null) {
            return layouts;
        }
        int leftEdge = rightLeft + 74;
        int panelRight = this.width - 20;
        int dropWidth = this.font.width("▼ +") + 12;
        int currentX = leftEdge + dropWidth + 8;
        int currentY = tagsTop;
        for (int index = 0; index < entry.tags.size(); index++) {
            String tag = entry.tags.get(index);
            int pillWidth = this.font.width(tag) + 22;
            if (currentX + pillWidth > panelRight) {
                currentX = leftEdge;
                currentY += ENTRY_TAG_ROW_HEIGHT;
            }
            layouts.add(new TagPillLayout(index, currentX, currentY, pillWidth));
            currentX += pillWidth + 6;
        }
        return layouts;
    }

    private void renderEntryTags(GuiGraphics graphics, int mouseX, int mouseY) {
        EditableEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        int x = rightLeft + 74;
        int y = tagsTop;
        int currentX = x;
        int currentY = y;
        String dropLabel = "▼ +";
        int dropWidth = this.font.width(dropLabel) + 12;
        boolean dropHover = mouseX >= currentX && mouseX < currentX + dropWidth
                && mouseY >= currentY && mouseY < currentY + ENTRY_TAG_HEIGHT;
        graphics.fill(currentX, currentY, currentX + dropWidth, currentY + ENTRY_TAG_HEIGHT,
                dropHover ? 0xFF404760 : 0xFF2D3349);
        graphics.fill(currentX, currentY, currentX + dropWidth, currentY + 1, CYAN);
        graphics.fill(currentX, currentY + ENTRY_TAG_HEIGHT - 1,
                currentX + dropWidth, currentY + ENTRY_TAG_HEIGHT, CYAN);
        graphics.fill(currentX, currentY, currentX + 1, currentY + ENTRY_TAG_HEIGHT, CYAN);
        graphics.fill(currentX + dropWidth - 1, currentY,
                currentX + dropWidth, currentY + ENTRY_TAG_HEIGHT, CYAN);
        graphics.drawString(this.font, dropLabel, currentX + 6, currentY + 3, CYAN);
        for (TagPillLayout pill : entryTagPillLayouts(entry)) {
            String tag = entry.tags.get(pill.index);
            boolean dragged = tagDragging && tagDragIndex == pill.index;
            int drawX = dragged ? tagDragMouseX - pill.width / 2 : pill.x;
            int drawY = dragged ? tagDragMouseY - 9 : pill.y;
            int color = tagColors.getOrDefault(tag, 0xFF888888);
            int fillColor = dragged ? (color & 0x00FFFFFF) | 0x66000000 : color;
            graphics.fill(drawX, drawY, drawX + pill.width, drawY + ENTRY_TAG_HEIGHT, fillColor);
            graphics.drawString(this.font, tag, drawX + 4, drawY + 3, 0xFFFFFFFF);
            if (!dragged) {
                int closeX = drawX + this.font.width(tag) + 6;
                boolean closeHover = mouseX >= closeX && mouseX < closeX + 10
                        && mouseY >= drawY && mouseY < drawY + ENTRY_TAG_HEIGHT;
                graphics.drawString(this.font, "✕", closeX, drawY + 3,
                        closeHover ? DANGER : 0xFFEEEEEE);
            }
        }

        tagDropdownX = x;
        tagDropdownY = tagsTop + entryTagRowCount(entry) * ENTRY_TAG_ROW_HEIGHT + TAG_DROPDOWN_GAP;
    }

    private void renderTagDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        EditableEntry entry = selectedEntry();
        if (entry == null) {
            tagDropdownOpen = false;
            return;
        }
        List<String> available = new ArrayList<>();
        for (String tag : tagColors.keySet()) {
            if (!entry.tags.contains(tag)) {
                available.add(tag);
            }
        }
        if (available.isEmpty()) {
            tagDropdownOpen = false;
            return;
        }
        int itemHeight = ENTRY_TAG_ROW_HEIGHT;
        int menuWidth = 132;
        int menuHeight = available.size() * itemHeight;
        int menuX = Math.min(tagDropdownX, this.width - menuWidth - 12);
        int menuY = Math.min(tagDropdownY, this.height - menuHeight - 12);
        tagDropdownX = Math.max(12, menuX);
        tagDropdownY = Math.max(PANEL_TOP, menuY);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        graphics.fill(tagDropdownX, tagDropdownY, tagDropdownX + menuWidth,
                tagDropdownY + menuHeight, 0xFF1A1E2B);
        graphics.fill(tagDropdownX, tagDropdownY, tagDropdownX + menuWidth, tagDropdownY + 1, BORDER);
        graphics.fill(tagDropdownX, tagDropdownY + menuHeight - 1,
                tagDropdownX + menuWidth, tagDropdownY + menuHeight, BORDER);
        for (int i = 0; i < available.size(); i++) {
            String tag = available.get(i);
            int itemY = tagDropdownY + i * itemHeight;
            boolean hover = mouseX >= tagDropdownX && mouseX < tagDropdownX + menuWidth
                    && mouseY >= itemY && mouseY < itemY + itemHeight;
            if (hover) {
                graphics.fill(tagDropdownX + 1, itemY, tagDropdownX + menuWidth - 1,
                        itemY + itemHeight, 0xFF404760);
            }
            graphics.fill(tagDropdownX + 4, itemY + 4, tagDropdownX + 14, itemY + 16,
                    tagColors.getOrDefault(tag, 0xFF888888));
            graphics.drawString(this.font, tag, tagDropdownX + 18, itemY + 4, TEXT);
        }
        graphics.pose().popPose();
    }

    private void renderTagManagementDecoration(GuiGraphics graphics, int mouseX, int mouseY) {
        int viewLeft = rightLeft + 8;
        int viewRight = this.width - 20;
        graphics.enableScissor(viewLeft, tagManagementTop, viewRight, tagManagementBottom);
        int index = 0;
        for (Map.Entry<String, Integer> entry : tagColors.entrySet()) {
            int y = tagManagementRowY(index);
            if (y + 20 >= tagManagementTop && y < tagManagementBottom) {
                int swatchX = rightLeft + 94;
                boolean hover = mouseX >= swatchX && mouseX < swatchX + 20
                        && mouseY >= y && mouseY < y + 20;
                int color = entry.getValue();
                graphics.fill(swatchX, y, swatchX + 20, y + 20, color);
                int borderColor = hover ? ACCENT : 0xFFFFFFFF;
                graphics.fill(swatchX, y, swatchX + 20, y + 1, borderColor);
                graphics.fill(swatchX, y + 19, swatchX + 20, y + 20, borderColor);
                graphics.fill(swatchX, y, swatchX + 1, y + 20, borderColor);
                graphics.fill(swatchX + 19, y, swatchX + 20, y + 20, borderColor);
            }
            index++;
        }
        graphics.disableScissor();

        int contentHeight = (tagColors.size() + 1) * TAG_ROW_HEIGHT;
        int viewHeight = tagManagementBottom - tagManagementTop;
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        if (contentHeight > viewHeight) {
            int barHeight = Math.max(14, viewHeight * viewHeight / contentHeight);
            int barRange = Math.max(1, viewHeight - barHeight);
            int barY = tagManagementTop + (int) (tagManagementScroll * barRange / maxScroll);
            graphics.fill(viewRight - 4, tagManagementTop, viewRight - 1, tagManagementBottom,
                    0x55374454);
            graphics.fill(viewRight - 4, barY, viewRight - 1, barY + barHeight, CYAN);
        }
    }

    private void renderTree(GuiGraphics graphics, int mouseX, int mouseY) {
        treeRows.clear();
        EditableEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }

        int availableWidth = Math.max(80, centerRight - centerLeft - 24);
        int y = treeTop - (int) treeScroll;
        graphics.enableScissor(centerLeft + 2, treeTop, centerRight - 7, treeBottom);
        y = renderTreeNodes(graphics, entry.changeTree, new ArrayList<>(), 0, y, availableWidth);
        graphics.disableScissor();
        treeContentHeight = Math.max(1, y - treeTop + (int) treeScroll + 4);
        int viewHeight = treeBottom - treeTop;
        int maxScroll = Math.max(0, treeContentHeight - viewHeight);
        treeScroll = Mth.clamp(treeScroll, 0, maxScroll);

        if (treeContentHeight > viewHeight) {
            int barHeight = Math.max(14, viewHeight * viewHeight / treeContentHeight);
            int barRange = Math.max(1, viewHeight - barHeight);
            int barY = treeTop + (int) (treeScroll * barRange / maxScroll);
            graphics.fill(centerRight - 5, treeTop, centerRight - 2, treeBottom, 0x55374454);
            graphics.fill(centerRight - 5, barY, centerRight - 2, barY + barHeight, CYAN);
        }
    }

    private int renderTreeNodes(GuiGraphics graphics, List<EditableChangeNode> nodes, List<Integer> prefix,
                                int depth, int y, int availableWidth) {
        for (int i = 0; i < nodes.size(); i++) {
            EditableChangeNode node = nodes.get(i);
            List<Integer> path = new ArrayList<>(prefix);
            path.add(i);
            String key = pathKey(path);
            int leftX = centerLeft + 8 + Math.min(depth, 8) * 14;
            int width = Math.max(70, availableWidth - Math.min(depth, 8) * 14);
            List<net.minecraft.util.FormattedCharSequence> lines;
            int rowHeight;
            if (node.isHeading()) {
                lines = List.of();
                rowHeight = 22;
            } else {
                lines = this.font.split(Component.literal("• " + node.text), width - 8);
                rowHeight = Math.max(20, lines.size() * 12 + 4);
            }

            TreeRow row = new TreeRow(path, node, y, rowHeight, leftX);
            treeRows.add(row);
            boolean visible = y + rowHeight >= treeTop && y <= treeBottom;
            boolean selected = pathEquals(path, selectedPath);
            boolean inlineEditing = isEditingNode(path);
            if (visible) {
                if (selected) {
                    graphics.fill(centerLeft + 5, y, centerRight - 8, y + rowHeight - 1, 0xFF2D3947);
                    graphics.fill(centerLeft + 5, y, centerLeft + 8, y + rowHeight - 1, ACCENT);
                }
                if (node.isHeading()) {
                    boolean collapsed = collapsedPaths.contains(key);
                    graphics.drawString(this.font, collapsed ? "▶" : "▼", leftX, y + 5, ACCENT);
                    if (!inlineEditing) {
                        String label = "H" + (depth + 1) + "  " + node.title;
                        graphics.drawString(this.font, label, leftX + 14, y + 5, selected ? TEXT : ACCENT);
                    }
                    graphics.drawString(this.font, commandDeckText("child_count", node.children.size()),
                            centerRight - 92, y + 5, MUTED);
                } else if (!inlineEditing) {
                    for (int line = 0; line < lines.size(); line++) {
                        graphics.drawString(this.font, lines.get(line), leftX + 4, y + 3 + line * 12,
                                selected ? TEXT : 0xFFD2D9E2);
                    }
                }
            }
            y += rowHeight;

            if (node.isHeading() && !collapsedPaths.contains(key)) {
                y = renderTreeNodes(graphics, node.children, path, depth + 1, y, availableWidth);
            }
        }
        return y;
    }

    private void renderToast(GuiGraphics graphics) {
        if (toastMessage == null || System.currentTimeMillis() > toastExpiry) {
            return;
        }
        int width = this.font.width(toastMessage) + 18;
        int x = this.width / 2 - width / 2;
        int y = this.height - 42;
        graphics.fill(x, y, x + width, y + 22, 0xEE26313D);
        graphics.fill(x, y, x + 3, y + 22, ACCENT);
        graphics.drawString(this.font, toastMessage, x + 9, y + 7, TEXT);
    }

    private void renderEntryDragOverlay(GuiGraphics graphics) {
        if (!entryDragging || entryDragSourceIndex < 0 || entryDragSourceIndex >= entries.size()) {
            return;
        }
        if (entryDragTargetRow >= 0) {
            int indicatorY = ENTRY_LIST_TOP + entryDragTargetRow * 25 - 2;
            graphics.fill(left + 8, indicatorY, leftRight - 8, indicatorY + 2, ACCENT);
        }
        EditableEntry entry = entries.get(entryDragSourceIndex);
        String label = entry.version + (entry.title.isEmpty() ? "" : "  " + entry.title);
        int width = Math.min(leftRight - left - 24, this.font.width(label) + 18);
        int x = Mth.clamp(left + 16, left + 8, leftRight - 8 - width);
        int y = Mth.clamp(entryDragMouseY - 10, ENTRY_LIST_TOP, bottom - 42 - 20);
        graphics.fill(x, y, x + width, y + 20, 0xEE2D3947);
        graphics.fill(x, y, x + 3, y + 20, ACCENT);
        graphics.drawString(this.font, label, x + 8, y + 6, TEXT);
    }

    private void showToast(String message) {
        toastMessage = message;
        toastExpiry = System.currentTimeMillis() + 2600L;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (importConfirmationVisible) {
            return handleImportConfirmationClick(mouseX, mouseY, button);
        }
        if (tagColorPicker != null) {
            boolean insidePicker = mouseX >= tagColorPickerX - 4
                    && mouseX <= tagColorPickerX + EditorColorPicker.PICKER_W + 4
                    && mouseY >= tagColorPickerY - 4
                    && mouseY <= tagColorPickerY + EditorColorPicker.PICKER_H + 4;
            if (tagColorPicker.mouseClicked(mouseX, mouseY, button, tagColorPickerX, tagColorPickerY)) {
                return true;
            }
            if (insidePicker && super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            closeTagColorPicker();
            return true;
        }
        commitFocusedTextInputOutside(mouseX, mouseY);
        if (tagDropdownOpen && handleTagDropdownClick(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && handleEntryRailClick(mouseX, mouseY, button)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handleEntryTagClick(mouseX, mouseY, button)) {
            return true;
        }
        if (handleTagColorSwatchClick(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            for (TreeRow row : treeRows) {
                if (row.contains(mouseX, mouseY)) {
                    long now = System.currentTimeMillis();
                    String rowKey = pathKey(row.path);
                    boolean doubleClick = rowKey.equals(lastTreeClickPath)
                            && now - lastTreeClickTime < DOUBLE_CLICK_MS;
                    lastTreeClickPath = rowKey;
                    lastTreeClickTime = now;
                    if (row.node.isHeading() && mouseX < row.left + 16) {
                        String key = pathKey(row.path);
                        if (!collapsedPaths.remove(key)) {
                            collapsedPaths.add(key);
                        }
                    } else if (doubleClick) {
                        selectNode(row.path);
                        startInlineNodeEdit(row.path);
                        lastTreeClickTime = 0;
                    } else {
                        selectNode(row.path);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (importConfirmationVisible) {
            return true;
        }
        if (tagColorPicker != null) {
            return true;
        }
        if (mouseX >= rightLeft && mouseX <= this.width - 12
                && mouseY >= tagManagementTop && mouseY <= tagManagementBottom) {
            int contentHeight = (tagColors.size() + 1) * TAG_ROW_HEIGHT;
            int viewHeight = tagManagementBottom - tagManagementTop;
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            if (maxScroll > 0) {
                tagManagementScroll = Mth.clamp(tagManagementScroll - delta * 12, 0, maxScroll);
                updateTagManagementWidgets();
                return true;
            }
        }
        if (mouseX >= centerLeft && mouseX <= centerRight && mouseY >= treeTop && mouseY <= treeBottom) {
            treeScroll -= delta * 18.0D;
            treeScroll = Mth.clamp(treeScroll, 0, Math.max(0, treeContentHeight - (treeBottom - treeTop)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tagColorPicker != null) {
            tagColorPicker.mouseDragged(mouseX, mouseY, button, tagColorPickerX, tagColorPickerY);
            return true;
        }
        if (tagDragIndex >= 0) {
            updateTagDrag(mouseX, mouseY);
            return true;
        }
        if (entryDragSourceIndex >= 0) {
            long elapsed = System.currentTimeMillis() - entryPressTime;
            if (!entryDragging && elapsed >= DRAG_HOLD_MS) {
                entryDragging = true;
            }
            if (entryDragging) {
                updateEntryDragTarget(mouseY);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tagColorPicker != null) {
            tagColorPicker.mouseReleased();
            return true;
        }
        if (tagDragIndex >= 0) {
            tagDragIndex = -1;
            tagDragging = false;
            tagPressTime = 0;
            updateInspectorLayout();
        }
        if (entryDragSourceIndex >= 0) {
            finishEntryDrag();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleEntryTagClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        EditableEntry entry = selectedEntry();
        if (entry == null) {
            return false;
        }
        int x = rightLeft + 74;
        int y = tagsTop;
        String dropLabel = "▼ +";
        int dropWidth = this.font.width(dropLabel) + 12;
        if (mouseX >= x && mouseX < x + dropWidth
                && mouseY >= y && mouseY < y + ENTRY_TAG_HEIGHT) {
            if (availableEntryTags(entry).isEmpty()) {
                showToast(commandDeckText("no_unused_tags").getString());
            } else {
                tagDropdownOpen = !tagDropdownOpen;
                updateInspectorLayout();
            }
            return true;
        }

        for (TagPillLayout pill : entryTagPillLayouts(entry)) {
            String tag = entry.tags.get(pill.index);
            if (mouseX >= pill.x && mouseX < pill.x + pill.width
                    && mouseY >= pill.y && mouseY < pill.y + ENTRY_TAG_HEIGHT) {
                int closeX = pill.x + this.font.width(tag) + 6;
                if (mouseX >= closeX && mouseX < closeX + 10) {
                    entry.tags.remove(pill.index);
                    tagDragIndex = -1;
                    tagDragging = false;
                    updateInspectorLayout();
                    return true;
                }
                tagPressTime = System.currentTimeMillis();
                tagDragIndex = pill.index;
                tagDragMouseX = (int) mouseX;
                tagDragMouseY = (int) mouseY;
                tagDragging = false;
                return true;
            }
        }
        return false;
    }

    private List<String> availableEntryTags(EditableEntry entry) {
        List<String> available = new ArrayList<>();
        for (String tag : tagColors.keySet()) {
            if (!entry.tags.contains(tag)) {
                available.add(tag);
            }
        }
        return available;
    }

    private void updateTagDrag(double mouseX, double mouseY) {
        if (tagDragIndex < 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - tagPressTime;
        if (!tagDragging && elapsed >= DRAG_HOLD_MS) {
            tagDragging = true;
        }
        if (!tagDragging) {
            return;
        }
        tagDragMouseX = (int) mouseX;
        tagDragMouseY = (int) mouseY;
        EditableEntry entry = selectedEntry();
        if (entry == null || tagDragIndex >= entry.tags.size()) {
            return;
        }
        for (TagPillLayout pill : entryTagPillLayouts(entry)) {
            if (pill.index == tagDragIndex) {
                continue;
            }
            if (mouseX >= pill.x && mouseX < pill.x + pill.width
                    && mouseY >= pill.y && mouseY < pill.y + ENTRY_TAG_HEIGHT) {
                String moved = entry.tags.get(tagDragIndex);
                entry.tags.set(tagDragIndex, entry.tags.get(pill.index));
                entry.tags.set(pill.index, moved);
                tagDragIndex = pill.index;
                updateInspectorLayout();
                return;
            }
        }
    }

    private boolean handleTagDropdownClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        EditableEntry entry = selectedEntry();
        if (entry == null) {
            tagDropdownOpen = false;
            return true;
        }
        List<String> available = availableEntryTags(entry);
        int menuWidth = 132;
        int menuHeight = available.size() * ENTRY_TAG_ROW_HEIGHT;
        if (available.isEmpty()) {
            tagDropdownOpen = false;
            return true;
        }
        if (mouseX >= tagDropdownX && mouseX < tagDropdownX + menuWidth
                && mouseY >= tagDropdownY && mouseY < tagDropdownY + menuHeight) {
            int index = (int) ((mouseY - tagDropdownY) / ENTRY_TAG_ROW_HEIGHT);
            if (index >= 0 && index < available.size()) {
                entry.tags.add(available.get(index));
            }
        }
        tagDropdownOpen = false;
        updateInspectorLayout();
        return true;
    }

    private boolean handleTagColorSwatchClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int swatchX = rightLeft + 94;
        if (mouseX < swatchX || mouseX >= swatchX + 20) {
            return false;
        }
        int index = (int) ((mouseY - tagManagementTop + tagManagementScroll) / TAG_ROW_HEIGHT);
        if (index < 0 || index >= tagColors.size()) {
            return false;
        }
        int rowY = tagManagementRowY(index);
        if (mouseY < rowY || mouseY >= rowY + 20
                || rowY < tagManagementTop || rowY >= tagManagementBottom) {
            return false;
        }
        openTagColorPicker(index);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (importConfirmationVisible) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelImport();
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmImport();
            }
            return true;
        }
        if (tagColorPicker != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeTagColorPicker();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                tagColorPicker.applyHexInput();
                return true;
            }
            super.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (inlineNodeBox != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitInlineNodeEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelInlineNodeEdit();
                return true;
            }
            if (inlineNodeBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (tagDropdownOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                tagDropdownOpen = false;
                updateInspectorLayout();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            exportJson();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (searchBox != null) {
                searchBox.setFocused(true);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (selectedPath.isEmpty()) {
                return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                outdentSelected();
            } else {
                indentSelected();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && selectedPath.size() > 0 && !isTextInputFocused()) {
            deleteSelectedNode();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    private boolean isTextInputFocused() {
        return getFocused() instanceof EditBox;
    }

    private void commitFocusedTextInputOutside(double mouseX, double mouseY) {
        if (isButtonAt(mouseX, mouseY)) {
            return;
        }
        if (getFocused() instanceof CommitOnBlurEditBox box && !box.isMouseOver(mouseX, mouseY)) {
            box.commit();
            if (getFocused() == box) {
                setFocused(null);
            }
        }
    }

    private boolean isButtonAt(double mouseX, double mouseY) {
        for (var child : children()) {
            if (child instanceof Button button && button.visible && button.active
                    && button.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xFF10141B);
    }

    private void exportJson() {
        try {
            ChangelogDocument.Document document = buildDocument();
            persistDocument(document);
            markClean();
            showToast(commandDeckText("exported_v2").getString());
        } catch (Exception exception) {
            CTNHChangelog.LOGGER.error("Failed to export changelog", exception);
            showToast(commandDeckText("export_failed").getString());
        }
    }

    private void importJson() {
        try {
            Path input = resolveImportPath();
            if (input == null) {
                showToast(commandDeckText("no_export_or_cache").getString());
                return;
            }
            ChangelogDocument.Document document = ChangelogJsonReader.read(
                    Files.readString(input, StandardCharsets.UTF_8));
            if (isDirty()) {
                pendingImportDocument = document;
                pendingImportPath = input;
                importConfirmationVisible = true;
                return;
            }
            loadDocument(document);
            markClean();
            showToast(commandDeckText("imported_v2_legacy").getString());
        } catch (Exception exception) {
            CTNHChangelog.LOGGER.error("Failed to import changelog", exception);
            showToast(commandDeckText("import_failed").getString());
        }
    }

    private ChangelogDocument.Document buildDocument() {
        syncFields();
        List<ChangelogDocument.EntryData> data = new ArrayList<>();
        for (EditableEntry entry : entries) {
            List<ChangeNode> changes = new ArrayList<>();
            for (EditableChangeNode node : entry.changeTree) {
                changes.add(node.toChangeNode());
            }
            data.add(new ChangelogDocument.EntryData(
                    entry.version,
                    entry.date,
                    entry.title,
                    entry.types,
                    entry.tags,
                    entry.color,
                    changes
            ));
        }
        return new ChangelogDocument.Document(footerText, tagColors, data);
    }

    private String snapshotCurrentDocument() {
        return ChangelogDocument.toPrettyJson(buildDocument());
    }

    private boolean isDirty() {
        return cleanSnapshot.isEmpty() || !cleanSnapshot.equals(snapshotCurrentDocument());
    }

    private void markClean() {
        cleanSnapshot = snapshotCurrentDocument();
    }

    private void persistDocument(ChangelogDocument.Document document) throws Exception {
        Path output = FMLPaths.GAMEDIR.get().resolve("changelog_opt").resolve("changelog.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, ChangelogDocument.toPrettyJson(document), StandardCharsets.UTF_8);
    }

    private Path resolveImportPath() {
        Path exported = FMLPaths.GAMEDIR.get().resolve("changelog_opt").resolve("changelog.json");
        if (Files.isRegularFile(exported)) {
            return exported;
        }
        Path cache = FMLPaths.GAMEDIR.get().resolve(".cache")
                .resolve(ChangelogEntry.getCacheFileNameForCurrentLanguage());
        return Files.isRegularFile(cache) ? cache : null;
    }

    private void loadDocument(ChangelogDocument.Document document) {
        entries.clear();
        for (ChangelogDocument.EntryData entryData : document.entries) {
            EditableEntry entry = new EditableEntry();
            entry.version = entryData.version;
            entry.date = entryData.date;
            entry.title = entryData.title;
            entry.types = new ArrayList<>(entryData.types);
            entry.tags = new ArrayList<>(entryData.tags);
            entry.color = entryData.accent;
            for (ChangeNode node : entryData.changes) {
                entry.changeTree.add(EditableChangeNode.fromChangeNode(node));
            }
            entry.syncLegacyChanges();
            entries.add(entry);
        }
        tagColors.clear();
        tagColors.putAll(document.tagColors);
        footerText = document.footer;
        tagManagementScroll = 0;
        tagDropdownOpen = false;
        removeTagManagementWidgets();
        buildTagManagementWidgets();
        selectedEntryIndex = entries.isEmpty() ? -1 : 0;
        selectedPath.clear();
        resetTreeView();
        boundEntry = null;
        loadSelectedFields();
        refreshEntryButtons();
    }

    private void confirmImport() {
        if (pendingImportDocument == null) {
            cancelImport();
            return;
        }
        ChangelogDocument.Document document = pendingImportDocument;
        clearPendingImport();
        loadDocument(document);
        markClean();
        showToast(commandDeckText("imported_v2_legacy").getString());
    }

    private void cancelImport() {
        clearPendingImport();
    }

    private void clearPendingImport() {
        importConfirmationVisible = false;
        pendingImportDocument = null;
        pendingImportPath = null;
    }

    private void renderImportConfirmation(GuiGraphics graphics) {
        int dialogWidth = Math.min(360, this.width - 24);
        int dialogHeight = 108;
        int dialogLeft = (this.width - dialogWidth) / 2;
        int dialogTop = (this.height - dialogHeight) / 2;
        int dialogRight = dialogLeft + dialogWidth;
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        graphics.fill(dialogLeft, dialogTop, dialogRight, dialogTop + dialogHeight, 0xFF1D2631);
        graphics.fill(dialogLeft, dialogTop, dialogRight, dialogTop + 2, ACCENT);
        graphics.drawString(this.font, commandDeckText("import_document"),
                dialogLeft + 16, dialogTop + 14, ACCENT);
        graphics.drawString(this.font, commandDeckText("discard_unsaved"),
                dialogLeft + 16, dialogTop + 34, TEXT);
        String source = pendingImportPath == null ? "selected file" : pendingImportPath.getFileName().toString();
        graphics.drawString(this.font, source, dialogLeft + 16, dialogTop + 49, MUTED);

        int buttonY = dialogTop + 78;
        int confirmLeft = dialogRight - 84;
        int cancelLeft = dialogRight - 160;
        graphics.fill(cancelLeft, buttonY, cancelLeft + 68, buttonY + 18, 0xFF374454);
        graphics.fill(confirmLeft, buttonY, confirmLeft + 68, buttonY + 18, ACCENT);
        graphics.drawString(this.font, commandDeckText("cancel"), cancelLeft + 15, buttonY + 5, TEXT);
        graphics.drawString(this.font, commandDeckText("confirm_import"),
                confirmLeft + 16, buttonY + 5, 0xFF18201F);
    }

    private boolean handleImportConfirmationClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        int dialogWidth = Math.min(360, this.width - 24);
        int dialogHeight = 108;
        int dialogLeft = (this.width - dialogWidth) / 2;
        int dialogTop = (this.height - dialogHeight) / 2;
        int buttonY = dialogTop + 78;
        int confirmLeft = dialogLeft + dialogWidth - 84;
        int cancelLeft = dialogLeft + dialogWidth - 160;
        if (mouseY >= buttonY && mouseY <= buttonY + 18) {
            if (mouseX >= confirmLeft && mouseX <= confirmLeft + 68) {
                confirmImport();
            } else if (mouseX >= cancelLeft && mouseX <= cancelLeft + 68) {
                cancelImport();
            }
        }
        return true;
    }

    @Override
    public void onClose() {
        commitInlineNodeEdit();
        if (importConfirmationVisible) {
            cancelImport();
            return;
        }
        if (tagColorPicker != null) {
            closeTagColorPicker();
            return;
        }
        if (tagDropdownOpen) {
            tagDropdownOpen = false;
            updateInspectorLayout();
            return;
        }
        try {
            ChangelogDocument.Document document = buildDocument();
            persistDocument(document);
            ChangelogEntry.applyDocument(document);
            markClean();
        } catch (Exception exception) {
            CTNHChangelog.LOGGER.error("Failed to save changelog edits", exception);
        showToast(commandDeckText("save_failed").getString());
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class TagPillLayout {
        private final int index;
        private final int x;
        private final int y;
        private final int width;

        private TagPillLayout(int index, int x, int y, int width) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.width = width;
        }
    }

    private static final class TreeRow {
        private final List<Integer> path;
        private final EditableChangeNode node;
        private final int y;
        private final int height;
        private final int left;

        private TreeRow(List<Integer> path, EditableChangeNode node, int y, int height, int left) {
            this.path = path;
            this.node = node;
            this.y = y;
            this.height = height;
            this.left = left;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= left - 4 && mouseX <= left + 320
                    && mouseY >= y && mouseY <= y + height;
        }
    }
}
