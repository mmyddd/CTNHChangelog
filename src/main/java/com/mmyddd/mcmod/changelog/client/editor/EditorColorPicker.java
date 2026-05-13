package com.mmyddd.mcmod.changelog.client.editor;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.util.function.Consumer;

public class EditorColorPicker {
    public static final int SQ_SIZE = 120;
    public static final int HUE_BAR_W = 16;
    public static final int HUE_BAR_H = SQ_SIZE;
    public static final int PICKER_W = SQ_SIZE + HUE_BAR_W + 80;
    public static final int PICKER_H = SQ_SIZE + 50;

    private float hue;
    private float saturation;
    private float brightness;
    private boolean draggingSquare;
    private boolean draggingHue;

    private DynamicTexture squareTexture;
    private ResourceLocation squareTextureLocation;
    private float lastRenderedHue = -1f;

    private EditBox hexInput;
    private final Consumer<Integer> onConfirm;
    private final Runnable onCancel;

    public EditorColorPicker(int initialColor, Consumer<Integer> onConfirm, Runnable onCancel) {
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        setFromARGB(initialColor);
    }

    private void setFromARGB(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        float[] hsb = Color.RGBtoHSB((int) (r * 255), (int) (g * 255), (int) (b * 255), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    public int toARGB() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        return 0xFF000000 | rgb;
    }

    public EditBox getHexInput() {
        return hexInput;
    }

    public void init(Font font, int x, int y) {
        int hexX = x + SQ_SIZE + HUE_BAR_W + 8;
        int hexY = y + SQ_SIZE + 8;
        hexInput = new EditBox(font, hexX, y + SQ_SIZE + 8, 64, 16,
                net.minecraft.network.chat.Component.literal("Hex"));
        hexInput.setValue(String.format("#%06X", toARGB() & 0x00FFFFFF));
        hexInput.setMaxLength(9);
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        // 半透明背景遮罩
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x80000000);

        // 选择器面板背景
        int panelRight = x + PICKER_W;
        int panelBottom = y + PICKER_H;
        graphics.fill(x - 4, y - 4, panelRight + 4, panelBottom + 4, 0xFF2D3349);
        graphics.fill(x - 2, y - 2, panelRight + 2, panelBottom + 2, 0xFF404760);
        graphics.fill(x, y, panelRight, panelBottom, 0xFF1A1E2B);

        // 饱和度-亮度方块
        renderSquare(graphics, x, y);

        // 色相条
        renderHueBar(graphics, x + SQ_SIZE + 4, y);

        // 预览色块
        int previewX = x + SQ_SIZE + HUE_BAR_W + 8;
        graphics.fill(previewX, y, previewX + 50, y + 30, toARGB());
        graphics.fill(previewX, y, previewX + 50, y + 1, 0xFFFFFFFF);
        graphics.fill(previewX, y + 29, previewX + 50, y + 30, 0xFFFFFFFF);
        graphics.fill(previewX, y, previewX + 1, y + 30, 0xFFFFFFFF);
        graphics.fill(previewX + 49, y, previewX + 50, y + 30, 0xFFFFFFFF);

        // Hex 输入框
        if (hexInput != null) {
            hexInput.render(graphics, mouseX, mouseY, 0);
        }

        // 确定/取消按钮
        int btnY = y + SQ_SIZE + 30;
        int btnW = 40;
        int confirmX = x + SQ_SIZE + HUE_BAR_W + 8;
        int cancelX = confirmX + btnW + 4;

        boolean confirmHover = mouseX >= confirmX && mouseX < confirmX + btnW && mouseY >= btnY && mouseY < btnY + 16;
        boolean cancelHover = mouseX >= cancelX && mouseX < cancelX + btnW && mouseY >= btnY && mouseY < btnY + 16;

        graphics.fill(confirmX, btnY, confirmX + btnW, btnY + 16, confirmHover ? 0xFFFFBB22 : 0xFFFFAA00);
        graphics.drawString(font, "OK", confirmX + 14, btnY + 4, 0xFF1A1E2B);

        graphics.fill(cancelX, btnY, cancelX + btnW, btnY + 16, cancelHover ? 0xFF404760 : 0xFF2D3349);
        graphics.drawString(font, "Cancel", cancelX + 6, btnY + 4, 0xFFAAAAAA);
    }

    private void renderSquare(GuiGraphics graphics, int x, int y) {
        if (squareTexture == null || lastRenderedHue != hue) {
            rebuildSquareTexture();
            lastRenderedHue = hue;
        }
        graphics.blit(squareTextureLocation, x, y, 0, 0, SQ_SIZE, SQ_SIZE, SQ_SIZE, SQ_SIZE);

        // 十字准星指示器
        int sx = x + (int) (saturation * (SQ_SIZE - 1));
        int sy = y + (int) ((1.0f - brightness) * (SQ_SIZE - 1));
        graphics.fill(sx - 3, sy, sx + 4, sy + 1, 0xFFFFFFFF);
        graphics.fill(sx, sy - 3, sx + 1, sy + 4, 0xFFFFFFFF);
    }

    private void renderHueBar(GuiGraphics graphics, int x, int y) {
        for (int i = 0; i < HUE_BAR_H; i++) {
            float h = (float) i / HUE_BAR_H;
            int rgb = Color.HSBtoRGB(h, 1.0f, 1.0f);
            graphics.fill(x, y + i, x + HUE_BAR_W, y + i + 1, 0xFF000000 | rgb);
        }
        // 色相指示器
        int indicatorY = y + (int) (hue * (HUE_BAR_H - 1));
        graphics.fill(x - 1, indicatorY - 1, x + HUE_BAR_W + 1, indicatorY + 2, 0xFFFFFFFF);
    }

    private void rebuildSquareTexture() {
        if (squareTexture != null) {
            squareTexture.close();
        }
        NativeImage image = new NativeImage(SQ_SIZE, SQ_SIZE, false);
        for (int py = 0; py < SQ_SIZE; py++) {
            float b = 1.0f - (float) py / (SQ_SIZE - 1);
            for (int px = 0; px < SQ_SIZE; px++) {
                float s = (float) px / (SQ_SIZE - 1);
                int rgb = Color.HSBtoRGB(hue, s, b);
                image.setPixelRGBA(px, py, 0xFF000000 | rgb);
            }
        }
        squareTexture = new DynamicTexture(image);
        squareTextureLocation = Minecraft.getInstance().getTextureManager()
                .register("ctnh_color_picker_sq", squareTexture);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int pickerX, int pickerY) {
        int sqX = pickerX;
        int sqY = pickerY;
        int hueX = pickerX + SQ_SIZE + 4;
        int hueY = pickerY;

        // 点击饱和度-亮度方块
        if (mouseX >= sqX && mouseX < sqX + SQ_SIZE && mouseY >= sqY && mouseY < sqY + SQ_SIZE) {
            saturation = (float) ((mouseX - sqX) / (SQ_SIZE - 1));
            brightness = 1.0f - (float) ((mouseY - sqY) / (SQ_SIZE - 1));
            saturation = Math.max(0, Math.min(1, saturation));
            brightness = Math.max(0, Math.min(1, brightness));
            draggingSquare = true;
            updateHexInput();
            return true;
        }

        // 点击色相条
        if (mouseX >= hueX && mouseX < hueX + HUE_BAR_W && mouseY >= hueY && mouseY < hueY + HUE_BAR_H) {
            hue = (float) ((mouseY - hueY) / (HUE_BAR_H - 1));
            hue = Math.max(0, Math.min(1, hue));
            draggingHue = true;
            updateHexInput();
            return true;
        }

        // 点击确定按钮
        int btnY = pickerY + SQ_SIZE + 30;
        int btnW = 40;
        int confirmX = pickerX + SQ_SIZE + HUE_BAR_W + 8;
        int cancelX = confirmX + btnW + 4;

        if (mouseX >= confirmX && mouseX < confirmX + btnW && mouseY >= btnY && mouseY < btnY + 16) {
            onConfirm.accept(toARGB());
            return true;
        }

        // 点击取消按钮
        if (mouseX >= cancelX && mouseX < cancelX + btnW && mouseY >= btnY && mouseY < btnY + 16) {
            onCancel.run();
            return true;
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, int pickerX, int pickerY) {
        int sqX = pickerX;
        int sqY = pickerY;
        int hueX = pickerX + SQ_SIZE + 4;
        int hueY = pickerY;

        if (draggingSquare) {
            saturation = (float) ((mouseX - sqX) / (SQ_SIZE - 1));
            brightness = 1.0f - (float) ((mouseY - sqY) / (SQ_SIZE - 1));
            saturation = Math.max(0, Math.min(1, saturation));
            brightness = Math.max(0, Math.min(1, brightness));
            updateHexInput();
            return true;
        }

        if (draggingHue) {
            hue = (float) ((mouseY - hueY) / (HUE_BAR_H - 1));
            hue = Math.max(0, Math.min(1, hue));
            updateHexInput();
            return true;
        }

        return false;
    }

    public void mouseReleased() {
        draggingSquare = false;
        draggingHue = false;
    }

    private void updateHexInput() {
        if (hexInput != null) {
            hexInput.setValue(String.format("#%06X", toARGB() & 0x00FFFFFF));
        }
    }

    public void applyHexInput() {
        if (hexInput == null) return;
        try {
            String val = hexInput.getValue().trim();
            if (val.startsWith("#")) {
                val = val.substring(1);
            }
            if (val.length() == 6) {
                int rgb = (int) Long.parseLong(val, 16);
                setFromARGB(0xFF000000 | rgb);
            }
        } catch (Exception ignored) {
        }
    }

    public void close() {
        if (squareTexture != null) {
            squareTexture.close();
            squareTexture = null;
        }
    }
}
