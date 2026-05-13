package com.mmyddd.mcmod.changelog.client.editor;

import com.mmyddd.mcmod.changelog.client.ChangelogUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 编辑器的条目编辑标签页
 * 左侧为条目列表，右侧为选中条目的详情编辑面板
 */
public class EditorEntriesTab {
    /** 左侧列表宽度 */
    private static final int LIST_WIDTH = 320;
    /** 每个条目项的高度 */
    private static final int ENTRY_HEIGHT = 44;
    /** 详情面板左边界 */
    private static final int DETAIL_LEFT = LIST_WIDTH + 10;
    /** 通用边距 */
    private static final int MARGIN = 10;
    /** 所有可用的类型列表 */
    private static final String[] ALL_TYPES = {"major", "minor", "patch", "hotfix", "danger"};

    private final ChangelogEditorScreen editor;
    /** 当前选中的条目索引，-1 表示无选中 */
    private int selectedIndex = -1;

    // 左侧列表滚动状态
    private double listScrollAmount;
    private int listContentHeight;
    private int listTop = 35;
    private int listBottom;
    private boolean listDragging;

    // 右侧详情滚动状态
    private double detailScrollAmount;
    private int detailContentHeight;
    private int detailTop = 35;
    private int detailBottom;
    private boolean detailDragging;

    // 详情面板的 EditBox 字段
    private EditBox versionBox;
    private EditBox dateBox;
    private EditBox titleBox;
    private EditBox colorHexBox;
    private EditBox newChangeBox;

    // 颜色选择器
    private EditorColorPicker colorPicker;
    private boolean showColorPicker;
    /** 颜色选择器弹窗位置 */
    private int pickerX, pickerY;

    // 标签下拉框状态
    private boolean tagDropdownOpen = false;
    private int tagDropdownX, tagDropdownY;

    // Changes 条目双击编辑状态
    private int editingChangeIndex = -1;
    private EditBox editChangeBox;
    private long changeLastClickTime = 0;

    // 标签拖拽状态
    private boolean tagDragging = false;
    private int tagDragIndex = -1;
    private int tagDragMouseX, tagDragMouseY;
    private long tagPressTime = 0;
    private static final long DRAG_HOLD_MS = 300;

    // 操作按钮
    private Button addEntryButton;
    private Button moveUpButton;
    private Button moveDownButton;
    private Button deleteButton;
    private Button addChangeButton;

    public EditorEntriesTab(ChangelogEditorScreen editor) {
        this.editor = editor;
    }

    /**
     * 创建按钮并注册到屏幕，初始化布局边界
     */
    public void addWidgets(ChangelogEditorScreen screen) {
        listBottom = screen.getScreenHeight() - 10;
        detailBottom = screen.getScreenHeight() - 10;

        // "新增条目" 按钮，位于列表底部
        addEntryButton = Button.builder(
                Component.literal("+ New Entry"),
                btn -> addEntry()
        ).bounds(10, listBottom - 24, LIST_WIDTH - 20, 20).build();

        // 详情面板底部操作按钮
        moveUpButton = Button.builder(
                Component.literal("↑"), // 上箭头
                btn -> moveEntryUp(selectedIndex)
        ).bounds(DETAIL_LEFT, detailBottom - 24, 40, 20).build();

        moveDownButton = Button.builder(
                Component.literal("↓"), // 下箭头
                btn -> moveEntryDown(selectedIndex)
        ).bounds(DETAIL_LEFT + 44, detailBottom - 24, 40, 20).build();

        deleteButton = Button.builder(
                Component.literal("Delete"),
                btn -> deleteEntry(selectedIndex)
        ).bounds(DETAIL_LEFT + 88, detailBottom - 24, 60, 20).build();

        // 添加变更按钮
        addChangeButton = Button.builder(
                Component.literal("+ Add Change"),
                btn -> addChange()
        ).bounds(0, 0, 100, 16).build(); // 位置在渲染时动态设置

        screen.addWidgetToScreen(addEntryButton);
        screen.addWidgetToScreen(moveUpButton);
        screen.addWidgetToScreen(moveDownButton);
        screen.addWidgetToScreen(deleteButton);
        screen.addWidgetToScreen(addChangeButton);

        // 按钮初始可见性
        updateButtonVisibility();

        // 如果有选中条目，重建 EditBox（从其他标签页切回时恢复）
        if (selectedIndex >= 0 && selectedIndex < editor.getEntries().size()) {
            createEditBoxes();
        }
    }

    /**
     * 保存当前编辑数据，移除所有 EditBox 和按钮
     */
    public void removeWidgets(ChangelogEditorScreen screen) {
        syncDetailToEntry();
        exitEditChange();
        destroyEditBoxes(screen);

        screen.removeWidgetFromScreen(addEntryButton);
        screen.removeWidgetFromScreen(moveUpButton);
        screen.removeWidgetFromScreen(moveDownButton);
        screen.removeWidgetFromScreen(deleteButton);
        screen.removeWidgetFromScreen(addChangeButton);
    }

    /**
     * 选中指定索引的条目，销毁旧 EditBox 并创建新的
     */
    public void selectEntry(int index) {
        if (selectedIndex == index) return;

        syncDetailToEntry();
        destroyEditBoxes(editor);
        selectedIndex = index;

        createEditBoxes();
        updateButtonVisibility();
        detailScrollAmount = 0;
    }

    /**
     * 根据当前 selectedIndex 创建 EditBox 并填充数据
     */
    private void createEditBoxes() {
        if (selectedIndex < 0 || selectedIndex >= editor.getEntries().size()) return;

        EditableEntry entry = editor.getEntries().get(selectedIndex);
        Font font = editor.getScreenFont();

        versionBox = new EditBox(font, 0, 0, 120, 16, Component.literal("Version"));
        versionBox.setValue(entry.version);
        versionBox.setMaxLength(32);

        dateBox = new EditBox(font, 0, 0, 120, 16, Component.literal("Date"));
        dateBox.setValue(entry.date != null ? entry.date : "");
        dateBox.setMaxLength(32);

        titleBox = new EditBox(font, 0, 0, 200, 16, Component.literal("Title"));
        titleBox.setValue(entry.title != null ? entry.title : "");
        titleBox.setMaxLength(128);

        colorHexBox = new EditBox(font, 0, 0, 80, 16, Component.literal("Color"));
        colorHexBox.setValue(String.format("#%06X", entry.color & 0x00FFFFFF));
        colorHexBox.setMaxLength(7);

        newChangeBox = new EditBox(font, 0, 0, 200, 16, Component.literal("New Change"));
        newChangeBox.setMaxLength(256);

        editChangeBox = new EditBox(font, 0, 0, 200, 16, Component.literal("Edit Change"));
        editChangeBox.setMaxLength(256);
        editChangeBox.visible = false;
        editChangeBox.active = false;

        editor.addWidgetToScreen(versionBox);
        editor.addWidgetToScreen(dateBox);
        editor.addWidgetToScreen(titleBox);
        editor.addWidgetToScreen(colorHexBox);
        editor.addWidgetToScreen(newChangeBox);
        editor.addWidgetToScreen(editChangeBox);
    }

    /**
     * 更新按钮可见性
     */
    private void updateButtonVisibility() {
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < editor.getEntries().size();
        if (moveUpButton != null) moveUpButton.visible = hasSelection;
        if (moveDownButton != null) moveDownButton.visible = hasSelection;
        if (deleteButton != null) deleteButton.visible = hasSelection;
        if (addChangeButton != null) addChangeButton.visible = hasSelection;
    }

    // ==================== 渲染 ====================

    /**
     * 渲染整个标签页
     */
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        renderEntryList(graphics, font, mouseX, mouseY);
        renderDetailPanel(graphics, font, mouseX, mouseY);

        // 渲染颜色选择器弹窗（覆盖在所有内容之上）
        if (showColorPicker && colorPicker != null) {
            colorPicker.render(graphics, font, pickerX, pickerY, mouseX, mouseY);
        }
    }

    /**
     * 渲染左侧条目列表
     */
    private void renderEntryList(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // 列表背景
        graphics.fill(0, listTop, LIST_WIDTH, listBottom, 0xFF1A1E2B);
        // 分隔线
        graphics.fill(LIST_WIDTH - 1, listTop, LIST_WIDTH, listBottom, 0xFF404760);

        List<EditableEntry> entries = editor.getEntries();

        // 计算内容总高度
        listContentHeight = entries.size() * ENTRY_HEIGHT;
        int viewHeight = listBottom - listTop;

        // 限制滚动范围
        int maxScroll = Math.max(0, listContentHeight - viewHeight);
        listScrollAmount = Mth.clamp(listScrollAmount, 0, maxScroll);

        // 裁剪列表区域
        graphics.enableScissor(0, listTop, LIST_WIDTH - 4, listBottom);

        int y = listTop - (int) listScrollAmount;
        for (int i = 0; i < entries.size(); i++) {
            EditableEntry entry = entries.get(i);

            // 仅渲染可见区域内的条目
            if (y + ENTRY_HEIGHT > listTop && y < listBottom) {
                boolean selected = (i == selectedIndex);
                boolean hovered = (mouseX >= 0 && mouseX < LIST_WIDTH - 4
                        && mouseY >= y && mouseY < y + ENTRY_HEIGHT
                        && mouseY >= listTop && mouseY < listBottom);

                // 选中高亮
                if (selected) {
                    graphics.fill(0, y, LIST_WIDTH - 4, y + ENTRY_HEIGHT, 0x40FFFFFF);
                } else if (hovered) {
                    graphics.fill(0, y, LIST_WIDTH - 4, y + ENTRY_HEIGHT, 0x20FFFFFF);
                }

                // 左侧颜色条（4px 宽）
                int barColor = entry.color | 0xFF000000;
                graphics.fill(0, y, 4, y + ENTRY_HEIGHT, barColor);

                // 版本号标签
                String versionText = entry.version;
                int versionWidth = font.width(versionText) + 10;
                graphics.fill(8, y + 6, 8 + versionWidth, y + 20, 0xFFFFAA00);
                graphics.drawString(font, versionText, 13, y + 9, 0xFF000000);

                // 标题（截断）
                String title = entry.title != null ? entry.title : "";
                int maxTitleWidth = LIST_WIDTH - 20 - versionWidth;
                if (maxTitleWidth > 20) {
                    String truncated = truncateText(font, title, maxTitleWidth);
                    graphics.drawString(font, truncated, 10 + versionWidth + 4, y + 9, 0xFFDDDDDD);
                }

                // 第二行：类型标签 + 右对齐日期
                int secondLineY = y + 26;
                int tagX = 10;
                for (String type : entry.types) {
                    int typeColor = ChangelogUtils.getTypeColor(type);
                    String typeLabel = ChangelogUtils.getTranslatedTypeTag(type);
                    int tw = font.width(typeLabel) + 6;
                    if (tagX + tw < LIST_WIDTH - 80) {
                        graphics.fill(tagX, secondLineY - 1, tagX + tw, secondLineY + 9, typeColor);
                        graphics.drawString(font, typeLabel, tagX + 3, secondLineY, 0xFFFFFFFF);
                        tagX += tw + 4;
                    }
                }

                // 日期（右对齐）
                String date = entry.date != null ? entry.date : "";
                if (!date.isEmpty()) {
                    int dateWidth = font.width(date);
                    graphics.drawString(font, date, LIST_WIDTH - 10 - dateWidth, secondLineY, 0xFF888888);
                }
            }

            y += ENTRY_HEIGHT;
        }

        graphics.disableScissor();

        // 渲染列表滚动条
        if (listContentHeight > viewHeight) {
            renderScrollbar(graphics, LIST_WIDTH - 6, listTop, LIST_WIDTH - 2, listBottom,
                    listScrollAmount, listContentHeight);
        }
    }

    /**
     * 渲染右侧详情编辑面板
     */
    private void renderDetailPanel(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // 无选中时显示提示
        if (selectedIndex < 0 || selectedIndex >= editor.getEntries().size()) {
            String hint = "Select an entry to edit";
            int hintX = DETAIL_LEFT + (editor.getScreenWidth() - DETAIL_LEFT) / 2 - font.width(hint) / 2;
            int hintY = listTop + 60;
            graphics.drawString(font, hint, hintX, hintY, 0xFF888888);
            return;
        }

        EditableEntry entry = editor.getEntries().get(selectedIndex);
        int panelRight = editor.getScreenWidth() - 10;
        int viewHeight = detailBottom - detailTop;

        // 计算内容总高度（用于滚动）
        detailContentHeight = calculateDetailContentHeight(font, entry, panelRight - DETAIL_LEFT);

        int maxScroll = Math.max(0, detailContentHeight - viewHeight);
        detailScrollAmount = Mth.clamp(detailScrollAmount, 0, maxScroll);

        // 裁剪详情面板区域
        graphics.enableScissor(DETAIL_LEFT, detailTop, panelRight, detailBottom);

        int x = DETAIL_LEFT + MARGIN;
        int y = detailTop - (int) detailScrollAmount;
        int fieldX = x + 70;
        int contentWidth = panelRight - x - MARGIN;

        // 1. 版本号
        graphics.drawString(font, "Version:", x, y + 3, 0xFFAAAAAA);
        positionEditBox(versionBox, fieldX, y);
        y += 24;

        // 2. 日期
        graphics.drawString(font, "Date:", x, y + 3, 0xFFAAAAAA);
        positionEditBox(dateBox, fieldX, y);
        y += 24;

        // 3. 标题
        graphics.drawString(font, "Title:", x, y + 3, 0xFFAAAAAA);
        positionEditBox(titleBox, fieldX, y);
        y += 24;

        // 4. 类型药丸
        graphics.drawString(font, "Types:", x, y + 3, 0xFFAAAAAA);
        renderTypePills(graphics, font, fieldX, y, entry.types, mouseX, mouseY);
        y += 28;

        // 5. 颜色
        graphics.drawString(font, "Color:", x, y + 3, 0xFFAAAAAA);
        // 预览色块
        int previewX = fieldX;
        graphics.fill(previewX, y, previewX + 16, y + 16, entry.color | 0xFF000000);
        graphics.fill(previewX, y, previewX + 16, y + 1, 0xFFFFFFFF);
        graphics.fill(previewX, y + 15, previewX + 16, y + 16, 0xFFFFFFFF);
        graphics.fill(previewX, y, previewX + 1, y + 16, 0xFFFFFFFF);
        graphics.fill(previewX + 15, y, previewX + 16, y + 16, 0xFFFFFFFF);
        // 十六进制输入框
        positionEditBox(colorHexBox, previewX + 20, y);
        y += 24;

        // 6. 标签
        graphics.drawString(font, "Tags:", x, y + 3, 0xFFAAAAAA);
        int[] tagEndPos = renderTagPills(graphics, font, fieldX, y, entry.tags, panelRight, mouseX, mouseY);
        y = tagEndPos[1] + 18;

        // 7. 变更列表（z-translate 使其渲染在按钮之下）
        graphics.drawString(font, Component.translatable("ctnhchangelog.changes").append(":"), x, y + 5, 0xFFAAAAAA);
        graphics.drawString(font, Component.translatable("ctnhchangelog.editor.double_click_edit"), fieldX, y + 5, 0xFF666666);
        y += 18;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, -100);

        int changeLeft = fieldX;
        for (int i = 0; i < entry.changes.size(); i++) {
            String change = entry.changes.get(i);
            int availableWidth = panelRight - changeLeft - 30;

            // 使用 font.split 进行自动换行
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.literal(change), availableWidth);

            int lineX = changeLeft;
            int lineY = y;

            if (i == editingChangeIndex) {
                // 编辑态：显示 EditBox
                positionEditBox(editChangeBox, lineX, lineY);
                editChangeBox.visible = true;
                editChangeBox.active = true;
                editChangeBox.setWidth(availableWidth);
                y = lineY + 20;
            } else {
                // 普通态：渲染文字
                for (int li = 0; li < lines.size(); li++) {
                    if (li == 0) {
                        // 第一行包含删除按钮
                        graphics.drawString(font, lines.get(li), lineX, lineY + 2, 0xFFDDDDDD);
                        // "x" 删除按钮
                        int cx = lineX + font.width(lines.get(li)) + 4;
                        boolean cxHovered = mouseX >= cx && mouseX < cx + 10
                                && mouseY >= lineY && mouseY < lineY + 14;
                        graphics.drawString(font, "x", cx, lineY + 2, cxHovered ? 0xFFFF5555 : 0xFFCCCCCC);
                    } else {
                        // 续行缩进
                        graphics.drawString(font, lines.get(li), lineX + 8, lineY + 2, 0xFFDDDDDD);
                    }
                    lineY += 14;
                }
                y = lineY + 4;
            }
        }

        // 隐藏未在编辑的 editChangeBox
        if (editingChangeIndex < 0 && editChangeBox != null) {
            editChangeBox.visible = false;
            editChangeBox.active = false;
        }

        graphics.pose().popPose();

        // 新变更输入框 + 添加按钮
        positionEditBox(newChangeBox, fieldX, y);
        positionButton(addChangeButton, fieldX + 204, y);

        graphics.disableScissor();

        // 渲染详情滚动条
        if (detailContentHeight > viewHeight) {
            renderScrollbar(graphics, editor.getScreenWidth() - 8, detailTop, editor.getScreenWidth() - 4, detailBottom,
                    detailScrollAmount, detailContentHeight);
        }

        // 标签下拉菜单最后渲染，确保在所有内容之上
        if (tagDropdownOpen && selectedIndex >= 0 && selectedIndex < editor.getEntries().size()) {
            renderTagDropdown(graphics, font, mouseX, mouseY);
        }
    }

    /**
     * 计算详情面板的内容总高度（用于滚动）
     */
    private int calculateDetailContentHeight(Font font, EditableEntry entry, int contentWidth) {
        int h = 0;
        h += 24; // version
        h += 24; // date
        h += 24; // title
        h += 28; // types
        h += 24; // color
        h += 22; // tags label
        h += 18; // changes label

        // 变更列表高度
        int changeWidth = contentWidth - 70 - MARGIN;
        for (String change : entry.changes) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.literal(change), changeWidth);
            h += lines.size() * 14 + 4;
        }

        h += 24; // newChangeBox + addChangeButton
        h += 30; // 底部按钮区域

        return h;
    }

    /**
     * 渲染类型药丸按钮（点击切换选中/取消）
     */
    private void renderTypePills(GuiGraphics graphics, Font font, int x, int y,
                                  List<String> selectedTypes, int mouseX, int mouseY) {
        int currentX = x;
        for (String type : ALL_TYPES) {
            boolean selected = selectedTypes.contains(type);
            int color = ChangelogUtils.getTypeColor(type);
            String label = ChangelogUtils.getTranslatedTypeTag(type);
            int pillWidth = font.width(label) + 16;

            if (selected) {
                graphics.fill(currentX, y, currentX + pillWidth, y + 20, color);
                graphics.drawString(font, label, currentX + 8, y + 5, 0xFFFFFFFF);
            } else {
                int dimmed = (color & 0x00FFFFFF) | 0x30000000;
                graphics.fill(currentX, y, currentX + pillWidth, y + 20, dimmed);
                graphics.drawString(font, label, currentX + 8, y + 5, 0xFFAAAAAA);
            }

            // 悬停边框
            if (mouseX >= currentX && mouseX < currentX + pillWidth
                    && mouseY >= y && mouseY < y + 20) {
                graphics.fill(currentX, y, currentX + pillWidth, y + 1, 0xFFFFAA00);
                graphics.fill(currentX, y + 19, currentX + pillWidth, y + 20, 0xFFFFAA00);
            }

            currentX += pillWidth + 6;
        }
    }

    /**
     * 渲染标签选择区域：下拉按钮 + 已选标签的可拖拽药丸
     * @return int[] {最后X, 最后Y}
     */
    private int[] renderTagPills(GuiGraphics graphics, Font font, int x, int y,
                                  List<String> tags, int panelRight, int mouseX, int mouseY) {
        int currentX = x;
        int currentY = y;

        // 下拉按钮 "▼ +"
        String dropLabel = "▼ +";
        int dropW = font.width(dropLabel) + 12;
        boolean dropHover = mouseX >= currentX && mouseX < currentX + dropW
                && mouseY >= currentY && mouseY < currentY + 18;
        graphics.fill(currentX, currentY, currentX + dropW, currentY + 18, dropHover ? 0xFF404760 : 0xFF2D3349);
        graphics.fill(currentX, currentY, currentX + dropW, currentY + 1, 0xFF55FF55);
        graphics.fill(currentX, currentY + 17, currentX + dropW, currentY + 18, 0xFF55FF55);
        graphics.fill(currentX, currentY, currentX + 1, currentY + 18, 0xFF55FF55);
        graphics.fill(currentX + dropW - 1, currentY, currentX + dropW, currentY + 18, 0xFF55FF55);
        graphics.drawString(font, dropLabel, currentX + 6, currentY + 3, 0xFF55FF55);

        // 动态更新下拉框位置，使其跟随滚动
        tagDropdownX = currentX;
        tagDropdownY = currentY + 20;

        currentX += dropW + 8;

        // 已选标签药丸
        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.get(i);
            int tagColor = editor.getTagColors().getOrDefault(tag, 0xFF888888);
            int pillWidth = font.width(tag) + 22;

            // 换行
            if (currentX + pillWidth > panelRight - MARGIN) {
                currentX = x;
                currentY += 20;
            }

            // 拖拽中的药丸跟随鼠标
            boolean isDragged = tagDragging && tagDragIndex == i;
            int drawX = isDragged ? tagDragMouseX - pillWidth / 2 : currentX;
            int drawY = isDragged ? tagDragMouseY - 9 : currentY;

            int bgColor = isDragged ? (tagColor & 0x00FFFFFF) | 0x40000000 : tagColor;
            graphics.fill(drawX, drawY, drawX + pillWidth, drawY + 18, bgColor);
            graphics.drawString(font, tag, drawX + 4, drawY + 3, 0xFFFFFFFF);

            if (!isDragged) {
                int xBtnX = drawX + font.width(tag) + 6;
                boolean xHover = mouseX >= xBtnX && mouseX < xBtnX + 10
                        && mouseY >= drawY && mouseY < drawY + 18;
                graphics.drawString(font, "✕", xBtnX, drawY + 3, xHover ? 0xFFFF5555 : 0xFFCCCCCC);
                currentX += pillWidth + 6;
            }
        }

        // 下拉菜单不在这里渲染，由 renderDetailPanel 在 disableScissor 之后渲染，避免被裁剪

        return new int[]{currentX, currentY};
    }

    /**
     * 渲染标签下拉菜单（显示已定义颜色但尚未添加到条目的标签）
     */
    private void renderTagDropdown(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (selectedIndex < 0) { tagDropdownOpen = false; return; }
        EditableEntry entry = editor.getEntries().get(selectedIndex);
        List<String> available = new ArrayList<>();
        for (String tag : editor.getTagColors().keySet()) {
            if (!entry.tags.contains(tag)) available.add(tag);
        }
        if (available.isEmpty()) { tagDropdownOpen = false; return; }

        int itemHeight = 20;
        int menuHeight = available.size() * itemHeight;
        int menuWidth = 120;

        // 将下拉菜单提升到更高的 z 层，避免被其他 fill/drawString 渲染层覆盖
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);

        graphics.fill(tagDropdownX, tagDropdownY, tagDropdownX + menuWidth,
                tagDropdownY + menuHeight, 0xFF1A1E2B);
        graphics.fill(tagDropdownX, tagDropdownY, tagDropdownX + menuWidth, tagDropdownY + 1, 0xFF404760);
        graphics.fill(tagDropdownX, tagDropdownY + menuHeight - 1, tagDropdownX + menuWidth,
                tagDropdownY + menuHeight, 0xFF404760);
        graphics.fill(tagDropdownX, tagDropdownY, tagDropdownX + 1, tagDropdownY + menuHeight, 0xFF404760);
        graphics.fill(tagDropdownX + menuWidth - 1, tagDropdownY, tagDropdownX + menuWidth,
                tagDropdownY + menuHeight, 0xFF404760);

        for (int i = 0; i < available.size(); i++) {
            String tag = available.get(i);
            int iy = tagDropdownY + i * itemHeight;
            boolean hover = mouseX >= tagDropdownX && mouseX < tagDropdownX + menuWidth
                    && mouseY >= iy && mouseY < iy + itemHeight;
            int color = editor.getTagColors().getOrDefault(tag, 0xFF888888);

            if (hover) {
                graphics.fill(tagDropdownX + 1, iy, tagDropdownX + menuWidth - 1, iy + itemHeight, 0xFF404760);
            }
            graphics.fill(tagDropdownX + 4, iy + 4, tagDropdownX + 14, iy + 16, color);
            graphics.drawString(font, tag, tagDropdownX + 18, iy + 4, 0xFFDDDDDD);
        }

        graphics.pose().popPose();
    }

    /**
     * 渲染通用滚动条
     */
    private void renderScrollbar(GuiGraphics graphics, int barLeft, int barTop, int barRight, int barBottom,
                                  double scrollAmount, int contentHeight) {
        int viewHeight = barBottom - barTop;
        if (contentHeight > viewHeight) {
            int barHeight = (int) ((float) viewHeight * viewHeight / contentHeight);
            barHeight = Math.max(barHeight, 10); // 最小滚动条高度
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            int barY = maxScroll > 0 ? (int) (scrollAmount * (viewHeight - barHeight) / maxScroll) : 0;
            barY = Mth.clamp(barY, 0, viewHeight - barHeight);

            // 滚动条轨道
            graphics.fill(barLeft, barTop, barRight, barBottom, 0x33AAAAAA);
            // 滚动条滑块
            graphics.fill(barLeft, barTop + barY, barRight, barTop + barY + barHeight, 0xFFAAAAAA);
        }
    }

    // ==================== 输入处理 ====================

    /**
     * 鼠标点击事件处理
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 颜色选择器打开时，优先委托给颜色选择器
        if (showColorPicker && colorPicker != null) {
            boolean handled = colorPicker.mouseClicked(mouseX, mouseY, button, pickerX, pickerY);
            if (handled) return true;
            // 点击颜色选择器外部则关闭
            showColorPicker = false;
            return false;
        }

        // 检测列表条目点击
        if (mouseX >= 0 && mouseX < LIST_WIDTH - 4 && mouseY >= listTop && mouseY < listBottom) {
            int clickedIndex = (int) ((mouseY - listTop + listScrollAmount) / ENTRY_HEIGHT);
            if (clickedIndex >= 0 && clickedIndex < editor.getEntries().size()) {
                selectEntry(clickedIndex);
                return true;
            }
        }

        // 以下检测需要有选中条目
        if (selectedIndex < 0 || selectedIndex >= editor.getEntries().size()) {
            return false;
        }

        EditableEntry entry = editor.getEntries().get(selectedIndex);
        Font font = editor.getScreenFont();
        int x = DETAIL_LEFT + MARGIN;
        int fieldX = x + 70;
        int panelRight = editor.getScreenWidth() - 10;

        // 动态计算 Y 坐标（与 renderDetailPanel 完全一致）
        int y = detailTop - (int) detailScrollAmount;

        // 1. Version: y → y+24
        y += 24;
        // 2. Date: y → y+24
        y += 24;
        // 3. Title: y → y+24
        y += 24;

        // 4. 类型药丸区域（点击切换选中/取消）
        {
            int pillX = fieldX;
            for (String type : ALL_TYPES) {
                String label = ChangelogUtils.getTranslatedTypeTag(type);
                int pillWidth = font.width(label) + 16;
                if (mouseX >= pillX && mouseX < pillX + pillWidth
                        && mouseY >= y && mouseY < y + 20) {
                    toggleType(entry, type);
                    return true;
                }
                pillX += pillWidth + 6;
            }
        }
        y += 28;

        // 5. 颜色区域
        int colorBlockX = fieldX;
        if (mouseX >= colorBlockX && mouseX < colorBlockX + 16
                && mouseY >= y && mouseY < y + 16) {
            openColorPicker(entry);
            return true;
        }
        y += 24;

        // 6. 标签区域
        {
            int tagRowX = fieldX;
            int tagRowY = y;

            // 下拉按钮检测
            String dropLabel = "▼ +";
            int dropW = font.width(dropLabel) + 12;
            if (mouseX >= tagRowX && mouseX < tagRowX + dropW
                    && mouseY >= tagRowY && mouseY < tagRowY + 18) {
                tagDropdownOpen = !tagDropdownOpen;
                // 位置由 renderTagPills 动态计算，不需要在这里设置
                return true;
            }
            tagRowX += dropW + 8;

            // 已选标签药丸检测
            for (int i = 0; i < entry.tags.size(); i++) {
                String tag = entry.tags.get(i);
                int pillWidth = font.width(tag) + 22;
                if (tagRowX + pillWidth > panelRight - MARGIN) {
                    tagRowX = fieldX;
                    tagRowY += 20;
                }
                if (mouseX >= tagRowX && mouseX < tagRowX + pillWidth
                        && mouseY >= tagRowY && mouseY < tagRowY + 18) {
                    // "✕" 删除
                    int xBtnX = tagRowX + font.width(tag) + 6;
                    if (mouseX >= xBtnX && mouseX < xBtnX + 10) {
                        entry.tags.remove(i);
                        return true;
                    }
                    // 开始长按计时（准备拖拽）
                    tagPressTime = System.currentTimeMillis();
                    tagDragIndex = i;
                    tagDragMouseX = (int) mouseX;
                    tagDragMouseY = (int) mouseY;
                    return true;
                }
                tagRowX += pillWidth + 6;
            }

            // 下拉菜单项检测
            if (tagDropdownOpen) {
                List<String> available = new ArrayList<>();
                for (String t : editor.getTagColors().keySet()) {
                    if (!entry.tags.contains(t)) available.add(t);
                }
                for (int i = 0; i < available.size(); i++) {
                    int iy = tagDropdownY + i * 20;
                    if (mouseX >= tagDropdownX && mouseX < tagDropdownX + 120
                            && mouseY >= iy && mouseY < iy + 20) {
                        entry.tags.add(available.get(i));
                        tagDropdownOpen = false;
                        return true;
                    }
                }
                tagDropdownOpen = false;
                return true;
            }

            y = tagRowY + 18;
        }

        // 7. Changes 标签: y → y+18
        y += 18;

        // 变更列表
        int changeLeft = fieldX;
        int changeAvailWidth = panelRight - changeLeft - 30;
        for (int i = 0; i < entry.changes.size(); i++) {
            // 编辑态条目高度为 20，普通态为 lines.size() * 14 + 4
            if (i == editingChangeIndex) {
                // 编辑态：检测 EditBox 区域
                if (mouseX >= changeLeft && mouseX < changeLeft + changeAvailWidth
                        && mouseY >= y && mouseY < y + 20) {
                    // 点击编辑中的条目，不做处理（让 EditBox 自己处理）
                    return false;
                }
                y += 20;
                continue;
            }

            String change = entry.changes.get(i);
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.literal(change), changeAvailWidth);

            // "x" 按钮在第一行末尾
            int cx = changeLeft + font.width(lines.get(0)) + 4;
            if (mouseX >= cx && mouseX < cx + 10
                    && mouseY >= y && mouseY < y + 14) {
                removeChange(i);
                return true;
            }

            // 双击检测：点击 change 文本区域
            int changeHeight = lines.size() * 14 + 4;
            if (mouseX >= changeLeft && mouseX < changeLeft + changeAvailWidth
                    && mouseY >= y && mouseY < y + changeHeight) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - changeLastClickTime < 300) {
                    // 双击：进入编辑模式
                    startEditChange(i);
                    changeLastClickTime = 0;
                    return true;
                }
                changeLastClickTime = currentTime;
                // 单击：如果正在编辑其他条目，先确认
                if (editingChangeIndex >= 0) {
                    confirmEditChange();
                }
                return true;
            }

            y += lines.size() * 14 + 4;
        }

        // 委托给 EditBox（通过 Minecraft 的 widget 系统自动处理）
        return false;
    }

    /**
     * 鼠标滚轮事件处理
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 颜色选择器打开时不处理滚动
        if (showColorPicker) return false;

        int scrollStep = (int) (delta * 12);

        // 判断鼠标在哪个区域
        if (mouseX < LIST_WIDTH) {
            // 左侧列表滚动
            int viewHeight = listBottom - listTop;
            int maxScroll = Math.max(0, listContentHeight - viewHeight);
            if (maxScroll > 0) {
                listScrollAmount = Mth.clamp(listScrollAmount - scrollStep, 0, maxScroll);
                return true;
            }
        } else {
            // 右侧详情滚动
            int viewHeight = detailBottom - detailTop;
            int maxScroll = Math.max(0, detailContentHeight - viewHeight);
            if (maxScroll > 0) {
                detailScrollAmount = Mth.clamp(detailScrollAmount - scrollStep, 0, maxScroll);
                return true;
            }
        }

        return false;
    }

    /**
     * 鼠标拖拽事件处理（滚动条拖拽 / 颜色选择器拖拽）
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        // 颜色选择器拖拽
        if (showColorPicker && colorPicker != null) {
            colorPicker.mouseDragged(mouseX, mouseY, button, pickerX, pickerY);
            return true;
        }

        // 标签药丸拖拽（长按触发）
        if (tagDragIndex >= 0) {
            long elapsed = System.currentTimeMillis() - tagPressTime;
            if (!tagDragging && elapsed >= DRAG_HOLD_MS) {
                tagDragging = true;
            }
            if (tagDragging) {
                tagDragMouseX = (int) mouseX;
                tagDragMouseY = (int) mouseY;

                if (selectedIndex >= 0 && selectedIndex < editor.getEntries().size()) {
                    EditableEntry entry = editor.getEntries().get(selectedIndex);
                    Font font = editor.getScreenFont();
                    int tagRowX = DETAIL_LEFT + MARGIN + 70;
                    String dropLabel = "▼ +";
                    tagRowX += font.width(dropLabel) + 12 + 8;
                    int tagRowY = detailTop - (int) detailScrollAmount + 24 * 3 + 28 + 24;
                    int panelRight = editor.getScreenWidth() - 10;

                    for (int i = 0; i < entry.tags.size(); i++) {
                        if (i == tagDragIndex) {
                            String t = entry.tags.get(i);
                            int pw = font.width(t) + 22;
                            if (tagRowX + pw > panelRight - MARGIN) {
                                tagRowX = DETAIL_LEFT + MARGIN + 70;
                                tagRowY += 20;
                            }
                            tagRowX += pw + 6;
                            continue;
                        }
                        String tag = entry.tags.get(i);
                        int pw = font.width(tag) + 22;
                        if (tagRowX + pw > panelRight - MARGIN) {
                            tagRowX = DETAIL_LEFT + MARGIN + 70;
                            tagRowY += 20;
                        }
                        int centerX = tagRowX + pw / 2;
                        if (Math.abs(tagDragMouseX - centerX) < pw / 2
                                && Math.abs(tagDragMouseY - (tagRowY + 9)) < 15) {
                            String temp = entry.tags.get(tagDragIndex);
                            entry.tags.set(tagDragIndex, entry.tags.get(i));
                            entry.tags.set(i, temp);
                            tagDragIndex = i;
                            break;
                        }
                        tagRowX += pw + 6;
                    }
                }
                return true;
            }
        }

        // 左侧列表滚动条拖拽
        if (listDragging) {
            int viewHeight = listBottom - listTop;
            int maxScroll = Math.max(0, listContentHeight - viewHeight);
            if (maxScroll > 0) {
                int barHeight = (int) ((float) viewHeight * viewHeight / listContentHeight);
                barHeight = Math.max(barHeight, 10);
                double scrollRatio = (mouseY - listTop) / (double) (viewHeight - barHeight);
                listScrollAmount = Mth.clamp(scrollRatio * maxScroll, 0, maxScroll);
            }
            return true;
        }

        // 右侧详情滚动条拖拽
        if (detailDragging) {
            int viewHeight = detailBottom - detailTop;
            int maxScroll = Math.max(0, detailContentHeight - viewHeight);
            if (maxScroll > 0) {
                int barHeight = (int) ((float) viewHeight * viewHeight / detailContentHeight);
                barHeight = Math.max(barHeight, 10);
                double scrollRatio = (mouseY - detailTop) / (double) (viewHeight - barHeight);
                detailScrollAmount = Mth.clamp(scrollRatio * maxScroll, 0, maxScroll);
            }
            return true;
        }

        return false;
    }

    /**
     * 鼠标释放事件处理
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        listDragging = false;
        detailDragging = false;
        tagDragging = false;
        tagDragIndex = -1;
        tagPressTime = 0;
        if (showColorPicker && colorPicker != null) {
            colorPicker.mouseReleased();
        }
        return false;
    }

    /**
     * 字符输入事件委托
     */
    public boolean charTyped(char codePoint, int modifiers) {
        // 颜色选择器打开时不处理
        if (showColorPicker) return false;
        return false; // EditBox 通过 widget 系统自动处理
    }

    /**
     * 按键事件处理
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 颜色选择器打开时，ESC 关闭
        if (showColorPicker) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeColorPicker();
                return true;
            }
            return false;
        }

        // Changes 编辑态：ESC 取消，Enter 确认
        if (editingChangeIndex >= 0 && editChangeBox != null && editChangeBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelEditChange();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                confirmEditChange();
                return true;
            }
            return false;
        }

        // Enter 键在颜色输入框中 → 应用颜色
        if (keyCode == GLFW.GLFW_KEY_ENTER && colorHexBox != null && colorHexBox.isFocused()) {
            applyColorFromHex();
            return true;
        }

        return false;
    }

    // ==================== 条目操作 ====================

    /**
     * 新增条目
     */
    private void addEntry() {
        syncDetailToEntry();
        List<EditableEntry> entries = editor.getEntries();
        entries.add(0, new EditableEntry());
        selectEntry(0);
        editor.showToast("Entry added");
    }

    /**
     * 删除指定索引的条目
     */
    private void deleteEntry(int index) {
        if (index < 0 || index >= editor.getEntries().size()) return;
        syncDetailToEntry();
        destroyEditBoxes(editor);
        editor.getEntries().remove(index);
        selectedIndex = -1;
        updateButtonVisibility();
        editor.showToast("Entry deleted");
    }

    /**
     * 上移条目
     */
    private void moveEntryUp(int index) {
        List<EditableEntry> entries = editor.getEntries();
        if (index <= 0 || index >= entries.size()) return;
        syncDetailToEntry();
        // 显式交换两个条目
        EditableEntry temp = entries.get(index);
        entries.set(index, entries.get(index - 1));
        entries.set(index - 1, temp);
        // 直接更新 selectedIndex 并重建 EditBox，不走 selectEntry 避免重复 sync
        selectedIndex = index - 1;
        destroyEditBoxes(editor);
        createEditBoxes();
        updateButtonVisibility();
        detailScrollAmount = 0;
    }

    /**
     * 下移条目
     */
    private void moveEntryDown(int index) {
        List<EditableEntry> entries = editor.getEntries();
        if (index < 0 || index >= entries.size() - 1) return;
        syncDetailToEntry();
        // 显式交换两个条目
        EditableEntry temp = entries.get(index);
        entries.set(index, entries.get(index + 1));
        entries.set(index + 1, temp);
        // 直接更新 selectedIndex 并重建 EditBox，不走 selectEntry 避免重复 sync
        selectedIndex = index + 1;
        destroyEditBoxes(editor);
        createEditBoxes();
        updateButtonVisibility();
        detailScrollAmount = 0;
    }

    /**
     * 从选中条目中删除标签
     */
    private void removeTag(int tagIndex) {
        if (selectedIndex < 0 || selectedIndex >= editor.getEntries().size()) return;
        EditableEntry entry = editor.getEntries().get(selectedIndex);
        if (tagIndex >= 0 && tagIndex < entry.tags.size()) {
            String removed = entry.tags.remove(tagIndex);
            editor.showToast("Tag removed: " + removed);
        }
    }

    /**
     * 添加变更到选中条目
     */
    private void addChange() {
        if (selectedIndex < 0 || selectedIndex >= editor.getEntries().size()) return;
        if (newChangeBox == null) return;

        String changeText = newChangeBox.getValue().trim();
        if (changeText.isEmpty()) return;

        EditableEntry entry = editor.getEntries().get(selectedIndex);
        entry.changes.add(changeText);
        newChangeBox.setValue("");
        editor.showToast("Change added");
    }

    /**
     * 从选中条目中删除变更
     */
    private void removeChange(int changeIndex) {
        if (selectedIndex < 0 || selectedIndex >= editor.getEntries().size()) return;
        EditableEntry entry = editor.getEntries().get(selectedIndex);
        if (changeIndex >= 0 && changeIndex < entry.changes.size()) {
            entry.changes.remove(changeIndex);
            editor.showToast("Change removed");
        }
    }

    /**
     * 开始编辑指定索引的 change 条目
     */
    private void startEditChange(int index) {
        if (selectedIndex < 0 || selectedIndex >= editor.getEntries().size()) return;
        EditableEntry entry = editor.getEntries().get(selectedIndex);
        if (index < 0 || index >= entry.changes.size()) return;

        editingChangeIndex = index;
        if (editChangeBox != null) {
            editChangeBox.setValue(entry.changes.get(index));
            editChangeBox.setCursorPosition(0);
            editChangeBox.setFocused(true);
            editChangeBox.visible = true;
            editChangeBox.active = true;
        }
    }

    /**
     * 确认当前编辑的 change 修改
     */
    private void confirmEditChange() {
        if (editingChangeIndex < 0 || selectedIndex < 0) return;
        if (editChangeBox == null) return;

        EditableEntry entry = editor.getEntries().get(selectedIndex);
        if (editingChangeIndex < entry.changes.size()) {
            String newValue = editChangeBox.getValue().trim();
            if (!newValue.isEmpty()) {
                entry.changes.set(editingChangeIndex, newValue);
            }
        }
        exitEditChange();
    }

    /**
     * 取消当前编辑的 change 修改（恢复原值）
     */
    private void cancelEditChange() {
        exitEditChange();
    }

    /**
     * 退出编辑模式
     */
    private void exitEditChange() {
        editingChangeIndex = -1;
        if (editChangeBox != null) {
            editChangeBox.visible = false;
            editChangeBox.active = false;
            editChangeBox.setFocused(false);
        }
    }

    /**
     * 切换条目的类型
     */
    private void toggleType(EditableEntry entry, String type) {
        if (entry.types.contains(type)) {
            // 至少保留一个类型
            if (entry.types.size() > 1) {
                entry.types.remove(type);
            }
        } else {
            entry.types.add(type);
            entry.sortTypes();
        }
    }

    // ==================== 颜色选择器 ====================

    /**
     * 打开颜色选择器
     */
    private void openColorPicker(EditableEntry entry) {
        colorPicker = new EditorColorPicker(
                entry.color | 0xFF000000,
                newColor -> {
                    // 确定回调
                    entry.color = newColor & 0x00FFFFFF;
                    if (colorHexBox != null) {
                        colorHexBox.setValue(String.format("#%06X", entry.color));
                    }
                    closeColorPicker();
                },
                this::closeColorPicker // 取消回调
        );

        // 计算弹窗位置（居中）
        pickerX = (editor.getScreenWidth() - EditorColorPicker.PICKER_W) / 2;
        pickerY = (editor.getScreenHeight() - EditorColorPicker.PICKER_H) / 2;

        colorPicker.init(editor.getScreenFont(), pickerX, pickerY);

        // 注册颜色选择器的 hex 输入框
        EditBox hexInput = colorPicker.getHexInput();
        if (hexInput != null) {
            editor.addWidgetToScreen(hexInput);
        }

        showColorPicker = true;
    }

    /**
     * 关闭颜色选择器
     */
    private void closeColorPicker() {
        if (colorPicker != null) {
            EditBox hexInput = colorPicker.getHexInput();
            if (hexInput != null) {
                editor.removeWidgetFromScreen(hexInput);
            }
            colorPicker.close();
            colorPicker = null;
        }
        showColorPicker = false;
    }

    // ==================== 辅助方法 ====================

    /**
     * 将详情面板的编辑数据同步回选中的条目
     */
    private void syncDetailToEntry() {
        if (selectedIndex < 0 || selectedIndex >= editor.getEntries().size()) return;
        EditableEntry entry = editor.getEntries().get(selectedIndex);

        if (versionBox != null) {
            entry.version = versionBox.getValue().trim();
        }
        if (dateBox != null) {
            entry.date = dateBox.getValue().trim();
        }
        if (titleBox != null) {
            entry.title = titleBox.getValue().trim();
        }
        if (colorHexBox != null) {
            entry.color = parseHexColor(colorHexBox.getValue());
        }
    }

    /**
     * 从十六进制字符串解析颜色值（仅 RGB，不含 alpha）
     */
    private int parseHexColor(String hex) {
        try {
            String val = hex.trim();
            if (val.startsWith("#")) {
                val = val.substring(1);
            }
            if (val.length() == 6) {
                return (int) Long.parseLong(val, 16);
            }
        } catch (Exception ignored) {
        }
        return 0xFFFFFF; // 默认白色
    }

    /**
     * 从颜色输入框应用颜色到选中条目
     */
    private void applyColorFromHex() {
        if (selectedIndex < 0 || selectedIndex >= editor.getEntries().size()) return;
        if (colorHexBox == null) return;

        EditableEntry entry = editor.getEntries().get(selectedIndex);
        entry.color = parseHexColor(colorHexBox.getValue());
    }

    /**
     * 销毁所有 EditBox 并从屏幕移除
     */
    private void destroyEditBoxes(ChangelogEditorScreen screen) {
        if (versionBox != null) {
            screen.removeWidgetFromScreen(versionBox);
            versionBox = null;
        }
        if (dateBox != null) {
            screen.removeWidgetFromScreen(dateBox);
            dateBox = null;
        }
        if (titleBox != null) {
            screen.removeWidgetFromScreen(titleBox);
            titleBox = null;
        }
        if (colorHexBox != null) {
            screen.removeWidgetFromScreen(colorHexBox);
            colorHexBox = null;
        }
        if (newChangeBox != null) {
            screen.removeWidgetFromScreen(newChangeBox);
            newChangeBox = null;
        }
        if (editChangeBox != null) {
            screen.removeWidgetFromScreen(editChangeBox);
            editChangeBox = null;
        }
    }

    /**
     * 定位 EditBox 控件到指定坐标
     */
    private void positionEditBox(EditBox box, int x, int y) {
        if (box != null) {
            box.setX(x);
            box.setY(y);
        }
    }

    /**
     * 定位 Button 控件到指定坐标
     */
    private void positionButton(Button btn, int x, int y) {
        if (btn != null) {
            btn.setX(x);
            btn.setY(y);
        }
    }

    /**
     * 截断文本以适应指定宽度
     */
    private String truncateText(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        for (int i = text.length() - 1; i > 0; i--) {
            if (font.width(text.substring(0, i)) + ellipsisWidth <= maxWidth) {
                return text.substring(0, i) + ellipsis;
            }
        }
        return ellipsis;
    }

    /**
     * 获取当前选中索引
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * 颜色选择器是否正在显示
     */
    public boolean isColorPickerOpen() {
        return showColorPicker;
    }
}
