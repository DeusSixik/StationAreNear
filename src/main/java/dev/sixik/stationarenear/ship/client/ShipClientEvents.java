package dev.sixik.stationarenear.ship.client;

import dev.sixik.stationarenear.ship.registry.ShipBlocks;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ShipClientEvents {

    private ShipClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ShipClientEvents::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BlockEntityRenderers.register(
                    ShipBlocks.PRESSURE_TIGHT_DOOR_ENTITY.get(),
                    PressureTightDoorRenderer::new
            );
            BlockEntityRenderers.register(
                    ShipBlocks.SHIP_TELEVISION_ENTITY.get(),
                    ShipTelevisionRenderer::new
            );
        });
    }
}
