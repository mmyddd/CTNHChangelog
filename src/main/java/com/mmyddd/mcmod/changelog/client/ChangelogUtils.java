package com.mmyddd.mcmod.changelog.client;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * 更新日志 UI 相关的工具方法，消除 ChangelogDetailScreen 和 ChangelogList.Entry 中的重复代码。
 */
public final class ChangelogUtils {

    /** 默认标签颜色（灰色） */
    public static final int DEFAULT_TAG_COLOR = 0xFF888888;

    /** 闪烁间隔（毫秒） */
    public static final int BLINK_INTERVAL = 800;

    private ChangelogUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 根据更新类型返回对应的标签颜色。
     *
     * @param type 更新类型（major / minor / patch / hotfix / danger）
     * @return ARGB 颜色值
     */
    public static int getTypeColor(String type) {
        return switch (type) {
            case "major" -> 0xFF5555FF;  // 蓝色
            case "minor" -> 0xFF55FF55;  // 绿色
            case "patch" -> 0xFFFFFF55;  // 黄色
            case "hotfix" -> 0xFFFF5555; // 红色
            case "danger" -> 0xFFFF5555; // 红色
            default -> DEFAULT_TAG_COLOR; // 灰色
        };
    }

    /**
     * 根据更新类型返回翻译后的标签文本。
     *
     * @param type 更新类型（major / minor / patch / hotfix / danger）
     * @return 翻译后的文本，未知类型返回 null
     */
    public static String getTranslatedTypeTag(String type) {
        return switch (type) {
            case "major" -> Component.translatable("ctnhchangelog.type.major").getString();
            case "minor" -> Component.translatable("ctnhchangelog.type.minor").getString();
            case "patch" -> Component.translatable("ctnhchangelog.type.patch").getString();
            case "hotfix" -> Component.translatable("ctnhchangelog.type.hotfix").getString();
            case "danger" -> Component.translatable("ctnhchangelog.type.danger").getString();
            default -> type;
        };
    }

    /**
     * 用于显示的标签，包含文本和颜色。
     */
    public static class DisplayTag {
        public final String text;
        public final int color;

        public DisplayTag(String text, int color) {
            this.text = Objects.requireNonNull(text, "DisplayTag text cannot be null");
            this.color = color;
        }
    }
}
