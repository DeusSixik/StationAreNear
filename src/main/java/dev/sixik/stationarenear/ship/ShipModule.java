package dev.sixik.stationarenear.ship;

import dev.sixik.stationarenear.ship.command.ShipCommands;
import dev.sixik.stationarenear.ship.runtime.ShipManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ShipModule {

    private ShipModule() {
    }

    public static void register(IEventBus modEventBus) {
        ShipCommands.register();
        MinecraftForge.EVENT_BUS.addListener(ShipManager::onAsteroidCollision);
        MinecraftForge.EVENT_BUS.addListener(ShipManager::onHullBlockDamage);
    }
}
