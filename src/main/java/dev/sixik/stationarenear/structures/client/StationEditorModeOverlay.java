package dev.sixik.stationarenear.structures.client;

import dev.sixik.stationarenear.structures.editor.StationEditorWandMode;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;

public final class StationEditorModeOverlay {

    private static final long DISPLAY_MILLIS = 1_700L;
    private static final int PANEL_COLOR = 0xB0101520;
    private static final int PANEL_BORDER = 0xC04584FF;
    private static final int SLOT_COLOR = 0xC0182234;
    private static final int SLOT_BORDER = 0x805A6B82;
    private static final int SELECTED_COLOR = 0xE0254F78;
    private static final int SELECTED_BORDER = 0xFF69D7FF;
    private static final int MUTED_ICON = 0xFF7A8799;
    private static final int ZONE_ICON = 0xFF78D6FF;
    private static final int TRIGGER_ICON = 0xFFFFC857;
    private static final int EDIT_ICON = 0xFFFF7A7A;
    private static final int CONNECTION_ICON = 0xFF84F08E;
    private static final int COPY_ICON = 0xFFD38BFF;

    private static StationEditorWandMode visibleMode = StationEditorWandMode.ZONE_SELECTION;
    private static long visibleUntilMillis;

    private StationEditorModeOverlay() {
    }

    public static void show(StationEditorWandMode mode) {
        visibleMode = mode == null ? StationEditorWandMode.ZONE_SELECTION : mode;
        visibleUntilMillis = System.currentTimeMillis() + DISPLAY_MILLIS;
    }

    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!StationStructureEditorStick.isEditorTool(minecraft.player.getMainHandItem())) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now > visibleUntilMillis) {
            return;
        }

        float alpha = Mth.clamp((visibleUntilMillis - now) / 400.0F, 0.0F, 1.0F);
        render(event.getGuiGraphics(), minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), alpha);
    }

    private static void render(GuiGraphics graphics, int screenWidth, int screenHeight, float alpha) {
        StationEditorWandMode[] modes = StationEditorWandMode.values();
        int slot = 34;
        int gap = 7;
        int width = modes.length * slot + (modes.length - 1) * gap + 18;
        int height = 64;
        int x = (screenWidth - width) / 2;
        int y = Math.max(18, screenHeight / 5);
        int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);

        fill(graphics, x, y, x + width, y + height, applyAlpha(PANEL_COLOR, alphaByte));
        outline(graphics, x, y, width, height, applyAlpha(PANEL_BORDER, alphaByte));

        int cursorX = x + 9;
        int cursorY = y + 8;
        for (StationEditorWandMode mode : modes) {
            boolean selected = mode == visibleMode;
            int fill = selected ? SELECTED_COLOR : SLOT_COLOR;
            int border = selected ? SELECTED_BORDER : SLOT_BORDER;
            fill(graphics, cursorX, cursorY, cursorX + slot, cursorY + slot, applyAlpha(fill, alphaByte));
            outline(graphics, cursorX, cursorY, slot, slot, applyAlpha(border, alphaByte));
            renderIcon(graphics, mode, cursorX + slot / 2, cursorY + slot / 2, selected, alphaByte);
            if (selected) {
                fill(graphics, cursorX + 8, cursorY + slot + 4, cursorX + slot - 8, cursorY + slot + 7, applyAlpha(SELECTED_BORDER, alphaByte));
            }
            cursorX += slot + gap;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String title = russianTitle(visibleMode);
        int textColor = applyAlpha(0xFFEAF8FF, alphaByte);
        int textX = x + (width - minecraft.font.width(title)) / 2;
        graphics.drawString(minecraft.font, title, textX, y + 49, textColor, false);
    }

    private static void renderIcon(GuiGraphics graphics, StationEditorWandMode mode, int centerX, int centerY, boolean selected, int alpha) {
        int muted = applyAlpha(MUTED_ICON, alpha);
        switch (mode) {
            case ZONE_SELECTION -> renderZoneIcon(graphics, centerX, centerY, applyAlpha(ZONE_ICON, alpha), muted);
            case TRIGGER_MANAGER_CREATE -> renderTriggerCreateIcon(graphics, centerX, centerY, applyAlpha(TRIGGER_ICON, alpha), muted);
            case TRIGGER_MANAGER_EDIT -> renderTriggerEditIcon(graphics, centerX, centerY, applyAlpha(EDIT_ICON, alpha), muted);
            case CONNECTION_MANAGER -> renderConnectionIcon(graphics, centerX, centerY, applyAlpha(CONNECTION_ICON, alpha), muted);
            case STRUCTURE_COPY -> renderCopyIcon(graphics, centerX, centerY, applyAlpha(COPY_ICON, alpha), muted);
        }
    }

    private static void renderZoneIcon(GuiGraphics graphics, int centerX, int centerY, int color, int muted) {
        int left = centerX - 10;
        int top = centerY - 10;
        int right = centerX + 10;
        int bottom = centerY + 10;
        fill(graphics, left, top, left + 8, top + 3, color);
        fill(graphics, left, top, left + 3, top + 8, color);
        fill(graphics, right - 8, top, right, top + 3, color);
        fill(graphics, right - 3, top, right, top + 8, color);
        fill(graphics, left, bottom - 3, left + 8, bottom, color);
        fill(graphics, left, bottom - 8, left + 3, bottom, color);
        fill(graphics, right - 8, bottom - 3, right, bottom, color);
        fill(graphics, right - 3, bottom - 8, right, bottom, color);
        fill(graphics, centerX - 2, centerY - 2, centerX + 2, centerY + 2, muted);
    }

    private static void renderTriggerCreateIcon(GuiGraphics graphics, int centerX, int centerY, int color, int muted) {
        fill(graphics, centerX - 3, centerY - 12, centerX + 3, centerY - 7, color);
        fill(graphics, centerX - 8, centerY - 7, centerX + 8, centerY - 2, color);
        fill(graphics, centerX - 12, centerY - 2, centerX + 12, centerY + 3, color);
        fill(graphics, centerX - 8, centerY + 3, centerX + 8, centerY + 8, color);
        fill(graphics, centerX - 3, centerY + 8, centerX + 3, centerY + 13, color);
        fill(graphics, centerX - 1, centerY - 7, centerX + 1, centerY + 8, muted);
        fill(graphics, centerX - 7, centerY - 1, centerX + 8, centerY + 1, muted);
    }

    private static void renderTriggerEditIcon(GuiGraphics graphics, int centerX, int centerY, int color, int muted) {
        fill(graphics, centerX - 9, centerY - 8, centerX + 3, centerY + 4, muted);
        fill(graphics, centerX - 7, centerY - 6, centerX + 1, centerY + 2, color);
        fill(graphics, centerX + 4, centerY - 10, centerX + 8, centerY - 6, color);
        fill(graphics, centerX + 1, centerY - 6, centerX + 5, centerY - 2, color);
        fill(graphics, centerX - 2, centerY - 2, centerX + 2, centerY + 2, color);
        fill(graphics, centerX - 5, centerY + 1, centerX - 1, centerY + 5, color);
        fill(graphics, centerX - 8, centerY + 7, centerX + 8, centerY + 10, muted);
    }

    private static void renderConnectionIcon(GuiGraphics graphics, int centerX, int centerY, int color, int muted) {
        fill(graphics, centerX - 12, centerY - 2, centerX + 13, centerY + 2, color);
        fill(graphics, centerX - 2, centerY - 10, centerX + 2, centerY + 11, muted);
        fill(graphics, centerX - 14, centerY - 7, centerX - 5, centerY + 8, color);
        fill(graphics, centerX + 5, centerY - 7, centerX + 14, centerY + 8, color);
        fill(graphics, centerX - 11, centerY - 4, centerX - 8, centerY + 5, muted);
        fill(graphics, centerX + 8, centerY - 4, centerX + 11, centerY + 5, muted);
    }


    private static String russianTitle(StationEditorWandMode mode) {
        return switch (mode) {
            case ZONE_SELECTION -> "\u0412\u044b\u0431\u043e\u0440 \u0437\u043e\u043d\u044b \u0441\u0442\u0440\u0443\u043a\u0442\u0443\u0440\u044b";
            case TRIGGER_MANAGER_CREATE -> "\u0421\u043e\u0437\u0434\u0430\u043d\u0438\u0435 \u0442\u0440\u0438\u0433\u0433\u0435\u0440\u043e\u0432";
            case TRIGGER_MANAGER_EDIT -> "\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u0442\u0440\u0438\u0433\u0433\u0435\u0440\u043e\u0432";
            case CONNECTION_MANAGER -> "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0441\u043e\u0435\u0434\u0438\u043d\u0435\u043d\u0438\u0439";
            case STRUCTURE_COPY -> "\u041a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u0441\u0442\u0440\u0443\u043a\u0442\u0443\u0440\u044b";
        };
    }


    private static void renderCopyIcon(GuiGraphics graphics, int centerX, int centerY, int color, int muted) {
        fill(graphics, centerX - 9, centerY - 7, centerX + 5, centerY + 7, muted);
        outline(graphics, centerX - 9, centerY - 7, 14, 14, color);
        fill(graphics, centerX - 4, centerY - 12, centerX + 10, centerY + 2, 0x00000000);
        outline(graphics, centerX - 4, centerY - 12, 14, 14, color);
        fill(graphics, centerX + 1, centerY - 5, centerX + 8, centerY - 3, color);
        fill(graphics, centerX + 4, centerY - 8, centerX + 6, centerY - 1, color);
        fill(graphics, centerX - 6, centerY + 9, centerX + 8, centerY + 12, muted);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        fill(graphics, x, y, x + width, y + 1, color);
        fill(graphics, x, y + height - 1, x + width, y + height, color);
        fill(graphics, x, y, x + 1, y + height, color);
        fill(graphics, x + width - 1, y, x + width, y + height, color);
    }

    private static void fill(GuiGraphics graphics, int minX, int minY, int maxX, int maxY, int color) {
        graphics.fill(minX, minY, maxX, maxY, color);
    }

    private static int applyAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }
}
