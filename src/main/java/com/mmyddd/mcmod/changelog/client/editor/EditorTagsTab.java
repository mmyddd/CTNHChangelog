package com.mmyddd.mcmod.changelog.client.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签颜色配置标签页。
 * 布局：
 *   y=35  [添加标签]
 *   y=60  [标签名____] [■色块] [0xFFRRGGBB] [x]
 *   y=90  [标签名____] [■色块] [0xFFRRGGBB] [x]
 *   ...
 */
public class EditorTagsTab {
    private static final int ROW_HEIGHT = 30;
    private static final int MARGIN = 20;

    private final ChangelogEditorScreen editor;
    private int areaTop = 35;
    private int areaBottom;

    // 滚动状态
    private double scrollAmount;
    private int contentHeight;
    private boolean isDragging;

    // 每行的 EditBox
    private List<EditBox> tagNameBoxes;
    private List<EditBox> tagColorBoxes;

    // 颜色选择器状态
    private EditorColorPicker colorPicker;
    private int colorPickerTargetIndex = -1;
    private int colorPickerX, colorPickerY;

    // 新标签输入
    private EditBox newTagBox;

    // 添加标签按钮
    private Button addTagButton;

    public EditorTagsTab(ChangelogEditorScreen editor) {
        this.editor = editor;
    }

    /**
     * 添加所有 widget 到 screen。
     */
    public void addWidgets(ChangelogEditorScreen screen) {
        areaBottom = screen.getScreenHeight() - 10;

        tagNameBoxes = new ArrayList<>();
        tagColorBoxes = new ArrayList<>();

        Map<String, Integer> tagColors = editor.getTagColors();
        List<String> tagNames = new ArrayList<>(tagColors.keySet());
        List<Integer> colorValues = new ArrayList<>(tagColors.values());

        // 为每个标签创建 EditBox
        for (int i = 0; i < tagNames.size(); i++) {
            int y = areaTop + 25 + i * ROW_HEIGHT - (int) scrollAmount;

            // 标签名输入框
            EditBox nameBox = new EditBox(
                    screen.getScreenFont(), MARGIN, y, 180, 20,
                    Component.literal("Tag Name")
            );
            nameBox.setValue(tagNames.get(i));
            nameBox.setBordered(true);
            screen.addWidgetToScreen(nameBox);
            tagNameBoxes.add(nameBox);

            // 颜色值输入框
            EditBox colorBox = new EditBox(
                    screen.getScreenFont(), MARGIN + 210 + 24, y, 100, 20,
                    Component.literal("Tag Color")
            );
            colorBox.setValue(String.format("#%06X", colorValues.get(i) & 0x00FFFFFF));
            colorBox.setBordered(true);
            screen.addWidgetToScreen(colorBox);
            tagColorBoxes.add(colorBox);
        }

        // 新标签输入框
        int newY = areaTop + 25 + tagNames.size() * ROW_HEIGHT - (int) scrollAmount;
        newTagBox = new EditBox(
                screen.getScreenFont(), MARGIN, newY, 180, 20,
                Component.literal("New Tag")
        );
        newTagBox.setHint(Component.literal("New tag name"));
        newTagBox.setBordered(true);
        screen.addWidgetToScreen(newTagBox);

        // 添加标签按钮
        addTagButton = Button.builder(
                Component.literal("Add Tag"),
                button -> addTagColor()
        ).bounds(MARGIN + 200, newY, 80, 20).build();
        screen.addWidgetToScreen(addTagButton);

        // 计算内容高度
        contentHeight = (tagNames.size() + 1) * ROW_HEIGHT + 30;
    }

    /**
     * 移除所有 widget，同步数据。
     */
    public void removeWidgets(ChangelogEditorScreen screen) {
        // 先同步所有 EditBox 的值回数据
        syncToData();

        // 移除所有 widget
        if (tagNameBoxes != null) {
            for (EditBox box : tagNameBoxes) {
                screen.removeWidgetFromScreen(box);
            }
            tagNameBoxes.clear();
        }
        if (tagColorBoxes != null) {
            for (EditBox box : tagColorBoxes) {
                screen.removeWidgetFromScreen(box);
            }
            tagColorBoxes.clear();
        }
        if (newTagBox != null) {
            screen.removeWidgetFromScreen(newTagBox);
            newTagBox = null;
        }
        if (addTagButton != null) {
            screen.removeWidgetFromScreen(addTagButton);
            addTagButton = null;
        }
    }

    /**
     * 渲染标签页内容。
     */
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        int viewTop = areaTop + 20;
        int viewBottom = areaBottom;
        int viewHeight = viewBottom - viewTop;

        // 启用裁剪区域
        graphics.enableScissor(MARGIN - 5, viewTop, editor.getScreenWidth() - MARGIN + 5, viewBottom);

        Map<String, Integer> tagColors = editor.getTagColors();
        List<String> tagNames = new ArrayList<>(tagColors.keySet());
        List<Integer> colorValues = new ArrayList<>(tagColors.values());

        // 渲染每一行
        for (int i = 0; i < tagNames.size(); i++) {
            int y = areaTop + 25 + i * ROW_HEIGHT - (int) scrollAmount;

            // 只渲染可见行
            if (y + ROW_HEIGHT < viewTop || y > viewBottom) {
                continue;
            }

            // 渲染标签名 EditBox（位置已通过 addWidgets 设置）
            // 渲染颜色色块（24x20 像素）
            int colorBlockX = MARGIN + 210;
            int colorBlockY = y;
            int color = colorValues.get(i);
            graphics.fill(colorBlockX, colorBlockY, colorBlockX + 24, colorBlockY + 20, 0xFF000000 | (color & 0x00FFFFFF));
            // 色块边框
            graphics.fill(colorBlockX, colorBlockY, colorBlockX + 24, colorBlockY + 1, 0xFFFFFFFF);
            graphics.fill(colorBlockX, colorBlockY + 19, colorBlockX + 24, colorBlockY + 20, 0xFFFFFFFF);
            graphics.fill(colorBlockX, colorBlockY, colorBlockX + 1, colorBlockY + 20, 0xFFFFFFFF);
            graphics.fill(colorBlockX + 23, colorBlockY, colorBlockX + 24, colorBlockY + 20, 0xFFFFFFFF);

            // 渲染删除按钮 "X"
            int deleteX = MARGIN + 340;
            int deleteY = y;
            boolean deleteHover = mouseX >= deleteX && mouseX < deleteX + 20
                    && mouseY >= deleteY && mouseY < deleteY + 20
                    && mouseY >= viewTop && mouseY <= viewBottom;
            int deleteColor = deleteHover ? 0xFFFF4444 : 0xFFAAAAAA;
            graphics.fill(deleteX, deleteY, deleteX + 20, deleteY + 20, 0x33FF0000);
            graphics.drawCenteredString(font, "X", deleteX + 10, deleteY + 6, deleteColor);
        }

        // 关闭裁剪区域
        graphics.disableScissor();

        // 渲染滚动条
        int barLeft = editor.getScreenWidth() - MARGIN - 6;
        int barRight = editor.getScreenWidth() - MARGIN;
        renderScrollbar(graphics, barLeft, viewTop, barRight, viewBottom);

        // 如果颜色选择器打开，渲染弹窗覆盖层
        if (colorPicker != null) {
            colorPicker.render(graphics, font, colorPickerX, colorPickerY, mouseX, mouseY);
        }
    }

    /**
     * 渲染滚动条。
     */
    private void renderScrollbar(GuiGraphics graphics, int barLeft, int barTop, int barRight, int barBottom) {
        int viewHeight = barBottom - barTop;
        if (contentHeight > viewHeight) {
            int barHeight = (int) ((float) viewHeight * viewHeight / contentHeight);
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            int barY = (int) (scrollAmount * (viewHeight - barHeight) / maxScroll);
            barY = Mth.clamp(barY, 0, viewHeight - barHeight);
            graphics.fill(barLeft, barTop, barRight, barBottom, 0x33AAAAAA);
            graphics.fill(barLeft, barTop + barY, barRight, barTop + barY + barHeight, 0xFFAAAAAA);
        }
    }

    /**
     * 处理鼠标点击事件。
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int viewTop = areaTop + 20;
        int viewBottom = areaBottom;

        // 如果颜色选择器打开，优先委托给它
        if (colorPicker != null) {
            if (colorPicker.mouseClicked(mouseX, mouseY, button, colorPickerX, colorPickerY)) {
                return true;
            }
            // 点击选择器外部关闭
            if (mouseX < colorPickerX || mouseX > colorPickerX + EditorColorPicker.PICKER_W
                    || mouseY < colorPickerY || mouseY > colorPickerY + EditorColorPicker.PICKER_H) {
                closeColorPicker();
                return true;
            }
            return true;
        }

        // 检测色块点击
        Map<String, Integer> tagColors = editor.getTagColors();
        List<String> tagNames = new ArrayList<>(tagColors.keySet());
        for (int i = 0; i < tagNames.size(); i++) {
            int y = areaTop + 25 + i * ROW_HEIGHT - (int) scrollAmount;
            if (y + ROW_HEIGHT < viewTop || y > viewBottom) continue;

            int colorBlockX = MARGIN + 210;
            if (mouseX >= colorBlockX && mouseX < colorBlockX + 24
                    && mouseY >= y && mouseY < y + 20) {
                openColorPicker(i, colorBlockX, y);
                return true;
            }
        }

        // 检测删除按钮点击
        for (int i = 0; i < tagNames.size(); i++) {
            int y = areaTop + 25 + i * ROW_HEIGHT - (int) scrollAmount;
            if (y + ROW_HEIGHT < viewTop || y > viewBottom) continue;

            int deleteX = MARGIN + 340;
            if (mouseX >= deleteX && mouseX < deleteX + 20
                    && mouseY >= y && mouseY < y + 20) {
                removeTagColor(i);
                return true;
            }
        }

        // 检测滚动条点击
        int barLeft = editor.getScreenWidth() - MARGIN - 6;
        int barRight = editor.getScreenWidth() - MARGIN;
        if (contentHeight > (viewBottom - viewTop)) {
            if (mouseX >= barLeft && mouseX <= barRight && mouseY >= viewTop && mouseY <= viewBottom) {
                isDragging = true;
                return true;
            }
        }

        return false;
    }

    /**
     * 处理鼠标滚动事件。
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int viewHeight = areaBottom - (areaTop + 20);
        if (contentHeight > viewHeight) {
            double maxScroll = Math.max(0, contentHeight - viewHeight);
            scrollAmount = Mth.clamp(scrollAmount - delta * 12, 0, maxScroll);
            updateWidgetPositions();
            return true;
        }
        return false;
    }

    /**
     * 处理鼠标拖拽事件。
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 颜色选择器拖拽
        if (colorPicker != null) {
            if (colorPicker.mouseDragged(mouseX, mouseY, button, colorPickerX, colorPickerY)) {
                return true;
            }
        }

        // 滚动条拖拽
        if (isDragging) {
            int viewTop = areaTop + 20;
            int viewHeight = areaBottom - viewTop;
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            if (maxScroll > 0) {
                int scrollBarHeight = (int) ((float) viewHeight * viewHeight / contentHeight);
                double scrollRatio = (mouseY - viewTop) / (double) (viewHeight - scrollBarHeight);
                scrollAmount = Mth.clamp(scrollRatio * maxScroll, 0, maxScroll);
                updateWidgetPositions();
            }
            return true;
        }
        return false;
    }

    /**
     * 处理鼠标释放事件。
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        if (colorPicker != null) {
            colorPicker.mouseReleased();
        }
        return false;
    }

    /**
     * 处理字符输入事件。
     */
    public boolean charTyped(char codePoint, int modifiers) {
        // EditBox 通过 screen 自动处理
        return false;
    }

    /**
     * 处理按键事件。
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 如果颜色选择器打开，处理 hex 输入
        if (colorPicker != null && colorPicker.getHexInput() != null) {
            if (colorPicker.getHexInput().keyPressed(keyCode, scanCode, modifiers)) {
                colorPicker.applyHexInput();
                return true;
            }
        }
        return false;
    }

    /**
     * 打开颜色选择器。
     */
    private void openColorPicker(int index, int x, int y) {
        Map<String, Integer> tagColors = editor.getTagColors();
        List<Integer> colorValues = new ArrayList<>(tagColors.values());

        int currentColor = (index >= 0 && index < colorValues.size())
                ? colorValues.get(index) : 0xFF888888;

        colorPickerTargetIndex = index;

        // 计算弹窗位置，确保不超出屏幕
        colorPickerX = Math.min(x, editor.getScreenWidth() - EditorColorPicker.PICKER_W - 10);
        colorPickerY = Math.min(y, editor.getScreenHeight() - EditorColorPicker.PICKER_H - 10);
        if (colorPickerX < 10) colorPickerX = 10;
        if (colorPickerY < 10) colorPickerY = 10;

        colorPicker = new EditorColorPicker(
                currentColor,
                // 确认回调
                newColor -> {
                    if (colorPickerTargetIndex >= 0) {
                        List<String> names = new ArrayList<>(editor.getTagColors().keySet());
                        if (colorPickerTargetIndex < names.size()) {
                            editor.setTagColor(names.get(colorPickerTargetIndex), newColor);
                            // 同步颜色 EditBox
                            if (colorPickerTargetIndex < tagColorBoxes.size()) {
                                tagColorBoxes.get(colorPickerTargetIndex)
                                        .setValue(String.format("#%06X", newColor & 0x00FFFFFF));
                            }
                        }
                    }
                    closeColorPicker();
                },
                // 取消回调
                this::closeColorPicker
        );

        colorPicker.init(editor.getScreenFont(), colorPickerX, colorPickerY);

        // 注册 Hex 输入框到 Screen 以接收键盘输入
        EditBox hexInput = colorPicker.getHexInput();
        if (hexInput != null) {
            editor.addWidgetToScreen(hexInput);
        }
    }

    /**
     * 关闭颜色选择器。
     */
    private void closeColorPicker() {
        if (colorPicker != null) {
            // 从 Screen 移除 Hex 输入框
            EditBox hexInput = colorPicker.getHexInput();
            if (hexInput != null) {
                editor.removeWidgetFromScreen(hexInput);
            }
            colorPicker.close();
            colorPicker = null;
            colorPickerTargetIndex = -1;
        }
    }

    /**
     * 添加新标签颜色。
     */
    private void addTagColor() {
        if (newTagBox != null && !newTagBox.getValue().trim().isEmpty()) {
            String tagName = newTagBox.getValue().trim();
            // 检查是否已存在
            if (editor.getTagColors().containsKey(tagName)) {
                editor.showToast("Tag already exists: " + tagName);
                return;
            }
            editor.addTagColor(tagName, 0xFF888888);
            newTagBox.setValue("");
            rebuildWidgets();
        } else {
            editor.showToast("Please enter a tag name");
        }
    }

    /**
     * 删除指定索引的标签颜色。
     */
    private void removeTagColor(int index) {
        Map<String, Integer> tagColors = editor.getTagColors();
        List<String> tagNames = new ArrayList<>(tagColors.keySet());
        if (index >= 0 && index < tagNames.size()) {
            editor.removeTagColor(tagNames.get(index));
            rebuildWidgets();
        }
    }

    /**
     * 同步所有 EditBox 值回数据。
     */
    private void syncToData() {
        if (tagNameBoxes == null || tagColorBoxes == null) return;

        Map<String, Integer> tagColors = editor.getTagColors();
        List<String> oldNames = new ArrayList<>(tagColors.keySet());
        List<Integer> oldColors = new ArrayList<>(tagColors.values());

        // 使用临时 LinkedHashMap 重建数据
        LinkedHashMap<String, Integer> newData = new LinkedHashMap<>();

        for (int i = 0; i < tagNameBoxes.size(); i++) {
            String newName = tagNameBoxes.get(i).getValue().trim();
            String colorStr = tagColorBoxes.get(i).getValue().trim();

            // 解析颜色值
            int color = parseColor(colorStr);
            if (color < 0) {
                // 解析失败，保留原值
                color = (i < oldColors.size()) ? oldColors.get(i) : 0xFF888888;
            }

            if (!newName.isEmpty()) {
                newData.put(newName, color);
            }
        }

        // 清除旧数据并写入新数据
        for (String oldName : oldNames) {
            editor.removeTagColor(oldName);
        }
        for (Map.Entry<String, Integer> entry : newData.entrySet()) {
            editor.addTagColor(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 解析颜色字符串。
     * 支持格式：#RRGGBB 或 0xFFRRGGBB
     * @return 解析后的 ARGB int，失败返回 -1
     */
    private int parseColor(String str) {
        try {
            if (str.startsWith("#")) {
                // #RRGGBB 格式
                String hex = str.substring(1);
                if (hex.length() == 6) {
                    return 0xFF000000 | (int) Long.parseLong(hex, 16);
                }
            } else if (str.startsWith("0x") || str.startsWith("0X")) {
                // 0xFFRRGGBB 格式
                return (int) Long.parseLong(str.substring(2), 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    /**
     * 重建所有 widget（销毁旧的并重新创建）。
     */
    private void rebuildWidgets() {
        // 同步当前数据
        syncToData();

        // 移除旧 widgets
        removeWidgets(editor);

        // 重新创建 widgets
        addWidgets(editor);
    }

    /**
     * 更新所有 widget 的位置（用于滚动）。
     */
    private void updateWidgetPositions() {
        if (tagNameBoxes == null) return;

        int viewTop = areaTop + 20;
        Map<String, Integer> tagColors = editor.getTagColors();
        List<String> tagNames = new ArrayList<>(tagColors.keySet());

        for (int i = 0; i < tagNameBoxes.size(); i++) {
            int y = areaTop + 25 + i * ROW_HEIGHT - (int) scrollAmount;
            tagNameBoxes.get(i).setX(MARGIN);
            tagNameBoxes.get(i).setY(y);
            tagColorBoxes.get(i).setX(MARGIN + 210 + 24);
            tagColorBoxes.get(i).setY(y);
        }

        // 更新新标签输入框位置
        if (newTagBox != null) {
            int newY = areaTop + 25 + tagNames.size() * ROW_HEIGHT - (int) scrollAmount;
            newTagBox.setX(MARGIN);
            newTagBox.setY(newY);
        }

        // 更新添加按钮位置
        if (addTagButton != null) {
            int newY = areaTop + 25 + tagNames.size() * ROW_HEIGHT - (int) scrollAmount;
            addTagButton.setX(MARGIN + 200);
            addTagButton.setY(newY);
        }
    }
}
