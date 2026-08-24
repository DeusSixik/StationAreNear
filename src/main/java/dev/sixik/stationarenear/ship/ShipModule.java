package dev.sixik.stationarenear.ship;

import dev.sixik.stationarenear.ship.command.ShipCommands;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ShipModule {

    private ShipModule() {
    }

    public static void register(IEventBus modEventBus) {
        ShipCommands.register();
    }
}
