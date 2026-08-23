package dev.sixik.stationarenear.structures.client;

import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.network.packet.CycleStationEditorModePacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.InputEvent;

public final class StationStructureEditorClientEvents {

    private StationStructureEditorClientEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(StationStructureWorldRenderer::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(StationStructureEditorClientEvents::onMouseScroll);
    }

    private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !StationStructureEditorStick.isEditorTool(minecraft.player.getMainHandItem())) {
            return;
        }
        if (minecraft.screen != null || !minecraft.player.isShiftKeyDown()) {
            return;
        }
        event.setCanceled(true);
        int direction = event.getScrollDelta() > 0.0D ? 1 : -1;
        StationStructureNetwork.sendModeCycle(new CycleStationEditorModePacket(direction));
    }
}
