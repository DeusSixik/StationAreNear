package dev.sixik.stationarenear.quest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;

public final class QuestFurniturePickupOverlay {

    private static final long DISPLAY_GRACE_MILLIS = 250L;
    private static final int PANEL_COLOR = 0xB010151C;
    private static final int PANEL_BORDER = 0xD07EDBFF;
    private static final int BAR_BACKGROUND = 0xD0182230;
    private static final int BAR_FILL = 0xFF77DFFF;
    private static final int BAR_EDGE = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFEAF8FF;

    private static float progress;
    private static String customTitle = "";
    private static long visibleUntilMillis;

    private QuestFurniturePickupOverlay() {
    }

    public static void sync(float syncedProgress, boolean visible) {
        sync(syncedProgress, visible, "");
    }

    public static void sync(float syncedProgress, boolean visible, String title) {
        if (!visible) {
            hide();
            return;
        }
        progress = Mth.clamp(syncedProgress, 0.0F, 1.0F);
        customTitle = title == null ? "" : title;
        visibleUntilMillis = System.currentTimeMillis() + DISPLAY_GRACE_MILLIS;
    }

    public static void hide() {
        progress = 0.0F;
        customTitle = "";
        visibleUntilMillis = 0L;
    }

    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || System.currentTimeMillis() > visibleUntilMillis) {
            return;
        }
        render(event.getGuiGraphics(), minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }

    private static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = 140;
        int height = 24;
        int x = (screenWidth - width) / 2;
        int y = screenHeight - 72;
        String title = customTitle.isBlank()
                ? ("\u041f\u043e\u0434\u0431\u043e\u0440 " + Math.round(progress * 100.0F) + "%")
                : customTitle;

        graphics.fill(x, y, x + width, y + height, PANEL_COLOR);
        outline(graphics, x, y, width, height, PANEL_BORDER);

        int textX = x + (width - minecraft.font.width(title)) / 2;
        graphics.drawString(minecraft.font, title, textX, y + 4, TEXT_COLOR, false);

        int barX = x + 8;
        int barY = y + 15;
        int barWidth = width - 16;
        int barHeight = 5;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, BAR_BACKGROUND);
        int filled = Mth.clamp(Math.round(barWidth * progress), 0, barWidth);
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + barHeight, BAR_FILL);
            graphics.fill(barX + filled - 1, barY, barX + filled, barY + barHeight, BAR_EDGE);
        }
        outline(graphics, barX, barY, barWidth, barHeight, 0xA0FFFFFF);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
