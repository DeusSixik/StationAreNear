package dev.sixik.stationarenear.terminal.client;

import dev.sixik.stationarenear.terminal.registry.TerminalBlocks;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class TerminalClientEvents {

    private TerminalClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(TerminalClientEvents::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> BlockEntityRenderers.register(
                TerminalBlocks.TERMINAL_ENTITY.get(),
                TerminalRenderer::new
        ));
    }
}
