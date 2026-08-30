package dev.sixik.stationarenear.navigation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;

public final class StationDockingOverlay {

    private static final int PANEL_COLOR = 0xD808111A;
    private static final int PANEL_BORDER = 0xE02888F0;
    private static final int SCAN_BG = 0x80102030;
    private static final int SCAN_ACCENT = 0xFF55FFFF;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int SUBTITLE_COLOR = 0xFF7EDBFF;

    private static String currentStationName = "";
    private static String currentStationCode = "";
    private static long startMillis;
    private static long visibleUntilMillis;

    private StationDockingOverlay() {
    }

    public static void show(String stationName, String stationCode, long durationMillis) {
        currentStationName = stationName == null ? "" : stationName;
        currentStationCode = stationCode == null ? "" : stationCode;
        long now = System.currentTimeMillis();
        if (now > visibleUntilMillis) {
            startMillis = now;
        }
        visibleUntilMillis = Math.max(visibleUntilMillis, now + Math.max(1000L, durationMillis));
    }

    public static void hide() {
        currentStationName = "";
        currentStationCode = "";
        visibleUntilMillis = 0L;
        startMillis = 0L;
    }

    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > visibleUntilMillis) {
            return;
        }

        render(event.getGuiGraphics(), minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), now);
    }

    private static void render(GuiGraphics graphics, int screenWidth, int screenHeight, long now) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = 230;
        int height = 38;
        int x = (screenWidth - width) / 2;
        int y = 24;

        float alpha = 1.0F;
        long elapsed = now - startMillis;
        long remaining = visibleUntilMillis - now;
        if (elapsed < 250L) {
            alpha = Mth.clamp(elapsed / 250.0F, 0.0F, 1.0F);
        } else if (remaining < 450L) {
            alpha = Mth.clamp(remaining / 450.0F, 0.0F, 1.0F);
        }

        int alphaBits = (int) (alpha * 255.0F) << 24;
        if ((alphaBits & 0xFF000000) == 0) {
            return;
        }

        int panelBg = (PANEL_COLOR & 0x00FFFFFF) | (((PANEL_COLOR >>> 24) * (int) (alpha * 255.0F) / 255) << 24);
        int panelBorder = (PANEL_BORDER & 0x00FFFFFF) | (((PANEL_BORDER >>> 24) * (int) (alpha * 255.0F) / 255) << 24);
        int titleColor = (TITLE_COLOR & 0x00FFFFFF) | alphaBits;
        int subtitleColor = (SUBTITLE_COLOR & 0x00FFFFFF) | alphaBits;

        graphics.fill(x, y, x + width, y + height, panelBg);
        outline(graphics, x, y, width, height, panelBorder);
        graphics.fill(x + 2, y + 2, x + 5, y + 5, panelBorder);
        graphics.fill(x + width - 5, y + 2, x + width - 2, y + 5, panelBorder);
        graphics.fill(x + 2, y + height - 5, x + 5, y + height - 2, panelBorder);
        graphics.fill(x + width - 5, y + height - 5, x + width - 2, y + height - 2, panelBorder);

        String title = I18n.get("overlay.stationarenear.docking_in_progress");
        int titleWidth = minecraft.font.width(title);
        int iconPulse = (int) ((now / 250L) % 4);
        String icon = switch (iconPulse) {
            case 0 -> ">  <";
            case 1 -> ">> <<";
            case 2 -> " > < ";
            default -> "<  >";
        };
        String header = "[ " + icon + " ]  " + title;
        int headerX = x + (width - minecraft.font.width(header)) / 2;
        graphics.drawString(minecraft.font, header, headerX, y + 6, titleColor, false);

        String displayName = currentStationName.isBlank() ? currentStationCode : currentStationName;
        String subtitle = displayName.isBlank()
                ? ""
                : I18n.get("overlay.stationarenear.docking_station", displayName);
        if (!subtitle.isBlank()) {
            int subX = x + (width - minecraft.font.width(subtitle)) / 2;
            graphics.drawString(minecraft.font, subtitle, subX, y + 18, subtitleColor, false);
        }

        int scanBarX = x + 10;
        int scanBarY = y + height - 5;
        int scanBarW = width - 20;
        graphics.fill(scanBarX, scanBarY, scanBarX + scanBarW, scanBarY + 2, (SCAN_BG & 0x00FFFFFF) | alphaBits);

        int scanOffset = (int) ((now / 4L) % scanBarW);
        int beamWidth = 24;
        int beamStart = Math.max(scanBarX, scanBarX + scanOffset - beamWidth);
        int beamEnd = Math.min(scanBarX + scanBarW, scanBarX + scanOffset);
        if (beamEnd > beamStart) {
            graphics.fill(beamStart, scanBarY, beamEnd, scanBarY + 2, (SCAN_ACCENT & 0x00FFFFFF) | alphaBits);
        }
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
